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
    @State private var showTimer = false
    @State private var nextTimer = false
    @State private var showBooking = false
    @State private var nextBooking = false
    @State private var showChecklist = false
    @State private var nextChecklist = false
    @State private var appChecklist = false
    @State private var showCalculator = false
    @State private var showTable = false
    @State private var showChart = false
    @State private var showCode = false
    @State private var showTerminal = false
    @State private var copyStatus = "Copy not inspected"
    @State private var ordinaryTable = false
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
            Button("Open generated timer") { showTimer = true }
            Button("Open generated booking") { showBooking = true }
            Button("Open generated checklist") { showChecklist = true }
            Button("Open generated calculator") { showCalculator = true }
            Button("Open generated table") { showTable = true }
            Button("Open generated chart") { showChart = true }
            Button("Open generated code") { showCode = true }
            Button("Open generated terminal") { showTerminal = true }
        }
        .sheet(isPresented: $showTimer) {
            VStack {
                Button("Use next timer payload") { nextTimer = true }
                ScrollView {
                    WidgetBlockView(kind: "app", json: nextTimer
                        ? #"{"type":"timer","title":"Actual seconds","durationSeconds":5}"#
                        : #"{"type":"timer","title":"Configure duration","items":[{"title":"Not time","price":25}]}"#, open: false).padding()
                }
            }
        }
        .sheet(isPresented: $showBooking) {
            VStack {
                Button("Use next booking payload") { nextBooking = true }
                ScrollView {
                    WidgetBlockView(kind: "app", json: nextBooking
                        ? #"{"type":"booking","title":"New slots","slots":["11:00"]}"#
                        : #"{"type":"booking","title":"Actual slots","slots":["09:00","10:00"]}"#, open: false).padding()
                }
            }
        }
        .sheet(isPresented: $showChecklist) {
            VStack {
                Button("Use app task surface") { appChecklist = true }
                Button("Use next checklist payload") { nextChecklist = true }
                ScrollView {
                    WidgetBlockView(kind: appChecklist ? "app" : "checklist", json: appChecklist
                        ? #"{"type":"tasks","title":"App tasks","items":[{"title":"App first","subtitle":"Supplied app detail","done":true},{"title":"App second"}]}"#
                        : nextChecklist
                        ? #"{"title":"New tasks","items":[{"label":"New supplied task"}]}"#
                        : #"{"title":"Actual tasks","items":[{"label":"Original source","detail":"Only supplied detail"},{"label":"Keep source"}]}"#, open: false).padding()
                }
            }
        }
        .sheet(isPresented: $showCalculator) {
            ScrollView {
                WidgetBlockView(kind: "calc", json: #"{"title":"\ud83d\ude80 Growth","fields":[{"key":"amount","label":"Amount \u00a9\ufe0f \ud83d\ude00","value":100}],"outputs":[{"label":"Double","expr":"amount*2"}]}"#, open: false).padding()
            }
        }
        .sheet(isPresented: $showTerminal) {
            ScrollView {
                WidgetBlockView(kind: "terminal", json: #"{"welcome":"Provided \ud83d\ude00 $& $$ __GS_WORKER_CONTRACT_LITERAL__","commands":[{"name":"deploy","description":"Help only","output":"deployed"}]}"#, open: false).padding()
            }
        }
        .sheet(isPresented: $showTable) {
            VStack {
                Button("Use ordinary Markdown") { ordinaryTable = true }
                ScrollView {
                    if ordinaryTable {
                        BlocksView(blocks: parseBlocks(Self.tableMarkdown)).padding()
                    } else {
                        WidgetBlockView(kind: "table", json: Self.tableJSON, open: false).padding()
                    }
                }
            }
        }
        .sheet(isPresented: $showChart) {
            ScrollView { WidgetBlockView(kind: "chart", json: Self.chartJSON, open: false).padding() }
        }
        .sheet(isPresented: $showCode) {
            VStack {
                Button("Inspect copied source") {
                    copyStatus = UIPasteboard.general.string == Self.codeSource ? "Original source copied" : "Wrong source copied"
                }
                Text(copyStatus)
                ScrollView { BlocksView(blocks: parseBlocks("```javascript\n" + Self.codeSource + "\n```")).padding() }
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

    private static let codeSource = #"const data = {"😀":7,"🌅":9}; console.log(data["😀"]+data["🌅"], "😀".length);"#

    private static var chartJSON: String {
        let data: [String: Any] = ["type": "line", "labels": (1...31).map { "P\($0)" },
            "series": [
                ["name": "Primary", "data": (0..<31).map { [$0, $0 - 15] }],
                ["name": "Secondary", "data": [[0, 2], [1, 4], [2, 6]]]
            ]]
        guard let encoded = try? JSONSerialization.data(withJSONObject: data),
              let json = String(data: encoded, encoding: .utf8) else { return "{}" }
        return json
    }

    private static var tableMarkdown: String {
        "`😀` [😀](https://example.org)\n\n| Name | Amount |\n| --- | --- |\n" +
            (1...31).map { "| Item \($0) | \($0) |" }.joined(separator: "\n")
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
