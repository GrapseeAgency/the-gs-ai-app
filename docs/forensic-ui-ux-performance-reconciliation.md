# FORENSIC RECONCILIATION — UI/UX & PERFORMANCE
### the-gs-ai-app · current HEAD `783ff3d` vs known-good `0975be3` (v0.60.0)
**Read-only audit. No code changed. No measurements taken on device — every performance claim below is static-analysis inference and is labeled as such.**

---

## A. Current commit/state

| Item | Value |
|---|---|
| Current commit | `783ff3dc4cf426ccf692167a4e032f05734dcad2` (working tree clean) |
| Previous known-good (the build the user compared against) | `0975be3` — tag `v0.60.0`, "Platform-mechanism native audit" |
| versionCode / versionName | **61 / 0.60.0** — unchanged since `0975be3`; every commit in the window shipped under the same numbers |
| Installed device APK | `gsai-0.60.0-61-step5-debug.apk`, 19,985,255 bytes, SHA-256 `61838b21e828f76e1d4f8f48574f16c2de9bfad2e93273c3d4b54c0d6a96a20a`, signed `gs-live.keystore` (CN=GS AI, cert SHA-256 `b1ffd75d…a483`) — built from `21be974` + the single `fe9bd1c` fix |
| iOS source state | Source-complete per static gates (60 checks PASS), **but `ExploreView.swift:183` contains a literal unresolved git-conflict marker `>>>>>>> 4418691` at struct-body level — a hard Swift compile error the static gate cannot catch.** The iOS tree is not verifiably buildable right now. |
| Delta under audit | 92 files, **+12,662 / −2,672** (Android 44 files +6,566/−1,431; iOS ~+5,332/−1,239). No files deleted. Zero new dependencies, zero WebView. |

**Structural fact that frames everything below:** the entire delta between the "fast, good" build and the "slow, developer-y" build is one event — the **UI rebuild Steps 1–5** (plus two mechanism-commits and one device-validation fix). Both reported regressions live inside that single window.

---

## B. Complete change timeline since known-good UI

Chronological, per-commit, not summarized. Dates Sep 8–10 (repo clock).

**Before the window — what the known-good build actually was (Tasks 1–85, v0.1.0→v0.60.0):** the "Aeruo Kinetic" system — obsidian/paper surfaces, aurora accent `#2DD4A8` with the gradient `#2DD4A8→#4CC3FF→#9D7BFF` reserved for AI moments, serif display / sans body, drawer navigation, one feature per release in lockstep on both platforms (search, TTS, code blocks v0.9, highlighting v0.10, markdown-lite v0.11, explore rows, voice, projects, settings…). Critically, v0.60.0 was the **culmination of five performance passes** (v0.43 static-default aurora; v0.44 lint crash fix; v0.45 paged history + O(1) streaming + Room indices; v0.46 StringBuilder buffers + instant follow; v0.47 mic lifecycle; v0.48 baseline profile; v0.56 the "weightlessness" pass — 30 Hz isolated bubble repaint, transcript written once at finalize; v0.59 frozen-block stream rendering, press feedback, virtualised lists; v0.60 platform-mechanism audit). **The known-good was fast because five whole releases had been spent making it fast.** Its Home was a decorated canvas: breathing orb, rotating serif tagline, "Upgrade plan" pill, "Trending / Deep research agent" cards, a 9-chip wall. Its chat was messenger-style bubbles with markdown-lite prose. Its model UI was a hardcoded "Instant High"-style pill.

**In-window commits:**

1. **`102b5fe` — Audit gap closure** (17 files, +1,213/−375). Android: streaming Job moved out of composition into an **app-scoped `ChatStreamController`** (rotation-safe, 30 Hz coalescing preserved); forced-theme launch tint (`themes.xml` + pre-`super.onCreate` resolution); high-contrast/screen-reader CompositionLocals. iOS: four list surfaces converted to native `List` + `swipeActions`; `Aero.textMuted/outline` became computed high-contrast-resolving vars; auto-title honours settings. *Why: close the Task-85 audit's deferred items.* Perf-sensitive: yes — streaming ownership refactored (mechanism preserved).

2. **`e0f5cd7` — Reset-loss recovery** (11 files, +421/−64). Re-applied Task-87/88 mechanisms lost to a sandbox wipe: Toast→Snackbar with "Open Settings", orb haptics/TalkBack, `GsCard` long-press a11y, offline banner `AnimatedVisibility`, **reduce-motion now yields `EnterTransition.None` (not a faster tween)**; iOS edge-swipe gated to Home root, all 7 drawer transitions Reduce-Motion-flattened. Added `device-validation/CHECKLIST.md`. *Why: restore lost work, prep device validation.*

3. **`5e37a4d` — UI rebuild STEP 1: design-token foundation** (38 files, +1,644/−525). NEW `ui/theme/Tokens.kt` (Android `GsColors`, 49 semantic fields, light/dark/high-contrast constructors) and iOS `GSAccessibilityFlags` + four-way dynamic color; Material3-scheme mapping over raw literals **deleted**; NEW primitives `GsButtons`/`GsOverlays`/`GsAccessibility` ("buttons are not pills"); ~20 feature files migrated token-by-token. *Why: one authoritative token system, kill theme-mixup class.* Perf-sensitive: no (theme root recomposes only on settings flips). Visual: zero intended visual change — but this is the commit that made the later visual identity *enforceable*.

4. **`fe213c7` — UI rebuild STEP 2: app shell & navigation** (9 files, +491/−140). NEW route taxonomy: **SECTION/SESSION/DETAIL** (13 sections use `popUpTo(home)+restoreState`; drawer architecturally absent in sessions; chat always a fresh detail push); **new 220 ms section tier** vs 320 ms detail slide/parallax; **drawer rebuilt to selected-state hierarchy** (account header / dominant New chat / RECENT / primary / Tools / Account); screen-header system (`title/subtitle/largeTitle`) + **640 dp reading column**; iOS drawer width-bounded over tappable scrim, Home made a shell state (not a route). *Why: canonical navigation.* This commit changed how *fast the app feels to move through* — sections got quicker (220 ms vs the old 320 ms), drawer got heavier in content.

