import Foundation
import Network

/**
 * Single shared connectivity signal (UI rebuild Step 4): NWPathMonitor wrapped
 * in the app's established ObservableObject pattern (ConversationStore/
 * SettingsStore lineage). One monitor per process; surfaces read `isOffline`
 * through @ObservedObject on `shared`.
 *
 * Honest semantics: `isOffline` mirrors the SYSTEM path status only — it says
 * nothing about backend health. A reachable Wi-Fi with a dead API still reads
 * online; that failure surfaces through ChatViewModel's own error / GS Lite
 * paths instead, so this signal never claims more than it knows.
 *
 * The initial value is `false` (assumed online) until the first path update
 * arrives: NWPathMonitor reports promptly after start, and the optimistic
 * default avoids a false "offline" flash while the monitor warms up.
 */
@MainActor
final class NetworkMonitor: ObservableObject {

    static let shared = NetworkMonitor()

    /// True only while the system reports no usable network path.
    @Published private(set) var isOffline = false

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "gs.network.monitor")

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let offline = path.status != .satisfied
            // Handler arrives on the monitor queue — hop to main for the
            // published write (object is @MainActor, like the stores).
            Task { @MainActor in
                self?.isOffline = offline
            }
        }
        monitor.start(queue: queue)
    }
}
