# Pending references — supplied by the owner, built on both platforms

Nine screens were supplied as reference art. All nine are built on **both**
platforms: the six owner sheets crossed on 2026-10-29, Privacy & Data Controls
followed, and Chat History & Search and Model Selection crossed on 2026-10-30.
Nothing here is called "captured" until its render gate proves parity — see
Acceptance.

| reference | expected screen | state |
| --- | --- | --- |
| `privacy-data-controls.png` | "Privacy & Data Controls" — hero, Data & Permissions, Conversation & Activity, Export & Deletion groups | **built (both)** — see deltas below |
| `chat-history-search.png` | "Chat History & Search" — search field + filter chips, totals card, Recent Chats, Quick Actions 2×2, new-conversation footer | **built (both)** — iOS twin rebuilt 2026-10-30 |
| `model-selection.png` | "Model Selection & Reasoning Effort" — AI Model radio list, Reasoning Effort 4-up, Model Preferences toggles | **built (both)** — four-across tiles + real switches |
| `notifications-alerts.png` | "Notifications & Alerts" — master switch, 5 types, 3 channels, quiet hours, closing card | **built (both)** |
| `notifications-inbox.png` | "Notifications" — 5 chips with counts, Today/Earlier, unread dots, caught-up card | **built (both)** |
| `account-security.png` | "Account & Security" — profile, sign-in methods, authentication, sessions, devices | **built (both)** |
| `subscription-usage.png` | "Subscription & Usage" — plan hero, details, 4 usage meters, billing, upgrade | **built (both)** |
| `help-support.png` | "Help & Support" — search, quick help, 3×2 categories, popular topics, contact | **built (both)** |
| `connected-apps.png` | "Connected Apps & Integrations" — hero, 6 providers, 5 available, data card | **built (both)** |

## Evidence

Before this pass a literal search for each screen's own copy returned zero files
across `android/app/src/main` and `ios/App/Sources` — that is what "absent" meant.
After it, every screen returns at least one file on both platforms:

```
Chat History & Search    ChatSearchScreen.kt            ChatSearchView.swift
Model Selection          ReferenceSettingsScreens.kt   AiIntelligenceView.swift
Privacy & Data Controls  PrivacySettingsScreen         PrivacyDataControlsView.swift
```

The six owner sheets below return a screen on both platforms.

## Near misses — do not mistake these for the reference

- `AiIntelligenceScreen` used to be a *different* screen from
  `model-selection.png` — different title, no Reasoning Effort 4-up, no Model
  Preferences toggles — and the settings hub row that now reaches it was added
  for that reason. The Android composable was rebuilt to the mock in the earlier
  Android pass (title, four model rows, four-across effort tiles, three
  preferences); on 2026-10-30 the iOS view was rebuilt to the same inventory,
  so the name and the reference now describe one page on both platforms.
- `PrivacySettingsScreen` is not "Privacy & Data Controls": the reference's four
  groups (Data & Permissions, Conversation & Activity, Export & Deletion, plus
  its two hero cards) are not its structure.
- The drawer's recent-chats list is not "Chat History & Search": the reference
  is a full page with search, chips, totals and a Quick Actions grid.

## Accepted deltas from the mocks (all three screens)

Every one of these is a place where the mock draws something the build cannot
back. They are corrections, not omissions:

- **Privacy — App Permissions** names the two grants the manifest actually has
  (microphone, notifications). The mock says "camera, microphone, location";
  there is no camera or location grant in this build.
- **Privacy — footer** says "private to this app, never sold". The mock says
  "encrypted"; Room here is not encrypted, and saying so would be false.
- **Privacy — Personalization & Ads, Third-Party Integrations, Search History,
  Data Retention** are static rows with no chevron. There is no ad profile, no
  integration, no stored search log and no retention setting behind them.
- **Privacy — Delete Your Account** keeps the mock's title but its subtitle says
  what it erases (everything this app stores on this device), and confirming it
  also signs the session out. There is no server account to delete.
- **Chat History — the topic chips are KEYWORD-derived** from the conversation
  title through the shipped `drawerTopic()` classifier (the same one the drawer
  uses). The schema has no facet column, so the buckets are approximate by
  construction; `Chats` is the classifier's general-chat bucket, which is what
  makes it different from `All`.
- **Chat History — "Saved items"** replaces the mock's "Saved Conversations":
  the app has saved *items*, and that is where the row goes.
- **Chat History — the totals are ACTIVE conversations.** An archived chat is
  counted on the archived facet, not in the mock's headline total.
- **Model Selection — Auto-select model** is the mock's OFF switch, disabled,
  because the backend always chooses the model.