5. **`052560f` — UI rebuild STEP 3: HOME** (9 files, +1,218/−599). **The commit the "developer-oriented" reaction is largely about.** Deleted: rotating tagline, "Upgrade plan" pill, fake Trending rows, 9-chip wall, fake attach affordance. Added: real identity greeting (AccountStore), **real model pill** (`"GS Balanced · Balanced"` — ModelPrefs + catalog with mode suffix), live Continue/Recents (≤3), 3 curated starters, honest tool-discovery rows. Reduce-motion now never creates the breathe transition. iOS mirrors + `Aero.contentMaxWidth=640`. *Why: "content-driven AI workbench answering three Home questions with zero fake features."* Net effect: the consumer warmth was removed and replaced with productivity-tool structure — honest but colder, and it put the model name permanently on the most valuable pixel row in the app.

6. **`328ec33` — UI rebuild STEP 4: CORE CHAT** (6 files, +790/−270). Conversation-first transcript: assistant turns became **document-style (de-carded)** with group identity row; width-bounded user bubbles; quiet day separators; honest failed-turn state with working Regenerate (errors no longer dressed as prose). **Header gained the model chip + ⋮ overflow listing all 8 model names**; iOS got the AeroSheetShell model picker with the "applies to your next message" honesty line. Unified composer `[+][expanding field][send/stop][mic]`; iOS three-row stack replaced by one composer zone; NEW NetworkMonitor (iOS). *Why: conversation-first chat with real model control.* Perf-sensitive: transcript structure rewritten but streaming mechanisms explicitly preserved verbatim.

7. **`21be974` — UI rebuild STEP 5: message formatting & rich content** (19 files, **+6,182/−770** — the largest commit in the window, and the prime suspect for the slowdown). NEW `ui/chat/content/` package (8 files, 3,183 lines): `ContentBlocks` (typed block parser), `RichBlocks` (document renderers incl. tables), **`MermaidDiagram` (955 lines — native Canvas renderer, no WebView)**, `MathText`, `SyntaxHighlight`, `CodeBlockCard` (340 dp 2-axis scroll, copied state, highlight-on-finalize), `InlineMarkdown`, `ContentFixtures` (dev matrix — ships in release builds). ChatScreen −408 (segment machinery deleted) and wired to `BlocksContent` with per-bubble `StreamBlockCache` + `key(block)` frozen-prefix skipping. iOS mirrors 7 files / 2,910 lines, finalize-time-only rich render, NSCache. Security: scheme blocklist, https-only images. *Why: real Markdown/tables/math/Mermaid natively, stream-safe.* Perf-sensitive: **extremely** — this is where the hot path changed shape (see O).

8. **`fe9bd1c` — Step 5 device-validation build** (2 files, +35/−2). Blockquote accent bar fixed to full quote height via `IntrinsicSize.Min` + `fillMaxHeight` (adds an intrinsic measurement pass — small layout cost, see O/F5). APK rebuilt, verified, published to the release page. *This is the build the user tested.*

*(Non-code commits between the above: worklog entries `16d2d55`, `6df875d`; hash-noise commits `e4a14cd`, `c1e7ecf`, `350a2a1`, `43a348f`, `8278409`, `8e71ea3`, `783ff3d`; `be649c1` gitignore after a 146 MB toolchain archive was accidentally committed.)*

---

## C. Current complete UI inventory

Completeness verified two ways: agents inventoried every surface from view bodies; I then cross-checked the file lists (32 Android UI files, 34 iOS feature views — all covered) and ran a second overlay grep (Android: 6 ModalBottomSheets, 12+ AlertDialogs, 2 DropdownMenus, 3 plain Dialogs; iOS: 31 modal sites — 7 sheets, 4 fullScreenCovers, 9 alerts, 13 confirmationDialogs, 9 contextMenus).

### Navigation & shell
- **No tab bar on either platform.** Primary nav = drawer (Android `GsDrawer` 304 dp; iOS `AeroDrawer` min(340, 85%)). Drawer contents: account header (initials avatar, name/email, "Pro" chip) → dominant "New chat" button → RECENT (≤5 live; **5 hardcoded sample chats when store is empty**) → All chats/Archived → Home/Chats/Explore/Create/Library → TOOLS: Projects/Assistants/Models/Search → ACCOUNT: Profile/Notifications/Billing/Settings. 14+ rows, 3 labels.
- 32 Android routes / 26 iOS routes; SECTION (13) / SESSION (2) / DETAIL taxonomy; 220 ms section transitions, 320 ms detail slide+parallax, predictive-back scrubbing; iOS edge-swipe drawer gated to Home root; iPad = same single column with 640 pt content clamp (no split view).
- Dead code: iOS `AeroTab` enum (5-tab legacy), unreferenced.

### Screens (composition as actually built — F=fabricated content)

