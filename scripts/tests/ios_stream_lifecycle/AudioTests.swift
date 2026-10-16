import AVFoundation
import Flutter
import Foundation

// Pause a real buffer read after send() has accepted the frame. This controls
// the conversion/cancellation race without timing-dependent sleeps.
private final class GatedPCMBuffer: AVAudioPCMBuffer {
  let readStarted = DispatchSemaphore(value: 0)
  let allowRead = DispatchSemaphore(value: 0)
  private var didPause = false

  override var frameLength: AVAudioFrameCount {
    get {
      if !didPause {
        didPause = true
        readStarted.signal()
        precondition(allowRead.wait(timeout: .now() + 5) == .success)
      }
      return super.frameLength
    }
    set { super.frameLength = newValue }
  }
}

// Models the SDK's cross-thread callback. Buffer ownership is handed to one
// worker and coordinated with the main thread through GatedPCMBuffer's semaphores.
private struct SDKCallback: @unchecked Sendable {
  let handler: AudioFrameStreamHandler
  let buffer: AVAudioPCMBuffer
}

private final class WeakHandlerReference {
  weak var value: AudioFrameStreamHandler?
  init(_ value: AudioFrameStreamHandler?) { self.value = value }
}

@main
struct AudioTests {
  @MainActor private static var failures = 0
  @MainActor private static var checks = 0

  @MainActor
  private static func expect(_ condition: Bool, _ message: String) {
    checks += 1
    if !condition {
      failures += 1
      print("FAIL: \(message)")
    }
  }

  private static func bytes(_ samples: [Int16]) -> Data {
    samples.withUnsafeBufferPointer { Data(buffer: $0) }
  }

