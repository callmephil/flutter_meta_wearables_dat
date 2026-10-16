// Exercises the production gate with controlled SDK suspension points. This
// verifies admission policy; check_wiring.py verifies its plugin call sites.
actor SuspensionPoint {
  private var continuation: CheckedContinuation<Void, Never>?
  private var observers: [CheckedContinuation<Void, Never>] = []

  func suspend() async {
    await withCheckedContinuation { continuation in
      precondition(self.continuation == nil)
      self.continuation = continuation
      observers.forEach { $0.resume() }
      observers.removeAll()
    }
  }

  func waitUntilSuspended() async {
    if continuation != nil { return }
    await withCheckedContinuation { observers.append($0) }
  }

  func resume() {
    precondition(continuation != nil)
    let pending = continuation
    continuation = nil
    pending?.resume()
  }
}

@MainActor
private func streamCleanup(_ gate: StreamTeardownGate, at pause: SuspensionPoint) async {
  gate.enter()
  defer { gate.leave() }
  await pause.suspend()
}

@MainActor
private func deviceCleanup(
  _ gate: StreamTeardownGate,
  audio: SuspensionPoint,
  device: SuspensionPoint
) async {
  gate.enter()
  defer { gate.leave() }
  await streamCleanup(gate, at: audio)
  await device.suspend()
}

@main
struct GateTests {
  @MainActor
  static func main() async {
    let gate = StreamTeardownGate()
    precondition(!gate.isActive, "Idle admission must remain open")

    let audio = SuspensionPoint()
    let device = SuspensionPoint()
    let stop = Task { await deviceCleanup(gate, audio: audio, device: device) }
    await audio.waitUntilSuspended()
    precondition(gate.isActive, "Audio cancellation must block start admission")
    await audio.resume()
    await device.waitUntilSuspended()
    precondition(gate.isActive, "Device stopping must remain gated after stream cleanup")
    await device.resume()
    await stop.value
    precondition(!gate.isActive, "Completed device cleanup must reopen admission")

    let firstPause = SuspensionPoint()
    let secondPause = SuspensionPoint()
    let first = Task { await streamCleanup(gate, at: firstPause) }
    await firstPause.waitUntilSuspended()
    let queuedEntered = SuspensionPoint()
    let queued = Task { @MainActor in
      // Enter before awaiting earlier cleanup, as the plugin does.
      gate.enter()
      defer { gate.leave() }
      await queuedEntered.suspend()
      await first.value
      await secondPause.suspend()
    }
    await queuedEntered.waitUntilSuspended()
    precondition(gate.isActive)
    await queuedEntered.resume()
    await firstPause.resume()
    await secondPause.waitUntilSuspended()
    precondition(gate.isActive, "An earlier completion must not release queued cleanup")
    await secondPause.resume()
    await queued.value
    precondition(!gate.isActive)

    let cancellationPause = SuspensionPoint()
    let cancelled = Task { await streamCleanup(gate, at: cancellationPause) }
    await cancellationPause.waitUntilSuspended()
    cancelled.cancel()
    precondition(gate.isActive, "Cancellation alone must not reopen a still-active cleanup")
    await cancellationPause.resume()
    await cancelled.value
    precondition(!gate.isActive, "Cancelled cleanup must release its scope on exit")

    enum CleanupExit: Error { case timeout }
    func earlyExit() {
      gate.enter()
      defer { gate.leave() }
      return
    }
    func timeoutExit() throws {
      gate.enter()
      defer { gate.leave() }
      throw CleanupExit.timeout
    }
    earlyExit()
    precondition(!gate.isActive, "Early return must release its scope")
    do {
      try timeoutExit()
      preconditionFailure("Expected timeout exit")
    } catch CleanupExit.timeout {
      precondition(!gate.isActive, "Timeout exit must release its scope")
    } catch {
      preconditionFailure("Unexpected cleanup error: \(error)")
    }
    print("Teardown gate: idle, audio wait, device wait, queued scopes, cancellation and exits passed")
  }
}