| Screen | Actual composition (background · structure · controls) | Data honesty |
|---|---|---|
| **Home** | appBackground; top bar = menu circle · **model pill** (aurora dot + "GS Balanced · Balanced") · add circle; 84 dp breathing aurora orb + serif greeting (real first name) + subline; Continue (≤3 real conversation cards) + "All chats"; "Start something" 3 icon rows; "Tools" 5 cards (Research/Create/Search/Projects/Assistants); LiveUpdate pill; **pinned composer ENTRY bar** (not a composer — routes to chat) + mic + 62 dp hold-to-dictate orb with halo; disclaimer line | Real identity/recents; starters are curated seeds. 4 aurora elements, 2 infinite animations |
| **Chat** | scaffold (back, title, Search icon, **model chip**, ⋮ overflow → **8 raw model names**); offline banner; find-in-chat bar; LazyColumn, 10 dp gaps, day pills; assistant = document-style de-carded prose + group identity row; user = right bubble ≤340 dp accent-14%; action row = HH:mm + Copy/Regenerate/Read aloud/Share (long-press adds Translate/Save/Branch); composer = attach · expanding field · send/stop · mic; aurora 4 dp bar while streaming; failed-turn notice with Regenerate | Fully real (Room + app-scoped stream controller) |
| **Chats** | header + search + add; pull-to-refresh; **3 quick-link cards (Folders/Archive/Shared)**; filter chips All/Pinned/Unread; real conversation rows w/ pin star + overflow | Rows real; **Folders 100% fake (3 hardcoded folders, all taps dead); Shared 100% fake (3 rows, fabricated view counts, dead copy/revoke)**; "Unread" filter = hardcoded empty |
| **Archived chats** | rows + unarchive/delete | Real |
| **Global search** | input + recents chips + 6 kind chips; FTS over conversations/messages/library/assistants/projects, grouped counts | Real |
| **Find-in-chat** | overlay field, n/m count, prev/next, highlighted matches | Real (iOS "Has files"/"Model" filter chips decorative) |
| **Explore** | search + category chips + 5 carousels: Top picks / Trending / **Popular prompts (3 hardcoded, "8.2k uses")** / **Featured tools (2 hardcoded)** / Made by you | **Catalog = 6–8 sample assistants with fabricated ratings/uses + real user creations** |
| **Create** | "Make anything" + 10-card grid (image, image-edit, document, presentation, spreadsheet, writing, code, diagram, prompt, assistant) + recent creations | Grid real routing; **studios fake (below)** |
| **Image Studio** | prompt card, style/aspect chips, generate → progress → **4 grey placeholder tiles** | **100% fake** (saves prompt text only) |
| **Writing Studio** | type/tone chips, brief → outline → **canned drafts per type** | **100% fake** |
| **Code Workspace** | **file chips of a hardcoded Kotlin project; Editor/Output/Diff tabs; "✓ Build succeeded in 1.2s" console** | **100% fake IDE** |
| **Prompt Builder** | role/context/goal/format cards → assembled prompt (real string) | Real (as a tool) |
| **Library** | search + 6 chips + **3 fake collection cards** + real saved items + **4 fake file seeds**; reader sheet (Continue/Copy/Delete) | Mixed; collections/files fabricated |
| **Projects** | grid of real store-backed projects + creation dialog | Real |
| **Project detail** | hero + instructions card + 4 chip-tabs: Chats (real linker) / Files (gate) / Activity (real log) / Members (gate) | Real + honest gates |
| **Assistants** | 5 segment chips; featured card; 2-col cards with **fabricated ★4.4–4.9 / 12.4k uses** for samples; full CRUD for user assistants | CRUD real; marketplace stats fake |
| **Assistant detail** | hero, category chip, description, starters, Start chat; favourite/edit/delete/share | **Hardcoded starters + capabilities for every assistant; stored instructions ignored** |
| **Assistant create/edit** | full form, real upsert | Real |
| **Model Centre** | default card; **"Reasoning mode" = 8 chips (Fast…Deep reasoning…)**; "All models" = 8 rows with **"· 128K context" subtitles**, speed dots, capability chips, Set as default | Real store-backed; **concept-set is developer-oriented** |
| **Model Compare** | 8 picker chips → spec table rows **Context ("128K" monospace) / Speed dots / Tools / Reasoning / Vision / Voice / Default** | Real writes; **spec-sheet UI** |
| **Research** | "Research mode" chip; run → **"Reading 5 sources · Cross-checking claims…" → canned synthesis with [n] markers, 5 fake sources with relevance %, export chips → "queued" toast that does nothing, 3 fake recents** | **100% fake, status lines fake** |
| **Vision** | source chips (no camera/gallery), **fake "IMG_2041.jpg · 3.2 MB"**, fake analysis, canned detections w/ confidence bars, canned OCR, canned chart, canned follow-up Q&A | **100% fake** |
| **Voice** | status machine (9 honest states), 24-bar waveform (only while mic open), transcript card (Send to chat/Copy/Try again), 3 controls, "Speech stays on this device" | **Real** (on-device speech) |
| **Profile** | "GA" avatar, **hardcoded "Grapsee Admin" / graphesee@gmail.com**, fake usage card (1,284 msgs / 45 m voice / 32 images, 68% bar), fake Devices/Security/Sessions values | **~100% fabricated identity** |
| **Settings** | 8 accordion cards: Appearance (real theme chips), Chat (**hardcoded "GS Balanced" value row** directly above the real model row), AI (**"Reasoning effort Low/Med/High" — persisted, wired to nothing**), Privacy (real export/wipe/crash report), Security (real passcode toggles + **fake "Two-factor On / Trusted devices 2"**), Notifications, Language, Accessibility (all real), About | Mixed; contradictions present |
| **Billing** | plan card (**local `remember` state**), **fake usage quotas**, credits card, 3 plan cards with "All 8 models / 2,000 messages", **payment methods "Visa •• 4242 / Apple Pay" hardcoded**, invoices whose "download" **saves a fabricated invoice into the real Library** | **~100% fake** |
| **Auth** | 7-step wizard; social buttons simulate; any 6-digit code verifies; identity persisted at 2FA | Simulated but honest-ish; real identity persistence |
| **Onboarding** | 6-step wizard, 12-chip interest wall, **"Reasoning effort" preference cards**, name + 3 accent swatches | **Nothing persists** (all @State) |
| **Notifications** | 8 hardcoded samples, deep links | **Fake** |
| **Translate sheet** | streaming translation of selected turn | Real |
| **Attach sheet (chat)** | attach affordance with honest messaging about availability | Honest stub |

### Rich-content surfaces (chat)
Paragraph · H1–H6 · bullet/ordered/nested lists · blockquote (full-height accent bar) · **code card** (language label, copy w/ 1.5 s copied state, 340 dp cap, 2-axis scroll, per-language highlighting at finalize) · **tables** (alignment, fit-vs-scroll region, hairlines) · divider · **math** (latex-lite, verbatim fallback) · **Mermaid** (native flowchart TD/LR/BT/RL + sequence, fullscreen pinch/pan viewer, honest unsupported-family fallback + source viewer) · image (https-only, 8 s/8 MB caps) · collapsible (`<details>`) · citations & tool-result seams (render **only** when backend supplies data — never faked). Stream-safe: open constructs stay stable representations; Mermaid never laid out mid-stream.

---

## D. Current visual system

