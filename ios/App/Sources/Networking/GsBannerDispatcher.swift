import Foundation
import UIKit
import UserNotifications

/// The iOS delivery path every notice goes through — the twin of Android's
/// `GsNotifier.post` and the dispatcher the Alerts sheet used to admit it did
/// not have.
///
/// The gates are the same ones Android applies, in the same order, and every
/// refusal is a `false` rather than an exception:
///  - the master switch and the per-kind switch (in `GsNotifier.post`),
///  - Quiet Hours and Nexly's own Do-Not-Disturb preference,
///  - the app must NOT be in front — a banner for the screen you are looking
///    at is noise, and the Inbox is the in-app channel,
///  - the OS must have authorized alerts. Permission is requested only from an
///    explicit reader action (the Push Notifications switch), never at launch.
///
/// Nothing is fabricated: the caller passes the real strings and the Inbox row
/// is written only after the banner was actually accepted by the system.
@MainActor
final class GsBannerDispatcher: NSObject, UNUserNotificationCenterDelegate {

    static let shared = GsBannerDispatcher()

    private var configured = false

    /// Installs the foreground delegate once. Called at launch; idempotent.
    func configure() {
        guard !configured else { return }
        configured = true
        UNUserNotificationCenter.current().delegate = self
    }

    /// Asks the OS for alert permission. Only the Push Notifications switch
    /// calls this — a permission prompt nobody asked for is the thing this
    /// build refuses to do.
    static func requestAuthorization() async -> Bool {
        do {
            return try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            return false
        }
    }

    static func authorizationStatus() async -> UNAuthorizationStatus {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
    }

    /// Posts one banner and reports whether the system accepted it. The caller
    /// records the Inbox row only when this returns true.
    func post(title: String, body: String) async -> Bool {
        guard UIApplication.shared.applicationState != .active else { return false }
        let status = await Self.authorizationStatus()
        guard status == .authorized || status == .provisional else { return false }

        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil)
        do {
            try await UNUserNotificationCenter.current().add(request)
            return true
        } catch {
            return false
        }
    }

    // MARK: UNUserNotificationCenterDelegate

    /// A notice that lands while the app is in front is shown as a banner, not
    /// swallowed — the delegate's default is silence.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .list])
    }
}

/// The iOS producer behind the "Product Updates" switch: one notice per build
/// version, recorded the first time that version runs.
///
/// Android's `LiveUpdater` announces a release from a manifest; iOS has no such
/// feed, and inventing one would be the fabricated-content failure the notifier
/// exists to prevent. What this build can back is the event that actually
/// happened: this version is now installed. The token is spent only once a row
/// was really written, so a refused pass (master switch off) cannot silence the
/// release for the life of the install.
@MainActor
enum GsReleaseNotice {

    static let versionKey = "gs.notice.announcedVersion"

    static func announceIfNeeded(version: String? = nil) {
        let defaults = UserDefaults.standard
        let current = version
            ?? (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String)
            ?? "0"
        guard defaults.string(forKey: versionKey) != current else { return }
        let title = "Updated to GS \(current)"
        let body = "This build includes the latest fixes and improvements. Open your chats to see what changed."
        Task {
            let posted = await GsNotifier.post(
                kind: .productUpdates, title: title, body: body)
            let recorded = posted || GsNotifier.record(
                kind: .productUpdates, title: title, body: body)
            if recorded {
                defaults.set(current, forKey: versionKey)
            }
        }
    }
}
