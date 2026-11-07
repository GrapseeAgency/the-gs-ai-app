# Platform runtime verification

The previous stopping point was compilation/structural coverage with a written
runtime limitation. This work adds executable observation, not a broader claim
that every platform feature has been accepted on hardware.

## Checks added

| Surface | Android API 34 instrumented test | iOS simulator XCTest/XCUITest |
| --- | --- | --- |
| Foreground notification suppression | Production ReplyNotifier, no active OS notification | Production GsNotifier must refuse while UIApplication is active |
| Background continuation | Actual Home key drives MainActivity ON_STOP; delayed callback runs | Actual Home key drives UIApplication background; finite UIKit background task |
| Delivered notification | NotificationManager.activeNotifications: real title, body, channel and PendingIntent | UNUserNotificationCenter.deliveredNotifications: exactly one matching title/body |
| Disabled master / messages | Production switch gates; no active OS notification | Production switch gates; refused result AND no delivered matching notice |
| Long press | Inject tap/hold into production GsCard; distinct callback counts | Open real chats context menu; Pin action must survive Home and process relaunch, then Unpin |
| Haptics | Recording View forwards to platform; constants and disabled gate checked | Test-only Objective-C interceptors forward to actual UIKit generators; vocabulary and disabled gate checked |

Android runs in `.github/workflows/platform-runtime.yml`, on pull requests and
main. `scripts/check_platform_runtime_results.py` requires all three named test
cases, and rejects missing, duplicate, skipped, error or failure results. Its
`--self-test` checks six positive/adversarial report shapes.

iOS runs in `.github/workflows/ios.yml`, now also on iOS pull requests. The
workflow requires all three named runtime tests to have **passed**, not merely
compiled or been collected. Notification and persisted-menu screenshots are
XCTest attachments. Existing xcresult failure artifacts and diagnostics remain.

The iOS notification driver is **DEBUG only**, activated solely by
`-gs_test_platform_runtime`. It uses the production notifier with real OS
permission and delivery APIs, restores preferences, and removes only its own
notice records. It does not simulate an OS delivery acknowledgment.

## Evidence status

- Prior Android workflow 42393087049 and iOS workflow 42393404031 both finished
  **successfully**. These predate the new runtime tests and do not certify them.
- iOS runtime run **42396930975** on source **4ab6b88** finished **successfully**.
  Both the XCTest step and the named-platform-runtime-pass gate succeeded:
  background OS delivery and disabled gates, long-press pin persistence across
  relaunch, and UIKit haptic dispatch/gating all executed. This is simulator
  evidence, not a physical-device sensation or scheduled-wake claim.
- The first Android runtime run **42396931145** failed a notification-state wait;
  gesture and haptic tests passed. The test's direct background PendingIntent
  launch was replaced with a real notification-shade tap (Android 14 restricts
  background activity launches). Named-stage and JUnit-stack diagnostics now
  distinguish the exact failing observation. Its successor must pass before
  Android notification behavior is claimed. See PR #26 for live results.
- Local Swift structure/static checks and the six report-gate self-tests pass.
  No Android SDK or Xcode runtime is available in the editing sandbox.

## Boundaries that remain real

1. **Continuation is not wake.** No native WorkManager/BGTaskScheduler scheduled
   wake implementation was found in the audited client paths. These tests do
   not wake a suspended/terminated app. Do not invent a scheduled task merely
   to obtain a green test, or use a manual scheduler trigger as proof of natural
   OS scheduling. Future wake claims require a real shipped producer and an
   OS-observed execution timestamp after suspension.
2. **Dispatch is not sensation.** Emulators/simulators can observe the actual
   platform haptic calls and preference gates, not actuator strength, latency,
   or whether a person feels them. Physical-device checks in
   `device-validation/CHECKLIST.md` still apply.
3. These tests verify **local notifications**, not backend/APNs/FCM push, and do
   not certify lock-screen visibility, Focus/DND interruption, notification
   sound, or all OEM power-management policies.
4. A directly driven production notifier is not an end-to-end model-stream
   completion test. The tests do not certify every upstream producer.
5. No new installable APK, signed IPA, or backend deployment is delivered by
   adding these checks. Full GenUI visual/interaction acceptance is separate.