- **Tokens:** Android `GsColors` = 49 semantic fields across 9 background roles / 7 text / 7 structural / 13 semantic / 6 AI-specific / 6 code + aurora; iOS mirrors (40 named tokens). Raw registry: 46 values. **Hard-coded color audit: Android ZERO hits outside the token files; iOS exactly 2 stray `Color.black` shadow literals.** High-contrast variants exist for the critical text/border tokens on both platforms.
- **Palette:** Dark = obsidian `#0A0D12` background → surface `#11151C` → raised `#181E28`; light = paper `#F7F7F5` → white surfaces. Single accent family (aurora teal `#2DD4A8`, deep `#0FA37E`, soft 10–14%), one 3-stop aurora gradient confined by comment-contract to "AI-active moments" (streaming bar, model dots, orb). Code palettes are per-theme GitHub-ish wells.
- **Type:** 15 Material slots + 4 custom roles; serif display/headline + sans body; 13/14 sp mono for code; all Dynamic Type–relative on iOS; fontScale 0.8–1.4× on Android.
- **Shape/space:** radii 10/14/16/24/26/pill; spacing 4–32; elevation flat/elevated/floating; **640 dp/pt reading column** on both platforms.
- **Motion:** 3 springs, press-scale 0.97, 30 ms stagger, section 220 ms / detail 320 ms; reduce-motion honored almost everywhere → transitions become `None`; the streaming aurora bar and voice waveform are deliberately not gated (defensible; documented).
- **Surface treatment:** border-led cards (1 dp outline, minimal shadow), de-carded assistant prose, sheet/dialog kit shared (`GsSheet`/`AeroSheetShell` — though iOS only 1 of ~6 custom sheets actually uses the shared shell).

**Assessment: the token/visual layer is the strongest part of the app.** It is disciplined, dual-theme coherent, and enforced. The problem is not how the app paints; it is what the screens choose to say and prioritize.

---

## E. Actual design philosophy (no adjectives, decisions only)

**What the visual identity actually is:** a dark-first editorial surface (obsidian/paper) with a single teal accent and a 3-stop "aurora" gradient used as a life-sign during AI activity; serif display type for identity moments; border-led cards; document-style assistant prose; drawer navigation; everything clamped to a 640 dp reading column.

**Actual information hierarchy (as built, not as intended):**
1. Model registry (Home top pill, chat header chip, overflow, two full screens, settings row, billing copy)
2. Conversation (chat transcript, composer)
3. Continue/Recents
4. Tools & workspaces (5–10 cards)
5. Everything else

**Intended content density:** reading-first in chat (good), airy workbench on Home, dense store on Explore, spec-sheet on Models.

**Intended navigation model:** drawer-first, sections snap Home-ward, chat always a fresh push. This matches the product class.

**Intended chat hierarchy:** assistant = document on canvas with identity row once per group; user = bounded bubble. This matches the product class.

**Intended composer hierarchy:** one row, expanding field, send/stop in the trailing slot, mic, attach. This matches the product class.

**Comparison with the modern AI-assistant class, conceptually:** the app's *chat surface* is structurally contemporary; its *front door and chrome* are not — the front door presents a model registry and a tool workbench where the class presents a single giant invitation to type.

**Why these decisions were chosen:** the Steps 1–5 rebuild had an explicit "zero fake features" honesty mandate (correct instinct), a "content-driven workbench" framing for Home, and a "real model control" requirement for chat. Each individually defensible; compounded, they re-centered the product on its machinery instead of its conversation.

**Inherited from the old design (Aeruo Kinetic, Step 0):** obsidian/paper palette, aurora accent + gradient rule, serif display, orb, drawer nav, press-scale/stagger motion, bordered cards, sheet/dialog grammar.

**Introduced by this rebuild (Steps 1–5):** the semantic token system, route taxonomy + transition tiers, 640 dp column, document-style chat, header system, the workbench Home composition, **the model pill/chip/overflow/ModelCentre/Compare surface family**, the content-block pipeline.

**Genuinely necessary (would survive any rebuild):** token system; document-style chat + quiet action row; composer anatomy; app-scoped stream controller; route taxonomy; drawer skeleton; 640 dp column; offline/honest-state machinery; rich-content capability.

**Merely stylistic preference (defensible but not necessary):** the breathing orb; serif greeting; aurora-dot in the model pill; speed-dots on model rows; "workbench" framing; featured-card 4 dp accent bars; stagger entrances.

**Actively harmful decisions:** mode-suffix labels ("GS Balanced · Balanced"); exposing context-window figures ("128K context") and a Context/Reasoning spec table to consumers; "Reasoning effort" chips wired to nothing; ~20 fabricated surfaces (Research/Vision/studios/Billing/Profile/Notifications/Folders/Shared/fake stats/fake invoices); duplicate contradictory settings rows; hardcoded sample identity.

---

## F. Ordinary-user vs developer-oriented analysis

The primary audience is an ordinary person who wants to talk to an AI. For every visible control: *would a normal user understand why this exists?*