  private static func floatBuffer(_ samples: [Float]) -> AVAudioPCMBuffer {
    let format = AVAudioFormat(
      commonFormat: .pcmFormatFloat32, sampleRate: 16000, channels: 1, interleaved: false
    )!
    let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(max(samples.count, 1)))!
    buffer.frameLength = AVAudioFrameCount(samples.count)
    for (index, value) in samples.enumerated() { buffer.floatChannelData![0][index] = value }
    return buffer
  }

  @MainActor
  private static func drainDeliveries() async {
    await withCheckedContinuation { continuation in
      DispatchQueue.main.async { continuation.resume() }
    }
  }

  @MainActor
  static func main() async {
    // Preserve the existing sample conversion contract, including clipping.
    expect(AudioFrameStreamHandler.pcm16(floatBuffer([])) == nil, "Empty buffers are ignored")
    expect(
      AudioFrameStreamHandler.pcm16(floatBuffer([-2, -1, -0.5, 0, 0.5, 1, 2]))
        == bytes([-32767, -32767, -16383, 0, 16383, 32767, 32767]),
      "Float32 conversion and clipping are unchanged"
    )
    let mono16 = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: false)!
    let int16 = AVAudioPCMBuffer(pcmFormat: mono16, frameCapacity: 3)!
    int16.frameLength = 3
    for (index, sample) in [Int16.min, 0, Int16.max].enumerated() { int16.int16ChannelData![0][index] = sample }
    expect(AudioFrameStreamHandler.pcm16(int16) == bytes([.min, 0, .max]), "Int16 samples are preserved")
    let mono32 = AVAudioFormat(commonFormat: .pcmFormatInt32, sampleRate: 16000, channels: 1, interleaved: false)!
    let int32 = AVAudioPCMBuffer(pcmFormat: mono32, frameCapacity: 3)!
    int32.frameLength = 3
    for (index, sample) in [Int32.min, 0, Int32.max].enumerated() { int32.int32ChannelData![0][index] = sample }
    expect(AudioFrameStreamHandler.pcm16(int32) == bytes([.min, 0, .max]), "Int32 downconversion is unchanged")
    for interleaved in [false, true] {
      let format = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 16000, channels: 2, interleaved: interleaved)!
      let stereo = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 2)!
      stereo.frameLength = 2
      if interleaved {
        for (index, sample) in [Float(0.5), 1, -0.5, 1].enumerated() { stereo.floatChannelData![0][index] = sample }
      } else {
        stereo.floatChannelData![0][0] = 0.5
        stereo.floatChannelData![0][1] = -0.5
        stereo.floatChannelData![1][0] = 1
        stereo.floatChannelData![1][1] = 1
      }
      expect(AudioFrameStreamHandler.pcm16(stereo) == bytes([16383, -16383]), "First-channel extraction, interleaved=\(interleaved)")
    }

    // Copy synchronously before SDK ownership ends, and preserve delivery order.
    let live = AudioFrameStreamHandler()
    var delivered: [Data] = []
    var onMainThread = true
    _ = live.onListen(withArguments: nil) { event in
      onMainThread = onMainThread && Thread.isMainThread
      delivered.append((event as! FlutterStandardTypedData).data)
    }
    let reusable = floatBuffer([0.25])
    live.send(reusable)
    reusable.floatChannelData![0][0] = 0.75
    live.send(reusable)
    await drainDeliveries()
    expect(delivered == [bytes([8191]), bytes([24575])], "SDK bytes are copied before return and retain their order")
    expect(onMainThread, "Delivery stays on the platform thread")
    _ = live.onCancel(withArguments: nil)

    let cancelled = AudioFrameStreamHandler()
    var cancelledDeliveries = 0
    _ = cancelled.onListen(withArguments: nil) { _ in cancelledDeliveries += 1 }
    cancelled.send(floatBuffer([0.25]))
    _ = cancelled.onCancel(withArguments: nil)
    await drainDeliveries()
    expect(cancelledDeliveries == 0, "A queued event cannot invoke a cancelled sink")

    // Both retained Flutter sinks publish on the same named channel. Only the
    // new frame may reach a consumer installed after cancel/relisten.
    let relisten = AudioFrameStreamHandler()
    var sharedChannel: [Data] = []
    let sink: FlutterEventSink = { sharedChannel.append(($0 as! FlutterStandardTypedData).data) }
    _ = relisten.onListen(withArguments: nil, eventSink: sink)
    relisten.send(floatBuffer([0.25]))
    _ = relisten.onCancel(withArguments: nil)
    _ = relisten.onListen(withArguments: nil, eventSink: sink)
    relisten.send(floatBuffer([0.75]))
    await drainDeliveries()
    expect(sharedChannel == [bytes([24575])], "Only fresh audio reaches the replacement subscriber")

    // A listener replacement must invalidate work even without explicit cancel.
    relisten.send(floatBuffer([0.25]))
    sharedChannel.removeAll()
    _ = relisten.onListen(withArguments: nil, eventSink: sink)
    relisten.send(floatBuffer([0.75]))
    await drainDeliveries()
    expect(sharedChannel == [bytes([24575])], "Listen itself advances the subscription generation")

    let conversion = AudioFrameStreamHandler()
    let format = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 16000, channels: 1, interleaved: false)!
    let gated = GatedPCMBuffer(pcmFormat: format, frameCapacity: 1)!
    gated.frameLength = 1
    gated.floatChannelData![0][0] = 0.25
    var conversionDeliveries = 0
    _ = conversion.onListen(withArguments: nil) { _ in conversionDeliveries += 1 }
    let callback = SDKCallback(handler: conversion, buffer: gated)
    await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
      DispatchQueue.global().async {
        callback.handler.send(callback.buffer)
        // send() has enqueued its delivery before this main-queue marker.
        DispatchQueue.main.async { continuation.resume() }
      }
      precondition(gated.readStarted.wait(timeout: .now() + 5) == .success)
      _ = conversion.onCancel(withArguments: nil)
      _ = conversion.onListen(withArguments: nil) { _ in conversionDeliveries += 1 }
      gated.allowRead.signal()
    }
    expect(conversionDeliveries == 0, "Cancellation during an SDK buffer read invalidates its pending audio")
    conversion.send(floatBuffer([0.75]))
    await drainDeliveries()
    expect(conversionDeliveries == 1, "Fresh audio still arrives after the conversion/cancel race")

    var released: AudioFrameStreamHandler? = AudioFrameStreamHandler()
    let weakHandler = WeakHandlerReference(released)
    var releasedDeliveries = 0
    _ = released!.onListen(withArguments: nil) { _ in releasedDeliveries += 1 }
    released!.send(floatBuffer([0.25]))
    released = nil
    expect(weakHandler.value == nil, "Queued delivery must not retain the handler")
    await drainDeliveries()
    expect(releasedDeliveries == 0, "An expired handler cannot invoke a retained sink")

    print("Audio handler: \(checks) checks, \(failures) failures")
    if failures != 0 { exit(1) }
  }
}