- **Model Selection — the model radios** persist a preference, but the backend
  ignores a client-chosen model (`src/app/api/v1/conversations/[id]/route.ts:20`).
- **Model Selection — the page continues below the mock's closing card.** Model
  source, View model details, Rendering and the on-device footnote are kept so
  `SETTINGS_MODEL` is not orphaned. The mock's own three groups (AI Model,
  Reasoning Effort, Model Preferences) fit one 393×852 view; the extras scroll.

## The six owner sheets (2026-10-29)

Source of truth: the six PNGs at the repository root (`Reference pictures./`),
copied byte-for-byte into this folder under stable kebab-case names so the
render gates have a baseline. Geometry was measured off their ink at
1.644 px/pt (a 646px phone panel = 393pt): page title 16, subtitle 10.5, group
title 13, caption 9, row title 10, detail 8, one-line row pitch 45, two-line
56, switch 34×20, tag pill 7.

Both platforms compose one shared kit per side (`RefSheetKit.kt` /
`GsSheetKit.swift`) instead of the settings hub's 15pt rows, which made every
sheet half again as tall as its mock. The same screen inventory, in the mocks'
order, exists on both:

| screen | Android | iOS | entry points |
|---|---|---|---|
| Notifications & Alerts | `settings/NotificationsAlertsScreen` | `Features/Notifications/NotificationsAlertsView` | Settings → Notifications & Alerts |
| Notifications Inbox | `notifications/NotificationsInboxScreen` | `Features/Notifications/NotificationsView` | drawer / Profile → Notifications |
| Account & Security | `settings/AccountSecurityScreen` | `Features/Account/AccountSecurityView` | Settings → Account & Security |
| Subscription & Usage | `subscription/SubscriptionUsageScreen` | `Features/Billing/SubscriptionUsageView` | Settings upgrade card → View plan |
| Help & Support | `support/HelpSupportScreen` | `Features/Support/HelpSupportView` | Settings → Help & Support |
| Connected Apps | `integrations/ConnectedAppsScreen` | `Features/Integrations/ConnectedAppsView` | Settings → Connected Apps; Privacy → Integrations |

Evidence:
- Android: `ReferenceSheetsScreenshotTest` renders all six at 393×852
  (`-PgsShots=true`). The captures live in `build/screenshots/ref-*.png`.
  `ReferenceSheetsContentTest` reads the real Compose semantics tree and asserts
  every section, row and closing card — in the mock's order — plus the truthful
  states (`Email Notifications` and `Two-Factor Authentication` disabled,
  `$19.99`/`Visa`/`Active`/`Connected`/`Limited Access` absent, six
  `Not Connected` pills) and the entry points (hub → alerts/account/help/plan,
  privacy → integrations), plus the Help search actually filtering its topics
  and Manage Plan / Need help resolving to Billing and Help.
  `NotificationsInboxInteractionTest` is the always-on
  proof of the reader contract: an out-of-order file still paints newest first,
  a row tap writes the stored read flag, "Mark all as read" writes every row,
  and an injected 18:00 clock decides the Today/Earlier split.
- The Android six are now card-gated by `scripts/check_sheets_render.py` in
  `android-app.yml`: the page header's ink band, every card's lit rim (level
  248-255 L, ≥ 6 L over its interior) and the card rhythm — seven inbox cards,
  six provider cards — with `--self-test` doctoring the header, a rim and (for
  the two lists) the card gaps and proving each probe goes red. `--derive`
  reprints the measured rows for recalibration.
- `scripts/check_sheets_copy_parity.py` runs in both workflows: every section
  and row title above must appear in both the Android and iOS source, in the
  mocks' order, and the mock's unbacked values (`$19.99`, `Visa`, `Reset my
  password`) must not. Its `--self-test` proves the missing-copy, forbidden-copy
  and order probes can each fail.
- iOS: `ReferenceSheetsRenderTests` renders the same six at 393×852 through the
  windowed `drawHierarchy` channel the Settings/Explore harnesses use. Its inbox
  is seeded with the same six notices as Android's fixture and cleared by the
  test's `defer`, so the iOS render exercises the row layout without leaving
  invented rows behind. `ios.yml` collects all six renders, fails on a missing
  one, and runs the same `check_sheets_render.py --platform ios` card gate plus
  its self-test on those captures.
- iOS notice path: `GsNotifier.record` honours the master switch and the
  per-kind switch, rejects empty copy, and writes `GsNoticeInbox` only then.
  `GsNotifierTests` pins those four cases on the simulator, and the sign-in in
  `GSApp` is its first real producer — so Account Activity is no longer a
  switch nothing reads.