| Surface / control | Verdict | Class |
|---|---|---|
| Drawer: New chat, recents, Chats, Explore, Search, Profile, Settings | Understood instantly | **Essential** |
| Drawer: Library, Projects | Understood with one use | **Essential/Secondary** |
| Drawer: **Models as a top-level destination**; Notifications; Billing | "Why do I pick a model? why are models a place?" | **Advanced → Developer-only as top-level** |
| Home: greeting + composer entry + Continue | Understood | **Essential** |
| Home: **model pill "GS Balanced · Balanced"** | Redundant suffix; implies a choice the user didn't make | **Advanced (label is Developer-oriented)** |
| Home: Starters, Tools rows | Understood, but compete with composer | **Secondary** |
| Home: hold-to-dictate orb | Discoverable only by accident; needs a hint | **Secondary** |
| Chat header: model chip + ⋮ 8-name overflow | Over-prominent; raw catalog names | **Advanced** |
| Model Centre: "Deep reasoning" / "128K context" / speed dots / capability chips | Architecture vocabulary | **Developer-only** |
| Model Compare screen (Context/Reasoning spec table) | A spec sheet | **Developer-only** |
| Settings: "Reasoning effort Low/Med/High" | A parameter dial; wired to nothing | **Developer-only / Unnecessary** |
| Settings: duplicated hardcoded "GS Balanced" row | Contradiction visible to anyone who changes the model | **Unnecessary (defect)** |
| Chat: action row (Copy/Regenerate/Read aloud/Share) + timestamps | Matches class expectations | **Essential** |
| Chat: long-press menu (Translate/Save/Branch) | Fine hidden behind long-press | **Secondary** |
| Code block chrome (language label, copy) | Understood | **Essential** |
| Tool-result block ("search · completed") | Pipeline vocabulary (currently unreachable — backend seam only) | **Developer-only** |
| Research "Reading 5 sources…" / Vision "IMG_2041.jpg · 3.2 MB" | Fake execution states | **Unnecessary (fabrication)** |
| Billing "All 8 models / 2,000 message quota" | Quota math for a product with no backend billing | **Unnecessary (fabrication)** |
| Code Workspace (fake Kotlin IDE) | A developer persona toy in a consumer app | **Developer-only / Unnecessary** |
| Onboarding "Reasoning effort" preference cards | Unpersisted and unexplained | **Developer-only / Unnecessary** |
| Profile fake usage stats / Devices "3" / Security "Strong" | Fabrication; any user who checks will distrust the app | **Unnecessary** |
| Offline banner, failed-turn notice, LiveUpdate pill | Honest status; understandable | **Essential/Secondary** |

**Verdict: the concern is confirmed, and it is concentrated.** The chat surface itself — the part users live in — is user-oriented and matches the class. The drift is in (a) the model-registry surface family, (b) the fabricated feature estate, (c) execution-status vocabulary, (d) the workbench framing of Home. An ordinary user does not need to understand model architecture to chat — today they meet it on screen one.

### Ordinary-user 5-second test per major screen

| Screen | 5-second comprehension | Why |
|---|---|---|
| Chat | **YES** | Title, transcript, familiar composer; sending/stopping/attaching all discoverable |
| Voice | **YES** | One purpose, honest states |
| Auth | **YES** | Conventional wizard |
| Chats / Archive / Search | **YES** | Familiar list+search grammar |
| Projects | **YES** | Plain cards + obvious create |
| Home | **PARTIALLY** | Composer is obvious, but Tools/Starters/model pill compete; "GS Balanced · Balanced" begs a question |
| Chats quick-links (Folders/Shared) | **PARTIALLY** | Tapping them reveals dead/fake content — confusion, not clarity |
| Explore / Create / Library / Assistants | **PARTIALLY** | Clear shapes; fabricated stats/usage counts mislead on second look |
| Settings | **PARTIALLY** | Contradictory rows; "Reasoning effort" unexplained |
| Model Centre / Compare | **NO→PARTIALLY** | Spec-sheet vocabulary; a normal user cannot act on "128K context" |
| Research / Vision / Image/Code studios | **NO** | They invite input and then fabricate results — the worst failure mode for trust |
| Billing | **NO** | Prices/quotas/payment cards that are all local fiction |

---

## G. Model-selection prominence analysis

**Where it currently appears (7 surfaces):** Home top pill · Chat header chip · Chat ⋮ overflow (8 raw names, 2 taps to switch, applied next message — the *only* genuinely good fast path) · Model Centre (full screen: 8 mode chips incl. "Deep reasoning", "· 128K context" subtitles, speed dots, capability chips) · Model Compare (spec table, "Set default" chips) · Settings (hardcoded row + real row) · Billing copy.

**How prominent:** the model name is the **center pixel of the Home top bar** and sits **leftmost in the chat toolbar before the search icon**. It is the most-repeated non-content string in the app.

**What the user sees:** fictional model names ("GS Balanced"), a duplicated mode suffix ("· Balanced"), context-window figures, throughput dots, capability taxonomies — none of which an ordinary user can act on, all of which imply architecture decisions they shouldn't need to make.

**Does it interrupt the conversation?** Tapping the chip routes *away from the chat* to a full screen (Android) — a context exit. The iOS sheet and the Android ⋮ menu do not interrupt. So the fast path exists but the *prominent* path interrupts.

**Does an ordinary user need to interact with it?** No. The send path already validates the stored id and falls back to the server default. The correct default experience is: no model thinking at all.

**Can it be simplified?** Yes, without deleting functionality: keep one low-key control in the chat header (model **name only**, no suffix; tap → sheet with 3–5 user-language tiers); move the full catalog, modes, context figures, and compare table behind Settings → Advanced (or delete Compare); Home pill → either remove or reduce to a static "GS" mark; delete the duplicate Settings row and the unwired Reasoning-effort chips. This preserves every existing store key and capability while re-centering the hierarchy on conversation.

**Correct hierarchy:** conversation first; model choice = a quiet, available, skippable control; model *education* = opt-in.

---

## H. Dark-theme audit (the default)

Dark is obsidian `#0A0D12` with a 3-step surface ladder (`#11151C`/`#181E28`/`#232B37` outlines). Verified: launch window tint resolved pre-inflation (no white flash); status bar follows; all 49 semantic tokens have explicit dark values; code wells use a dark GitHub-ish palette; user bubble = accent 14% on obsidian (sufficient contrast); aurora bar/streaming dots visible; sheets/dialogs/nav surfaces all themed; high-contrast variant raises text/border contrast. No mixed-fragment risk found (zero off-token colors). **Verdict: intentional and coherent. Keep.** Minor notes: table hairlines at 25% alpha are near-invisible on raised surfaces in dark; mermaid canvas tokens were verified theme-resolved.

## I. Light-theme audit

Light is paper `#F7F7F5` → white surfaces with the same accent and the same component grammar — genuinely the same product in a second appearance (single token table, four-way dynamic resolution). Verified: launch tint `#F7F7F5`; text hierarchy holds; borders/dividers visible; code well switches to light palette; aurora reads well on paper. No light-only decorations, no divergent hierarchy. **Verdict: intentional and coherent. Keep.** Minor notes: white-surface-on-white-background distinction relies almost entirely on the 1 dp outline (thin in bright sunlight); accent-soft 10% fills are faint on paper.

**Theme system conclusion: themes are NOT a source of the current problem.** The two-theme simplification the user mandates is already the implemented reality (Light/Dark/System chips; no public theme machinery beyond that; system respected internally).

