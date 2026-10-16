/// Blocks stream admission until every nested or queued teardown scope exits.
/// All scope changes and admission checks run on the main actor.
@MainActor
final class StreamTeardownGate {
  private var depth = 0

  // The plugin can be constructed off-actor; the gate starts empty and is only
  // accessed later from its main-actor lifecycle methods.
  nonisolated init() {}

  var isActive: Bool { depth > 0 }

  func enter() {
    depth += 1
  }

  func leave() {
    precondition(depth > 0, "Unbalanced stream teardown scope")
    depth -= 1
  }
}