- iOS banner delivery now exists: `GsBannerDispatcher` (UNUserNotificationCenter)
  asks for permission only from the Push Notifications switch, refuses to post
  while the app is in front, and `GsNotifier.post` adds Quiet Hours and DND to
  the same switch gates. The Inbox row is written only after the system accepted
  the banner, so the log cannot claim a notice the device never showed. A new
  build version records one Product Updates notice (`GsReleaseNotice`, once per
  `CFBundleShortVersionString`, token spent only when a row was really written).
  Foreground/background delivery itself is not observable in CI; the gate-able
  halves — quiet-hours arithmetic (wrap and boundaries) and the once-per-version
  token — are pinned in `GsNotifierTests`.
- iOS reachability: `SheetsUITests` drives the real drawer → Settings → each
  Settings sheet → Back, the Upgrade pill for the plan, Privacy → Chat History,
  and the account sheet's Notifications row for the inbox. It runs in the
  `GsChatUITests` bundle. Run 41014412869 was its first CI observation and it
  failed: the account sheet routed while it was still presented, so the drawer
  closed and the inbox never mounted. The sheet now dismisses before the route,
  which fixes all four account rows; the fix's proof is the next green run.
- Subscription numbers: `UsageMeterTest` pins the local-calendar-month → UTC
  stamp boundary in Asia/Dhaka and UTC, and the unknown-vs-zero contract (`-1`
  renders "—"; zero would falsely claim "none used").
- Alert switches: `SettingsNoticeKindsTest` pins the per-kind gate — every mock
  kind defaults on, one off leaves the rest on, an unknown id never becomes
  live, and the master switch round-trips.
- Tips producer: finishing onboarding records the mock's "Personalization
  applied" notice (skipping the setup records nothing) on both platforms, so
  that switch has a real event behind it. `GsNotifierRecordTest` pins the
  writer's master/kind gates and the empty-copy guard.
- Privacy & Data Controls now has its iOS page: `PrivacyDataControlsView`
  renders to `nexly-privacy-ios` in `ReferenceSheetsRenderTests`, its section
  and row inventory is in the cross-platform copy gate, and `SheetsUITests`
  opens it from the Settings hub.

### Accepted deltas — truth over art

These are corrections, not omissions. Every row keeps its slot.

- **Price is $12.99/month**, never the mock's $19.99. It is formatted from
  `PlanStore` on both platforms, and Billing says "Not activated" until an
  activation is recorded — there is no purchase path, so no install honestly
  has an "Active" pill or a next-billing date.
- **Only Grapsee Mail signs in.** Google and Apple keep their slots and say
  "Not connected" with **no chevron**. "Add another sign-in method" states that
  Grapsee Mail is the only credential.
- **No password store, no 2FA server.** Change Password and Two-Factor keep
  their slots; the 2FA switch is drawn disabled/off, Change Password has no
  chevron, and both subtitles say why.
- **Biometric Login is real on both platforms** (`BiometricPrompt` / `LAContext`)
  and the root shell actually gates on it. Android draws it disabled where the
  device has no enrolled sensor; iOS prompts before writing the preference.
- **Sessions are this install.** "Current Session" shows the real device and a
  persisted client id; the mock's second "Mobile Session" becomes "Other
  Sessions — No other device is signed in" with no fabricated timestamp.
- **No payment processor** — Payment Method says "Not set", Billing History says
  no invoice can be issued, and neither promises a chevron.
- **No integration adapters** — all eleven providers are "Not Connected". The
  green "Connected" / blue "Limited Access" pills would have been the most
  fabricated thing in the app. Every control still resolves: "…" and "+ Connect"
  open that provider's real status sheet, Learn More explains what a connection
  requires, Manage Permissions states that nothing is granted.
- **Email Notifications** is disabled and off: this build sends no mail.
- **Notifications Inbox** reads only `NoticeInbox` (Android) / `GsNoticeInbox`
  (iOS). Android writes after a shade post or an in-app record; iOS has no
  banner dispatcher, so `GsNotifier.record` is the in-app writer for the kinds
  the switches allow, with a sign-in and onboarding completion as its real
  producers. An install with no events is genuinely empty and says "You're all
  caught up"; Billing is always zero.
- **Help & Support content describes this build.** The mock's password-reset
  article is replaced by the Grapsee Mail article; Live Chat opens Nexly, the
  only chat that answers. Search, category filters, "View all" sheets, article
  sheets and mail intents are all real.
- **Artwork**: the mock's 3D crystal and mascot are not shipped assets. The
  sheets use the app's own artwork set (pearl-lotus bleed, Tabler brand marks,
  robot/headset glyphs) rather than a low-resolution crop of the mock. Brand
  marks whose identity is a multi-colour lockup stay monochrome.

