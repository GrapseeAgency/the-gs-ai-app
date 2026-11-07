#if DEBUG
import SwiftUI
import UIKit
import UserNotifications

/// Debug-only XCUITest driver around the PRODUCTION notifier. It does not
/// replace the OS or the dispatcher. No probe is compiled into Release.
/// Pressing Home tests short background continuation, NOT a scheduled wake.
@MainActor
struct PlatformRuntimeProbe: View {
    @State private var status = "idle"
    @State private var showCalculator = false
    @State private var showTable = false
    @State private var title = ""
    @State private var armed = false
    @State private var accepted: Bool?
    @State private var backgroundObserved = false
    @State private var saved: [Bool] = []

    var body: some View {
        VStack(spacing: 24) {
            Text(status).accessibilityIdentifier("platform-runtime-status")
            Button("Arm allowed notice") { Task { await arm(mode: "allowed") } }
            Button("Arm master-disabled notice") { Task { await arm(mode: "master") } }
            Button("Arm messages-disabled notice") { Task { await arm(mode: "messages") } }
            Button("Inspect OS delivery") { Task { await inspect() } }
            Button("Restore runtime settings") { Task { await restore() } }
            Button("Open generated calculator") { showCalculator = true }
            Button("Open generated table") { showTable = true }
            if showCalculator {
                WidgetBlockView(kind: "calc", json: #"{"title":"\ud83d\ude80 Growth","fields":[{"key":"amount","label":"Amount \u00a9\ufe0f \ud83d\ude00","value":100}],"outputs":[{"label":"Double","expr":"amount*2"}]}"#, open: false)
            }
        }
        .sheet(isPresented: $showTable) {
            ScrollView {
                WidgetBlockView(kind: "table", json: Self.tableJSON, open: false)
                    .padding()
            }
        }
        .onAppear {
            let settings = SettingsStore.shared
            saved = [settings.pushNotifications, settings.noticeMessages,
                     settings.quietHoursEnabled, settings.noticeDnd]
            GsBannerDispatcher.shared.configure()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { _ in
            guard armed else { return }
            armed = false
            backgroundObserved = UIApplication.shared.applicationState == .background
            // Finite continuation window, not a new background service or a
            // claim that the OS will wake a suspended/terminated application.
            let taskID = UIApplication.shared.beginBackgroundTask(withName: "PlatformRuntimeProbe")
            Task { @MainActor in
                defer {
                    if taskID != .invalid { UIApplication.shared.endBackgroundTask(taskID) }
                }
                do { try await Task.sleep(nanoseconds: 1_000_000_000) }
                catch { status = "callback-cancelled"; return }
                accepted = await GsNotifier.post(kind: .messages, title: title,
                    body: "Actual background callback from the runtime test")
                status = "callback-complete"
            }
        }
    }

    private static var tableJSON: String {
        let data: [String: Any] = ["columns": ["Name", "Amount"],
            "rows": (1...31).map { ["Item \($0)", String($0)] }]
        guard let encoded = try? JSONSerialization.data(withJSONObject: data),
              let json = String(data: encoded, encoding: .utf8) else { return "{}" }
        return json
    }

    private func arm(mode: String) async {
        await clearNotices()
        accepted = nil
        backgroundObserved = false
        title = "GS runtime \(UUID().uuidString)"
        let settings = SettingsStore.shared
        settings.pushNotifications = true
        settings.noticeMessages = true
        settings.quietHoursEnabled = false
        settings.noticeDnd = false
        guard await GsBannerDispatcher.requestAuthorization() else {
            status = "permission-denied"
            return
        }
        // Permission dismissal briefly leaves UIApplication inactive. Wait for
        // the real lifecycle, not an assumed alert-animation duration.
        for _ in 0..<30 {
            if UIApplication.shared.applicationState == .active { break }
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
        // The real active-state gate must refuse before Home is pressed.
        guard UIApplication.shared.applicationState == .active else {
            status = "prepare-not-active"
            return
        }
        let foregroundPosted = await GsNotifier.post(kind: .messages, title: title,
            body: "This foreground notice must be refused")
        guard !foregroundPosted else { status = "foreground-gate-failed"; return }
        settings.pushNotifications = mode != "master"
        settings.noticeMessages = mode != "messages"
        armed = true
        status = "armed-\(mode)"
    }

    private func inspect() async {
        guard !armed, let accepted = accepted else { status = "callback-pending"; return }
        guard backgroundObserved else { status = "background-not-observed"; return }
        let delivered = await UNUserNotificationCenter.current().deliveredNotifications()
        let matching = delivered.filter { $0.request.content.title == title }
        if accepted {
            let valid = matching.count == 1 && matching.first?.request.content.body ==
                "Actual background callback from the runtime test"
            status = valid ? "os-delivered" : "accepted-but-not-delivered"
        } else {
            status = matching.isEmpty ? "os-blocked" : "blocked-but-delivered"
        }
    }

    private func clearNotices() async {
        let center = UNUserNotificationCenter.current()
        let ours = await center.deliveredNotifications().filter { $0.request.content.title.hasPrefix("GS runtime ") }
        center.removeDeliveredNotifications(withIdentifiers: ours.map { $0.request.identifier })
        for entry in GsNoticeInbox.shared.entries where entry.title.hasPrefix("GS runtime ") {
            GsNoticeInbox.shared.delete(id: entry.id)
        }
    }

    private func restore() async {
        armed = false
        await clearNotices()
        if saved.count == 4 {
            let settings = SettingsStore.shared
            settings.pushNotifications = saved[0]
            settings.noticeMessages = saved[1]
            settings.quietHoursEnabled = saved[2]
            settings.noticeDnd = saved[3]
        }
        status = "restored"
    }
}
#endif