---

## J. Home audit — is it the fastest path to a conversation?

**No.** Concretely:
1. **The composer is an entry, not a composer** (the code comment says so): tap → route push → type. The product class makes the front-door input *be* the conversation. One extra hop on the single most frequent action in the product.
2. **Competing primary actions:** the screen simultaneously offers the composer, 3 starters, 5 tool cards, Continue/Recents, and the model pill. Five invitation systems.
3. **Decorative space:** the 84 dp orb + halo is pure decoration occupying the hero position; the greeting's serif display is identity theater. The class uses that space for the input itself.
4. **Developer information:** the model pill ("GS Balanced · Balanced") is the first non-navigation element the eye meets.
5. **What's genuinely good:** real identity, real Continue/Recents, honest starters, zero fake features, pinned composer position, LiveUpdate honesty.

**Verdict: Home must be recomposed, not re-skinned.** The screen exists to answer "what can I do here?" — today it answers "here is my tool bench and model registry."

## K. Chat audit (ordinary-user lens)

- **First impression: good.** Title, back, transcript, composer all read instantly; send/stop/mic/attach are where thumbs expect them; stop-as-send is correct; failed turns are honest.
- **Reading experience:** de-carded document prose with grouped identity = looks like an intelligent response, not a developer artifact — **when it renders**. During streaming, the caret + aurora bar are class-appropriate. BUT the renderer is the perf regression epicenter (O): long/structured answers are where "slower" will be felt most — exactly the answers that make the app look smart.
- **Controls:** 4 inline actions + 3 long-press actions + quiet timestamps = within class norms (ChatGPT/Claude carry similar). Not too many.
- **Composer:** familiar and correct. Attach is an honest stub.
- **Model controls:** the chip's redundant suffix and the 8-name overflow are the two developer-oriented elements on this screen. The chip being leftmost in the toolbar over-weights it.
- **Density:** conversation has priority; the 640 dp column, quiet grouping, and day pills are right.
- **Verdict: keep the anatomy wholesale; demote the model chip; fix the streaming pipeline.**

## L. Navigation audit

Drawer-first matches the class. SECTION/SESSION/DETAIL taxonomy is sound and back behavior is predictable. Issues: **over-exposure** — Models, Billing, Notifications as peer drawer destinations elevates machinery to parity with conversation; the Chats quick-links surface two dead/fake screens (Folders/Shared) in prime position; drawer recents show hardcoded samples when empty (the only remaining fake on an otherwise real surface); dead `AeroTab` enum lingers. **Verdict: keep the skeleton; prune the destinations; kill the last fakes.**

## M. Message-rendering audit

Assistant: identity row once per group → de-carded prose → quiet action row → honest failure state. User: bounded accent bubble with edit/branch. This is the correct class-matching anatomy and it is *better* than the old messenger bubbles. Streaming isolation (only the live bubble reads the stream state) is architecturally right. **Verdict: KEEP — this is the strongest structural work of the rebuild.** The only corrections are perf (O) and the expand-state loss bug (U).

## N. Rich-content audit

Capability-wise the app now renders the full matrix the user demanded (markdown/links/code/tables/math/mermaid) natively, stream-safely, with security blocklists and honest degradation — no WebView, no new deps, no fabricated citations. This is a genuine differentiator vs the old markdown-lite. Costs: ~3,200 lines/platform of renderer code; `ContentFixtures` dev matrix ships in release builds (dead weight, must be debug-only); the perf findings in O; image blocks lack a bitmap cache. **Verdict: KEEP the capability; it is not the source of the developer-oriented feel (it renders *content*, not chrome); FIX the parser mechanics.**

---

## O. Performance regression analysis (static analysis — nothing measured on device; all findings INFERRED, not MEASURED)