### Parity corrections, third pass (after the iOS kit)

The first capture pass proved the screens exist. Comparing the six renders with
the mocks at their own scale found these structural drifts; all are corrected
on both platforms:

- **The inbox and the provider list are lists of separate cards, not one card
  with hairlines.** Both mocks draw every notice and every provider as its own
  20-corner card with a 5pt gap; the grouped card read as a table.
- **The inbox sorts at the reader.** `NoticeInbox.record` prepends, but the
  Today/Earlier split must not depend on file order, so both readers sort by
  `createdAt` descending. The interaction test seeds an out-of-order file and
  asserts the newer row paints first.
- **Product updates draw the mock's blue gear**, not a star — the same glyph
  the settings hub uses for system updates.
- **The caught-up card's rules flank the title.** They were flexible weights
  filled with `divider` at 55 %, which vanished into the cream glass; they are
  now the mock's two 28pt rules at 35 % tertiary, beside the title.
- **Provider status pills sit under the scope**, as drawn; only Microsoft, whose
  row also carries "+ Connect", keeps its pill in the trailing slot. The
  statuses themselves stay "Not Connected" (see the truth table above).
- **Connected Apps' five Available tiles share one height** and pin their "+"
  to the card floor; Figma's wrapped "Design & files" had pushed its "+" below
  the others.
- **The iOS inbox's category pill is inline beside the title**, not stacked
  under the timestamp in a fixed 74pt column.
- **The Account & Security profile hero is the mock's larger identity block**:
  56pt avatar and 13pt name on both platforms (it was 46pt/10pt, which read as
  another row rather than the page's subject).
- **The inbox captures are clock-deterministic.** Both readers take a `now`
  clock (production: the wall clock; the capture fixtures: a fixed 18:00). A
  run just after midnight had split 4h/8h/12h into Earlier, leaving only four
  cards; the card gate caught it and the fixture now cannot move with the hour.

Every kit addition they use is defaulted (`RefSheetRow.extra`,
`RefSheetTile.pinCorner`, `GsSheetRow.titleTrailing` / `detailTrailing`), so no
existing call site changes height or structure. The renders were re-taken after
these corrections.

## Acceptance

A capture is not evidence until a gate can fail on it. The Android six are
card-gated (header ink, lit rim, card rhythm) and verified locally with the
non-vacuity self-test; both platforms' section/row inventory is checked from
source with its own self-test. The iOS six run the same gate against the
simulator captures in `ios.yml`, but that gate has not yet been observed on an
iOS artifact, so iOS pixel parity is not claimed until it does. The gate
deliberately does not overlay the mock: the mock's phone panel is 393×~913 pt
while the capture is 393×852, so a pixel diff would be a scaling argument. That
comparison stays the human review these PNGs are for.

## Where each screen stands (2026-10-28, second pass)

All three are **built on Android**. `ReferenceRowInteractionTest` is the
always-on proof (9 JVM tests: hub → privacy → chat routes, model persistence,
reset confirmation, remember switch, four effort round-trips, and Model
Preferences visible without scrolling). The device captures are the pixel
evidence; the first capture (40650433868) and the final one (40659388170) are
named where they changed a decision.

**`model-selection.png` — built.**

- AI Model: the mock's four rows, both sentences of copy and the Recommended
  chip; each model keeps its mock tone; the chosen model is a check in a soft
  well, as drawn.
- Reasoning Effort: four across in one card with hairline column dividers, the
  mock's order, copy, timings, radios (hollow rings, accent ring + dot when
  selected) and the accent outline on the chosen tile. Each description is the
  mock's two lines; the radio announces `selected` semantics. The older
  three-tile Fast/Thinking/Auto control is deleted.
- Model Preferences: real switches. Auto-select is OFF and disabled; Remember
  my choices writes `SettingsStore.rememberChoices`; Reset to default opens the
  repo's confirm dialog, then writes pro / on / standard.
- The model radio writes `SettingsStore.preferredModel` (it used to be a local
  `remember`, so the tick reset every visit), and the mock's closing card
  ("Smarter choices. Better conversations.") is present.

**`privacy-data-controls.png` — built.**

The capture showed Personalization & Ads, Third-Party Integrations, Search
History and Data Retention still drawing a chevron; `RefNavRow`'s default
trailing IS a chevron, and `onClick = null` only removed the click. They now
pass `trailing = {}`. Chat History opens the search page for real. The hero
uses the same shipped lotus photograph and `LOTUS_BLEED_ALPHA` recipe as the
Settings hub, and the footer card carries the mock's title above the honest
body copy.

