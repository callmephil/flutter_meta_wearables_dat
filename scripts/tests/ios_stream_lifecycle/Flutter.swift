// Host-only channel types. Production AudioFrameStreamHandler.swift is compiled
// unchanged against this module; no DAT SDK or Flutter engine is loaded here.
import Foundation

public typealias FlutterEventSink = (Any?) -> Void

public class FlutterError: NSObject {}

public protocol FlutterStreamHandler {
  func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError?
  func onCancel(withArguments arguments: Any?) -> FlutterError?
}

public final class FlutterStandardTypedData: NSObject {
  public let data: Data

  public init(bytes: Data) {
    data = bytes
  }
}