**P1. [HIGH — the smoking gun] The frozen-block streaming contract silently disables itself on the most common answer shapes.**
`parseStreamingBlocks` (`ContentBlocks.kt:499–514`) falls back to a **full re-parse of the entire accumulated message on every ~33 ms flush** whenever the last block's `sourceStart <= 0`. `BulletList`/`OrderedList` hardcode `sourceStart = 0` (`ContentBlocks.kt:448, 450` — I verified these lines personally), and the first paragraph of any answer starts at offset 0. Consequence:
- during the opening seconds of **every** reply, and
- during the entire rest of any reply whose tail is a growing list (how models end most answers),
…each flush does an O(document) parse, allocates a **new instance of every block**, which defeats `key(block)` instance-identity skipping and forces deep structural equality checks over the whole message ~30×/s. The known-good baseline re-ran **one precompiled regex over the tail** per flush. This is the single best mechanical explanation for "noticeably slower," and for "layout jumps/frozen feel" during list-heavy answers.
*Proposed fix (not implemented):* give list blocks real source offsets (thread the first list line's offset through `parseList`), relax the `lastStart <= 0` bail-out to "cache-cold only," and add an incrementality unit test asserting head-block instance identity across 100 synthetic flushes for paragraph-first / list-tail / code-tail streams.

**P2. [MEDIUM-HIGH] Tail-work constant inflation (~order of magnitude).** Re-parse unit moved from *line* (per-line classification, per-line remembered inline render) to *block* (whole tail block re-parsed + re-rendered; every list item re-laid-out in a non-lazy `Column`). The inline parser grew from a 4-alternation regex to a **16-alternation recursive** one; up to ~9 `matchEntire` regexes per line. *Fix:* line-keyed inline-span cache in the tail parser; allocation-free fast path for plain text; consider background parsing with the frozen-prefix guard.

**P3. [MEDIUM] 30 Hz allocation/GC churn.** Per flush: `replace("\r\n","\n")` + `split` copies, new block/span trees, new `AnnotatedString`s, a fresh `SpanTheme` **per block per recomposition** (`rememberSpanTheme` isn't remembered), a new `RoundedCornerShape` per recomposition (`GsRadius.smShape()` at `ChatScreen.kt:1358`). Young-gen GC pauses during streaming on mid/low-RAM devices. *Fix:* hoist theme/shape constants; skip normalization when no `\r` present.

**P4. [MEDIUM, one-time per message] Finalize hitch + state loss.** The isStreaming flip re-parses everything and adds per-paragraph `SelectionContainer`s, `highlightCode` per code block, table `BoxWithConstraints`, quote intrinsics — in one frame ("answers land with a stutter"). Worse: `CollapsibleBlockView`/`ToolResultBlockView` `remember(block)` expand-state resets whenever F1's fallback re-instantiates blocks — a correctness bug, not just perf. *Fix:* key expand-state on `sourceStart`; stage finalize work.

**P5. [LOW-MEDIUM] Layout additions.** Blockquote `IntrinsicSize.Min` double-measures its subtree (the `fe9bd1c` fix — correct visually, expensive when the quote is the live tail); code blocks nest 2-axis scroll under a 340 dp cap; tables/math add horizontal scroll regions inside LazyColumn items — more measure passes per tail re-layout. *Fix:* draw-phase accent bar instead of intrinsics; these are bounded costs elsewhere.

**P6. [LOW] Image blocks re-download on scroll return** (no cross-disposal cache). Mermaid is clean: finalize-only layout, memoized O(V+E), canvas redraws only on invalidation.

**P7. [LOW] CRLF offset mismatch** — incremental bookkeeping slices raw content while offsets are computed against normalized content; breaks block identity only when the backend emits `\r\n`. Normalize once.

**P8. Confirmed NOT guilty (mechanisms preserved from the perf era):** 30 Hz coalescing; StringBuilder buffers; app-scoped stream ownership; single-bubble recompose isolation; `key=message.id` + `contentType`; highlight-on-finalize; paged history (60); baseline profile + ProfileInstaller; formatter caches; Home animation inventory (net *improved* — tagline loop deleted); **the entire iOS streaming path** (plain-Text live bubble, finalize-only parse, NSCache, Equatable row skipping).

**Honest caveat:** no FPS/frame-time/allocation measurements exist. The next step (if instructed) should include Compose compiler metrics + Layout Inspector frame sampling on-device, plus a Macrobenchmark of chat streaming against the `0975be3` APK to convert these inferences into measured regressions before and after fixing.

## P. Previous build vs current build — comparison table

| Dimension | v0.60.0 (`0975be3`) | Current (`783ff3d`) | Verdict |
|---|---|---|---|
| Chat streaming | per-flush tail regex, line-cached render | block pipeline with self-disabling incrementality (O/P1) | **Regressed** |
| Chat anatomy | messenger bubbles, errors-as-prose | document-style, honest failures | **Improved** |
| Message capabilities | markdown-lite + code | full markdown/tables/math/mermaid | **Improved** |
| Home | decorated marketing canvas (fake trending, upgrade pill, chip wall) | honest workbench (identity, recents, starters, tools) | **More honest, less inviting; hierarchy wrong** |
| Model UI | hardcoded pill ("Instant High") | real registry on 7 surfaces | **More honest, developer-oriented** |
| Navigation feel | 320 ms everywhere | 220 ms sections + 320 ms details | **Improved** |
| Theme integrity | partial mixups possible | enforced token system, zero off-token colors | **Improved** |
| Fake content | trending rows, upgrade pill, attach | ~20 fabricated surfaces (see C) | **Worse in breadth** |
| Settings | fewer, mostly real | more, with contradictions + unwired dials | **Mixed → worse** |
| iOS buildability | compiles | conflict marker at `ExploreView.swift:183` | **Regressed (defect)** |
| Startup | baseline profile, forced-theme tint | same + theme resolution earlier | **Equal** |
| Lists | LazyColumn | LazyColumn + contentType + virtualised iOS Lists | **Improved** |

**Net:** the rebuild genuinely improved structure, honesty of chat, capabilities, and theming — while introducing one high-severity hot-path regression, a fabricated-feature estate, and a model-registry-first hierarchy. The user's two complaints are both real and both localized.

## Q. Primary / Secondary / Contextual / Decorative / Unnecessary classification

| Element | Class |
|---|---|
| Chat composer (field, send/stop, mic) | **Primary** |
| Chat transcript (assistant prose, user bubbles) | **Primary** |
| Drawer New chat + recents | **Primary** |
| Home composer entry | **Primary** (and should become the *only* primary) |
| Chat header (title, back, search) | **Secondary** |
| Model chip / pill / overflow | **Secondary → should be Contextual** (currently dressed as Primary) |
| Model Centre / Compare | **Advanced → Developer-only** |
| Continue/Recents cards, Starters | **Secondary** |
| Tools rows, Create grid | **Secondary** |
| Action row per turn, find bar, offline banner, LiveUpdate pill | **Contextual** |
| Jump-to-latest, day pills, timestamps, copied states | **Contextual** |
| Breathing orb, halo, aurora dots, serif greeting flourish | **Decorative** |
| Library collections/files seeds, drawer sample recents, Explore stats, marketplace ratings | **Unnecessary (fabricated)** |
| Research/Vision/Code/Writing/Image fake results & statuses | **Unnecessary (fabricated)** |
| Billing plans/quotas/payment cards, Profile usage/devices/security values, Notifications samples | **Unnecessary (fabricated)** |
| Reasoning-effort dials, "128K context" figures, speed dots, "GS Balanced · Balanced" suffix | **Unnecessary for consumers / Developer-only** |

A screen should have ~1 primary; Home currently has 5, chat has ~1.5.

## R. KEEP / REDESIGN / DELETE matrix

**KEEP (working, correct, class-matching):**
- Entire token/theme/motion system (Steps 1); both themes; high-contrast machinery
- Chat anatomy: document-style assistant turns, identity rows, quiet action row, failed-turn honesty, edit/branch, drafts
- Composer anatomy (stop-as-send, mic, attach stub)
- Streaming architecture: app-scoped controller, 30 Hz coalescing, bubble isolation, highlight-on-finalize, frozen-prefix *concept*
- Route taxonomy, transitions, 640 dp reading column, screen-header grammar
- Drawer skeleton (account header / New chat / recents / groups)
- Real features: search (global+find), Library saves/reader, Projects + linker, Assistants CRUD, Voice (real speech), TTS, Translate, offline banner, LiveUpdate, Settings accessibility/privacy/export
- Rich-content capability (code/tables/math/mermaid/citations) incl. security blocklists
- Paged history, baseline profile, all v0.43–0.59 perf mechanisms

**REDESIGN (function useful, structure/hierarchy wrong):**
- **Home**: recompose conversation-first — composer becomes a real inline composer or the hero becomes the input; orb/greeting shrink to a header moment; one invitation system (continue/recents), starters condensed to ≤3 quiet chips; model pill removed or reduced to a static brand mark
- **Model selection family**: chat chip → name-only, sheet-based (never a route exit); Model Centre rewritten in user language (3–5 tiers: "Fast / Everyday / Best for hard problems") with modes/contexts/capabilities collapsed behind an expander or moved to Advanced; Compare deleted from consumer nav
- **Settings**: delete duplicate hardcoded model row; relocate/remove "Reasoning effort"; make AI-language and language rows honest; keep everything real
- **Assistant detail**: honour stored instructions/starters/capabilities
- **Chats hub**: quick-links become Archive (real) only; filters made real or removed
- **Explore/Create/Learn surfaces**: keep structure, replace fabricated stats with honest counts or none
- **Onboarding**: persist or reduce to 2 honest steps (name + notifications)
- **iOS sheets**: standardize on the shared AeroSheetShell

**DELETE (fabricated, dead, or developer-only):**
- All fabricated data: Profile identity/usage/devices; drawer sample recents; Chats/Explore/Library/Assistants sample stats & seeds; Notifications samples; Billing plans/credits/payment methods/invoice fabrication (esp. writing fake invoices into the real Library); Research/Vision/studios canned results and fake execution states ("Reading 5 sources…", "✓ Build succeeded")
- The fake **Code Workspace** IDE (or quarantine behind an explicit "coming soon" gate — currently it simulates a compiler)
- Folders & Shared chats dead surfaces (until real)
- Settings "Reasoning effort" chips; Model Compare; "· mode" suffixes; context-window/capability chip exposure in consumer surfaces
- `ContentFixtures` from release builds (debug-only); dead `AeroTab`; hardcoded "GS Balanced" rows

## S. Screens that should be completely rebuilt
1. **Home** (composition, not skin)
2. **Model Centre + Model Compare** (rewrite in user language or demote behind Advanced)
3. **Research & Vision** (real backend or honest "coming soon" cards — current fake-pipeline states are trust-destroying)
4. **Profile** (real AccountStore identity or nothing)
5. **Billing** (honest "subscription managed by store" stub or nothing)

## T. Screens that should remain mostly intact
Chat (minus model-chip prominence), Chats/Archive, Global Search, Find-in-chat, Library (minus fakes), Projects + detail, Assistants + create/edit (minus fake stats), Voice, Auth, Settings (after the two fixes), Translate, Create (minus fake studios), Explore (minus fabricated stats), drawer (minus sample recents), Onboarding (after persistence decision).

## U. Critical defects
1. **[BLOCKER] `ExploreView.swift:183` unresolved conflict marker** — hard Swift compile error; static gates PASS (they can't catch it); iOS is not verifiably buildable.
2. **[HIGH] Streaming full-parse fallback (O/P1)** — perf regression epicenter.
3. **[HIGH] Collapsible/tool-result expand state resets** via `remember(block)` under P1 (correctness).
4. **[MEDIUM] Hardcoded "GS Balanced" Settings row contradicts the real row** (both platforms).
5. **[MEDIUM] AssistantDetailView ignores stored instructions; hardcoded starters/capabilities for every assistant.**
6. **[MEDIUM] Onboarding persists nothing** while implying personalization.
7. **[MEDIUM] Billing "invoice download" writes fabricated documents into the real Library** (data pollution).
8. **[LOW] CRLF offset mismatch breaks incremental identity** (O/P7); image re-fetch without cache; speed-dots encode "Deep" as fewest dots; iOS Settings claims "GS LiveUpdate" (Android-only feature); `ContentFixtures` ships in release.

## V. Recommended new product hierarchy
1. **Conversation is the product.** Open → type. Home's input is the chat (or one tap from it), everything else supports that.
2. **One primary element per screen.** Chat: the transcript+composer. Home: the input.
3. **Model choice = quiet, name-only, sheet-based, skippable.** No modes/contexts/taxonomies in consumer surfaces; education is opt-in.
4. **Honesty = visible truth, not invisible fiction.** Real states (offline, failed, empty) kept and celebrated; fabricated estates removed or explicitly gated "coming soon."
5. **Dark and Light as one product** (already true — keep).
6. **Advanced concepts (registry, compare, reasoning) live under Settings → Advanced**, not in drawers or headers.
7. **Performance is a feature of the conversation.** The streaming path must be cheaper per flush than v0.60.0, not richer.

## W. Exact next-step recommendation (in order, nothing implemented until instructed)
1. **Fix iOS buildability** — resolve the `ExploreView.swift:183` conflict marker (5-minute fix, unblocks all iOS verification).
2. **Fix the streaming hot path** — real source offsets for list blocks + relaxed bail-out + tail inline cache + shape/theme hoisting + expand-state keyed on `sourceStart`; add the incrementality unit test. Then *measure*: on-device frame sampling during list-tail streaming, Macrobenchmark vs the `0975be3` APK, before/after numbers.
3. **Hierarchy pass** — model chip → name-only sheet control; Home recomposed conversation-first; drawer pruned (Models/Billing/Notifications out of primary nav); Settings de-duplicated.
4. **Honesty purge** — delete or gate every fabricated surface (Section R DELETE list), starting with Profile, Billing fiction, Research/Vision fake pipelines, and the fake IDE.
5. **Re-audit on device** against the original validation matrix (streaming feel, scroll, theme), then decide Step 6.

---

*Method: 4 parallel read-only forensic agents (timeline, Android inventory, iOS inventory, performance) + coordinator verification of every load-bearing claim (parser lines, conflict marker, hardcoded rows, token audit, completeness cross-check of all 32 Android / 34 iOS UI files). No code was modified. Full inventory detail lives in the worklog (Task IDs F-1…F-4) and this document.*