**`chat-history-search.png` — built on both (iOS twin 2026-10-30).**

- Six chips, as drawn. `More` opens a sheet with the two real facets the store
  can answer: Archived conversations (an archived preview flow) and Has files
  (an EXISTS over message attachments). The round filter button beside the
  field opens the same sheet.
- The topic chips filter both the recent list and the search hits through
  `drawerTopic()`; classification runs once per title on `Dispatchers.Default`,
  is cached process-wide, and filters inside `derivedStateOf`.
- Totals come from `ConversationDao.observeTotals()` — `COUNT(*)` + `MAX` over
  active conversations — not from the preview list's size.
- Recent Chats: ≤7 rows from the preview flow, real last-message preview
  (markdown flattened), pinned → Star/Mint, has files → Paperclip/Blue, else
  MessageCircle/Lilac, a real ⋮ opening `ConversationActionsSheet`, and a
  "View all" link to `CHATS`.
- Quick Actions: Search Chats focuses the field, Manage History → `CHAT_ARCHIVE`,
  Saved items → `SETTINGS_MEMORY`, Export Chat History writes the JSON export
  through `DataExport`. The footer CTA starts a new conversation.
- **iOS** (`ChatSearchView`, rebuilt): same page and inventory. The chips run
  the same shipped classifier (`DrawerTopic.topic(for:)`), totals are the active
  conversation count and newest `updatedAt`, the ⋮ is a real `Menu` over the
  store's pin/rename/archive/delete, Search Chats raises the real field, the
  facets sheet is the same two switches, and Export writes the same
  `ExportPayload` JSON through `ActivitySheet`. The empty states say what is
  true of THIS install rather than borrowing the mock's counts.

**`model-selection.png` — built on both (iOS twin 2026-10-30).**

The iOS page is the old `AiIntelligenceView` rebuilt to the reference: the four
model rows with the Recommended chip and check-in-a-well, the four-across effort
tiles with the mock's radios and timings, the three preferences (Auto-select off
and disabled, Remember my choices live, Reset behind the repo's confirm dialog),
the closing card, then the GS Intelligence hero, the model-source pair, the
on-device details sheet and the battery footnote. The effort tile writes
`settings.reasoningEffort` AND the `gs.mode.new` wire mode through the same
`wireModeForEffort` mapping Android uses, so the lit tile and the next reply
agree; "Remember my choices" still governs only the NEW-chat default.

## Resume order

1. Observe the next iOS run: the card gate, the Settings trio and the six-screen
   reachability test all now have their first CI calibration behind them (run
   41014412869 for the failure, the next run for the fix).
2. Gate the two newly ported screens the way the Settings trio is gated
   (measured rim + self-test) once their iOS layout is frozen; today their
   captures exist and their copy inventory is gated, but their card geometry is
   not.
3. Delete or wire the iOS-only dead surfaces: `SearchView` (superseded by
   `ChatSearchView`), and the unreachable `ResearchView`, `VisionView`,
   `FoldersView`, `SharedChatsView`, `NexlyChatShell`. Android has no twin for
   any of them; leaving them in the tree makes the screen inventory look larger
   than the app.

## Sub-page reachability (2026-10-30)

A route registered in the nav host is not a screen a reader can reach. The
Android hub had four such destinations — `SETTINGS_CHAT` (Chat &
Conversations), `SETTINGS_VOICE` (Voice & Speech) and `SETTINGS_ACCESSIBILITY`
(Accessibility); `SETTINGS_MODEL` was the fourth and is reached from the AI
page's "View model details" row. All three dead ones now have rows in the hub's
last group, and `ReferenceSheetsContentTest.theSettingsHubReachesTheFourSettingsSheets`
clicks each and asserts the route, so the next removal cannot pass silently.

iOS gained the matching **Chat & Conversations** and **Accessibility** rows
(they previously existed only as sections inside other pages). **Voice &
Speech** has no iOS twin: iOS's `VoiceView` is the live voice session, not a
preferences page, and inventing a settings page with no preferences behind it
would be the dead-control failure this project keeps removing. It is recorded
here as a platform delta, not built.

## Instrument notes worth keeping

The Settings gate is green on both platforms (`3dc6e9ff`) *because* several
instruments were repaired, each found by a failing run: iOS was being graded with
Android's rim column, Android's anchors were stale by 53pt after a row-pitch
change, two material windows were sampling the page instead of glass, the
screenshot suite discarded its own verdict twice (`|| true`, then a zero exit from
`am instrument`), and a probe reported `OK` while being provably unable to fail.
Do not trust a green from this rig without reading which probe produced it.

