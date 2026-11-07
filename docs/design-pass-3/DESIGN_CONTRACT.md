# GS AI — REDESIGN PASS 3 · DESIGN CONTRACT (binding)

This file is the single source of truth for redesign pass 3. Every agent implements
against it; deviations must be recorded in the report, not improvised silently.

Companion evidence lives beside this file (`docs/design-pass-3/`).

---

## 0. VERDICT ON THE CURRENT DESIGN (why it reads as a system design tool)

Verified against the shipped v0.73.0 screenshots (`docs/design-pass/after/`):

1. The monochrome went total: gray on gray, no hierarchy, no warmth, no focal point.
   The launcher icon carries a teal aurora orb; the UI carries nothing. The app lost
   its own identity color.
2. Settings is eight identical floating cards, each one row: icon + label + chevron.
   That is a Figma component sheet, not a product.
3. The empty chat canvas is empty. The first thing a new user meets is a developer
   dialog ("This device is MID class, so it gets Qwen2.5 0.5B Instruct (Q4_0)")
   with a duplicated title line.
4. Icons float bare; there is no container system, no density rhythm.
5. Decorative motion loops (background aurora, 1.4s skeleton pulses) exist next to a
   motion spec that bans them.
6. Unicode glyphs (✓ ✕ ↗ ▾) stand in for real icons in search-trace chrome.

Pass 3 fixes all six structurally. It is not an icon swap.

---

## 1. THE ONE ACCENT (the single explicit color change of this pass)

**Decision: the UI accent is reunified with the GS Aurora teal already carried by the
launcher icon (`#2DD4A8 → #0FA37E`, obsidian ground).** The interface stays monochrome;
teal is the only hue allowed to mean something:

- Teal = "the model is alive", "this is selected", "this is the primary move".
- ONE teal-filled or teal-tinted element per screen, at most. Never body text.
  Never decorative chrome. Never on backgrounds.
- Light theme: primary buttons stay ink (black fill, white label — paper-editorial
  calm). Teal appears as selection state, life-signs (orb, streaming caret, waveform,
  loading bar), and the armed send control. `accent = #0FA37E`, `accentStrong =
  #0B7D5F`, `onAccent = #0A0A0A` (ink on teal, 7.5:1).
- Dark theme: same rules on true black. `accent = #2DD4A8`, `accentStrong = #2DD4A8`,
  `onAccent = #052019` (11:1).
- The aurora life-sign gradient becomes white→teal (was white→gray, which was a
  life-sign that looked broken).
- Nothing else changes hue. Status colors (error/success/warning/info) and the code
  syntax palette are content states, not chrome, and stay as they are.

This is recorded ONCE here. No screen may invent a second accent.

---

## 2. FOUNDATIONS (tokens)

### Spacing — 4 / 8 / 12 / 16 / 24 / 32 only
- `GsSpacing.control` 14dp → **12dp** (the last off-scale value; GsButtons uses it).
- Card padding 16, section gaps 24, screen gutters 16. No 10/14/18/20/26 paddings
  in touched files. 18dp user-bubble corner radius and row-internal 14/12 paddings
  may stay where they are component-internal metrics, but any NEW spacing uses the scale.

### Icon containers — one rule
- Container: rounded square, **radius 12dp**, sizes 32 / 40 / 48dp. One size per list.
- Fill: neutral container (`secondaryBackground`/`containerLow`). Icon 20dp, ink.
- Per screen, exactly one container may be accent-filled (the primary action).
- New shared primitive `GsIconBadge` in GsComponents.kt; screens adopt it.

### Radii
sm 10→**8**, md 14→**12**, card 16, sheet 24, input 24 (was 26), pill 999, new
`iconBox = 12`.

### Type
- Weights: Normal / Medium / SemiBold only (already true).
- Serif stays ONLY as the brand voice at two moments: the splash/auth wordmark and
  the empty-chat greeting. Every other surface is sans. Dialog headings and screen
  titles are sans from now on.

### Elevation and shadows
- Border-led flat by default. Max two shadow levels per screen. Only the composer
  (when detached) and true overlays may cast the soft shadow. Scrims do the rest.

### Copy rules (Rule Zero enforcement)
- No em-dashes in product copy (current state is clean; keep it).
- No emoji, no decorative glyphs in chrome. `SearchEventCards.kt` replaces
  ✓ / ✕ / ↗ / – with GsIcons Check / X / ArrowUpRight / Minus.
- No marketing speak in empty states. Plain sentences, factual, warm, short.
- No device/quant jargon in user copy ("MID class", "Q4_0" leave the consent dialog).

---

## 3. MOTION — the exact table (single source: `GsEase` in ui/theme/Ease.kt)

| Element | Value |
|---|---|
| Message entrance | translateY 8dp + fade, 200ms, ease-out (`MESSAGE_SEND_MS` 150→200) |
| Message exit | fade, 150ms, ease-in (new `messageDisappear`) |
| Composer expand / collapse | 180ms, ease-in-out (`STATE_MS`) |
| Sheet / drawer open | 250ms, cubic-bezier(0.2, 0, 0, 1) (`SHEET_MS`, exists) |
| Sheet / drawer close | 200ms, ease-in (new `SHEET_CLOSE_MS` / `sheetClose()`) |
| Streaming chunk | fade per delta, 120ms (`STREAM_CHUNK_MS`, exists) |
| Press state | 120ms, ease-out, scale 0.97 (kineticPress: springs → tween 120) |
| Mode change | 180ms, ease-in-out (`STATE_MS`, exists) |
| Toast / snackbar | 200ms in, 150ms out (new `TOAST_IN_MS` / `TOAST_OUT_MS`) |
| Reduced motion | every value above becomes instant (gate exists; keep) |

- Delete the spring family (`standard/snappy/gentle`). Auth + Onboarding call sites
  move to `GsEase.state()`.
- Skeleton pulse 700ms → 400ms (≤800ms rule).
- Decorative background loops die: no `auroraBackground` on static containers. Aurora
  remains ONLY as an AI life-sign (orb, streaming caret bar, listening waveform,
  onboarding-ready one-shot sweep, model-alive blob) — foreground, functional.
- Detail-push nav transition (320ms) is not in the operator table; it stays, gated,
  documented as the one coexisting navigation tier. Section switches stay 220ms.

---

## 4. SCREEN SPECS (all 16; resources bracketed)

### 1. Splash / first launch
No splash composable exists (system launch window only). Action: first-launch
experience = empty chat + the consent dialog rebuild (spec 16 below covers dialogs;
consent rebuild is in spec 12/15). Note in report: "no splash screen; launch window
theme already matches; startup animation would violate the 600ms rule for zero value."

### 2. Auth gate — AuthScreen.kt
- Welcome: orb mark (BREATHING, 96dp) → "GS AI" serif display (the brand moment) →
  one plain line: "A quiet place to think, write, and ask." → three value rows
  rebuilt with `GsIconBadge` (Zap "One AI for everything", Lock "Your data stays
  yours", Gauge "Native speed") → ink primary "Create account", quiet "Sign in".
  Kill "Your intelligent command centre" (system-speak).
- Sign-in / sign-up / verify / 2FA / reset: fields 12dp radius, 56dp tall, no
  outlines-on-focus storms (border brightens, 180ms). Provider rows keep one icon
  each. Verify step: 6-box code becomes 6 quiet squares with teal underline on fill.
- [21st.dev pattern adopted: quiet system line for form errors instead of snackbar
  where inline.]

### 3. Onboarding — OnboardingScreen.kt
- Progress: 6 short segments (2dp tall, teal fill for done/current), not a bare bar.
- Interests slide: 12 chips → **[uiverse tiny-moose-42]** soft checkbox cards, 2-col
  grid: 22dp box (radius 7), teal fill + radial glow ripple + stroke-drawn check on
  select, label brightens. Multi-select stays.
- AI preferences slide: two preference card groups (Concise/Standard/Detailed,
  Professional/Friendly/Playful) as quiet selectable cards.
- Capabilities slide: **[MD3 carousel]** multi-browse contained carousel, 240dp item
  + peek, snap settle 250ms cubic(0.2,0,0,1) (documented deviation: MD3's per-
  component 500ms token is replaced by the app's single curve family; gesture feel
  unchanged). 6 capability cards, icon in 12dp badge, one line each.
- Notifications slide: 4 switch rows in one group card.
- Personalisation slide: name field + avatar tint choice recolored to the new
  identity (Aurora teal / Graphite / Ink) — labels updated, "Sky/Violet" die.
- Ready slide: teal one-shot sweep (1200ms, then static) + "You're all set[, name]."
- Continue control: **[uiverse clever-insect-61]** pill with triple-chevron cascade
  (monochrome ink chevrons, staggered fill cycle), disabled = 45% alpha.
- Hint: **[uiverse sour-dragon-56]** ONE tooltip, first slide: "Pick as many as you
  like. You can change these later." Pop + float + arrow mechanics, calm skin
  (surface + border + soft shadow; no texture).

### 4. Empty chat — ChatScreen.kt `WorkspaceEmptyState`
- Structure top→bottom: ThinkingOrb (BREATHING, 72dp) → greeting, serif, one line
  ("Good evening.") → sans sub-line "What are we working on?" → **[uiverse
  big-chipmunk-8]** ONE capability card (content, not decoration): masked hero zone
  (radial fade) containing the orb + a slow scanning beam (teal, the screen's single
  accent moment), then 4 rows: Telescope "Research the web", NotebookPen "Draft and
  write", Image "Read images", Code "Write and fix code" — each with 12dp badge.
  → two suggestion pills (staggered 200ms entrances).
- Composer floats above, detached, one soft shadow (the allowed elevation moment).

### 5. Active chat with streaming reply — ChatScreen.kt
- Entrance/exit per motion table; `messageAppear` retimed to 200ms; new
  `messageDisappear` for deletions/regenerate.
- Streaming: teal caret pulse (life-sign), orb INLINE row kept, stop control = ink
  circle with square glyph, 120ms press.
- Assistant identity row: "GS" 22dp monogram circle stays, label drops (noise).
- Timestamps move out of the always-visible action row: actions = Copy / Regenerate /
  Read aloud / Share, 28dp quiet icon buttons appearing after settle (150ms fade);
  timestamp shows on long-press menu only.

### 6. Rich reply — ui/chat/content/*
- Table: header row on `secondaryBackground`, hairline row dividers, numeric columns
  right-aligned, first column left — fixes the floating-number look.
- Code card: true ink-well (dark surface both themes), language label + copy button
  in header, 12dp radius; syntax palette unchanged (content state).
- Blockquote: 2dp teal-tinted bar, quieter fill — actually keep neutral 3dp bar; teal
  is reserved. Neutral it is.
- **[21st.dev chat-message]** adopted patterns: reasoning auto-collapses to "Done
  reasoning." style one-liner when settled (SearchTraceCard already does; keep and
  polish); tool/step rows state-styled monochrome (working = muted, done = check,
  failed = error), no green.
- **[21st.dev chat-reasoning]** SearchTraceCard body becomes the step timeline:
  8dp dots + 2px connectors, last connector fades out, chevron rotates 180°/200ms.

### 7. Chat with image attachment — AttachmentChipRow + AttachSheet
- Chip: 56dp thumb, 12dp radius, hairline border, X removes (28dp hit area on 44dp
  row), filename one line under.
- Attach sheet tiles rebuilt: icon in 48dp/12dp badge + label, pressed 120ms;
  unavailable tiles keep 45% alpha with plain "Not available yet" line.

### 8. Composer states — ChatScreen.kt `ComposerRow`
- **[21st.dev ai-prompt-box]** anatomy adopted: container = field + bottom toolbar
  row inside ONE surface (radius input 24): [Plus attach] [mode pills Flash /
  Thinking / Auto] [spacer] [Mic] [Send]. Attachments strip sits above the field
  inside the same container. Placeholder brightens on focus
  (**[uiverse dangerous-dolphin-47]** mechanic, NO gradient, NO glimmer dot).
- Mode pill: current mode label + ChevronDown; tap opens the mode sheet (spec 9).
  Selected pill = `accentSoft` fill + accentStrong text (the screen's selection
  accent; composer already uses teal only on the armed send — keep ONE: mode pill
  selected state uses ink fill + paper text instead. Teal stays on send.)
- Send button: **[uiverse dull-ladybug-18]** layered press — idle: raisedSurface +
  ink arrow; armed (text present): teal fill + ink arrow (the screen's accent);
  press: container scale 0.94 @120ms, arrow lifts 2dp with 60ms delay (staggered
  layering); streaming: becomes stop control.
- Focus expansion: **[uiverse cowardly-goat-90]** — on focus the container border
  brightens and padding expands 2dp (180ms ease-in-out); the send chip scales in
  0.25→1 on first character.
- The old inline mode chip next to the text field dies (state now always visible).

### 9. Mode / capability picker sheet — AttachSheet mode section
- Keep the sheet; drop its attach-tiles duplication? NO — the sheet KEEPS attach
  tiles (spec 7) and its Mode row becomes: 3 wide mode cards in a horizontal snap
  row, 148dp items (the current width), selected = ink container + paper text with
  scale-in check; each card is **[uiverse tricky-robin-67]** flipable (tap flips
  300ms): front = mode name + one-liner, back = what it changes ("Flash answers
  fast. Thinking takes longer and reasons step by step.").
- Carousel snap settle 250ms cubic(0.2,0,0,1); mode change applies at 180ms.

### 10. Drawer — GsDrawer.kt
- Top: search pill (input surface, 44dp) + ink "New chat" pill (primary).
- Recents: rows 52dp, title bodyLarge, star 14dp if pinned, long-press sheet stays.
- Library group: All chats / Archived / Voice — **each with a 40dp/12dp neutral
  icon badge** (Inbox / Archive / AudioLines). Archived joins the drawer (it was a
  hidden quick-link in Chats; it is real and reachable — consumer drawer shows it).
- Footer: account row (40dp circle, initials) + name/email + ChevronRight; the
  account sheet rows (Profile/Settings/Notifications/Billing) get 32dp badges.
- Open 250ms cubic(0.2,0,0,1); scrim tap closes 200ms ease-in.

### 11. Search history — ChatSearchScreen.kt (+ ChatsScreen search affordance)
- Field: input surface, radius 12, TextSearch trailing.
- Results: rows with 40dp badge (MessageCircle), snippet with match highlighted at
  `accentSoft` underline (selection accent), grouped headers quiet.
- Empty states: plain sentences ("No matches for that.").
- Dead twin `SearchScreen` (one-index search, zero callers) is DELETED; its
  recent-search-chip idea is noted in the report as intentionally not ported
  (dead code, not a design source).

### 12. Settings main — SettingsScreen.kt (the big rebuild)
- Eight identical expand cards DIE. New structure: grouped list, Dola shape.
- Each group: `GsSectionHeader` OUTSIDE (small caps, labelMedium) + one `GsGroupCard`
  containing 2-5 rows. Row: 40dp/12dp neutral icon badge + title (bodyLarge) +
  trailing value / switch / chevron, 56dp tall, hairline dividers inset from the
  icon, group radius 16, tap target full row.
- Groups: Appearance · Chat · AI · Privacy · Security · Notifications · Language ·
  Accessibility · About (About may render as inline value rows without push).
- Tap row/group → sub-screen (spec 13). One teal moment on this screen: none
  needed; switches when ON use accent (semantic, allowed as the selection accent).

### 13. Settings sub-screens
- Real pushed screens: `settings/{group}` route, detail-push transition, header =
  group name. Rows are the same components. Back folds correctly.
- AI sub-screen: "Prefer on-device AI" is the high-emphasis toggle —
  **[uiverse evil-shrimp-6]** glossy 3D checkbox rebuilt monochrome: frosted
  container (subtle inset highlights), check state = teal fill + scale 1.06 +
  stroke check pop (spring only here, the one sanctioned overshoot), NO blue.
  Consent dialog opens from it.
- Privacy sub-screen keeps destructive rows (error tone) and export flow.
- Accessibility: font-scale slider keeps staged commit; preview line live.

### 14. Error / failure states
- FailedTurnNotice: quiet system line (CircleAlert error icon 16dp + "Reply failed" +
  factual line + Retry text button), entrance 200ms, retry press 120ms.
- Offline banner: single-line system strip, WifiOff badge, 200/150ms.
- GsErrorState / empty states: icon in 48dp badge (neutral), title, one line, action.
- **[21st.dev system-message]** pattern adopted for all inline status lines.
- Send failure notice keeps honest copy; no new wording tone.

### 15. Model download / loading progress — OnDeviceModelConsentDialog.kt
- Consent dialog rebuild: icon (Cpu, teal-tinted badge — the screen's accent), sans
  SemiBold title "Use GS on this device?", body: "GS can also run on this device.
  The model downloads once, about 429 MB, then works offline. Chats stay on this
  device either way." + device line in plain words ("Recommended for this device.")
  + cellular SwitchRow + **[uiverse evil-shrimp-6]** "Remember my choice" glossy
  checkbox + ink Enable / text "Not now". Duplicated title line dies; jargon dies.
- Downloading phase: **[uiverse fast-vampirebat-53]** staggered blob loader rebuilt
  monochrome (three soft ink blobs falling into place, 1s cycle, 300ms offsets,
  ground shadows via layered alpha) + honest percent + size line + Cancel.
  LinearProgressIndicator replaced by a 3dp teal determinate bar (life-sign).
- DONE: check pop (STATE_MS) + "Ready".
- **[uiverse young-walrus-64]** metaball blob = the on-device model's alive
  presence in the models area IF a reachable models surface exists after cleanup
  (see deletions). If no reachable surface survives, the blob is recorded unused
  with that reason. Never two download loaders in one state.

### 16. Confirmation dialogs — GsOverlays.kt `GsConfirmDialog`
- AlertDialog: icon slot (badge), title sans SemiBold, body one paragraph,
  confirm ink (destructive = error text), dismiss text. Enter 200 / exit 150.
- Delete chat copy: "Delete this chat? This cannot be undone." / Delete / Keep.

---

## 5. RESOURCE MAP (every resource, every line — no silent skips)

| Resource | Where used | Why there |
|---|---|---|
| uiverse/Shammy2002/evil-shrimp-6 | Settings→AI toggle + consent dialog "Remember my choice" | The two high-emphasis yes/no moments; glossy checkbox earns attention exactly there |
| uiverse/ahmed_8975/tiny-moose-42 | Onboarding interests multi-select | Multi-select with glow + stroke check is the onboarding delight moment |
| uiverse/andrew-manzyk/fast-vampirebat-53 | First-launch model download progress | Staggered falling blobs = "pieces arriving"; matches a download |
| uiverse/vinodjangid07/dull-ladybug-18 | Composer send button | Layered press (squeeze + delayed arrow lift) = the most-pressed control |
| uiverse/marcelodolza/stupid-vampirebat-24 | Chats list empty state "New chat" primary | The app's one big invitation; rotating outline sweep runs on press only (no infinite loop) |
| uiverse/kheshore/clever-insect-61 | Onboarding continue | Forward-momentum chevrons on the most-forward control |
| uiverse/Cobp/dangerous-dolphin-47 | Composer container (shape + focus brighten; NO gradient/glimmer) | Its calm anatomy is right; its decoration is not |
| uiverse/byllzz/great-wombat-13 | UNUSED — no folder/workspace surface survives (static demo deleted); a 3D folder explosion has no honest home | Recorded per contract |
| uiverse/Ashon-G/big-chipmunk-8 | Empty chat capability card (ONE) | Shows what GS does; masked hero + beam, content-first |
| uiverse/ElSombrero2/tricky-robin-67 | Mode cards in the picker sheet (flip for explanation) | Modes need a one-tap "what does this change" without leaving the sheet |
| uiverse/andrew-manzyk/young-walrus-64 | On-device model alive blob (only if a reachable models surface survives cleanup; else unused-with-reason) | Liquid metaball = "the model is loaded and alive" |
| uiverse/Lakshay-art/sour-dragon-56 | ONE onboarding hint (slide 1) | Tooltip mechanics (pop/float/arrow) adopted, textured skin dropped |
| uiverse/Cobp/cowardly-goat-90 | Composer focus expansion + send chip scale-in | Expanding input with ambient controls is the composer's focus state |
| uiverse/Cobp/hot-dodo-99 | ThinkingOrb (existing orb upgraded: breathing pulse + tap blooms the trace panel) | The AI orb exists; the bloom pattern is its upgrade path |
| uiverse/Cobp/hot-dodo-99 (dup) | Confirmed byte-identical (md5 checked) | Used once, as instructed |
| 21st.dev jahed/ai-prompt-box | Composer anatomy (toolbar-in-container, placeholder brighten, attachment strip) | Best-in-class prompt anatomy |
| 21st.dev asanshay/chat-message | Message rendering roles; reasoning auto-collapse; tool state styling | Production chat anatomy |
| 21st.dev asanshay/chat-reasoning | SearchTraceCard step timeline (dots + fading connector + chevron) | Collapsible reasoning done right |
| 21st.dev ibelick/prompt-input-with-suggestions | Suggestion pill row pattern in empty state | Calm suggestions bar |
| 21st.dev ibelick/system-message | Inline quiet status lines (errors, offline) | Status without bubbles |
| 21st.dev ibelick/feedback-bar | Message action row pattern (quiet, post-settle) | Action chrome that waits its turn |
| MD3 carousel spec | Onboarding capabilities carousel + mode card snap row | The two true browse surfaces |
| assistant-ui comparison | Pattern matrix in final report (adopted / already present / rejected lines) | The contract's required check |

---

## 6. ICON SYSTEM

- Family: `GsIcons` (vendored Lucide, 24dp, stroke 2, round caps) — unchanged.
- Remaining Material icons die: ModelCentreScreen (3) + ModelCompareScreen (2) —
  moot, those files are deleted; verify zero `Icons.` usages repo-wide after waves.
- `SearchEventCards.kt` unicode glyphs → GsIcons.
- Icon grid artifact: generated from GsIcons path data into docs/design-pass-3/
  icon-grid.png (web render; CI emulator artifact listed as future evidence).
- Launcher stays the teal aurora orb (now the in-app accent too — one identity).

---

## 7. DELETIONS (each recorded with reason)

| Item | Reason |
|---|---|
| ui/models/ModelCentreScreen.kt | Orphaned (no route), unreviewed checkpoint artifact, references undefined `GsRoutes.MODEL_COMPARE` (breaks compile) |
| ui/models/ModelCompareScreen.kt | Orphaned, unreviewed checkpoint artifact |
| data/ModelPrefs.kt | Only consumer was the two orphaned screens |
| data/model/ModelCatalog.kt (data/model/) | Unreviewed duplicate of data/local/ModelCatalog.kt; only consumer was orphaned screens. ModelStore uses the local one. |
| ui/chat/ConversationFoldersScreen.kt | Static demo data, unrouted, drawer comments mark it for deletion |
| ui/chat/SharedChatsScreen.kt | Static demo, unrouted |
| ui/search/SearchScreen.kt + GsRoutes.SEARCH | Dead registration, zero callers, duplicates ChatSearchScreen |
| ui/research/ResearchScreen.kt + GsRoutes.RESEARCH | Dead honest-gate stub, zero callers |
| ui/vision/VisionScreen.kt + GsRoutes.VISION | Dead honest-gate stub, zero callers |
| Inline mode chip in composer | Superseded by always-visible mode pill |
| Timestamps in always-visible action row | Long-press detail now |

Deletion rule respected: anything deleted here is unrouted or duplicated; nothing
reachable dies. Re-verify with grep before each removal lands.

---

## 8. iOS MIRROR (SwiftUI)

- DesignSystem.swift: accent tokens (teal family), motion table (same numbers),
  icon-badge constants, spacing control 12.
- Screens restyled: AuthFlowView welcome, AeroDrawer, SettingsView groups,
  ChatDetailView composer anatomy + empty state card, AttachmentSheetView.
- iOS static gates (ios_static_gates.py, swift_structure_gate.py) must stay green;
  no new files beyond what gates allow.

---

## 9. EVIDENCE PLAN

- BEFORE: `docs/design-pass/after/*.png` (the shipped v0.73.0 emulator set, 12 shots)
  is the before set for every screen it covers; uncovered screens are noted.
- AFTER (this sandbox, no emulator):
  1. A design-review gallery is rendered at the web `/` route (device-framed
     recreations of every redesigned screen, built from this contract) and captured
     with agent-browser into `docs/design-pass-3/after/*.png`. These are design
     evidence, not device evidence; the report labels them as such.
  2. `GsScreenshotSuite.kt` is extended to cover the new states so the next CI
     emulator run (when push is restored) captures device-true afters.
- Push status this session: GitHub credential swept by the platform; all work is
  committed locally on `main` (see worklog). CI evidence runs require an operator
  token restore.

---

## 10. RULES REMINDERS (binding)

- Design on what exists. No features. No backend/engine/bench/release changes.
- Dola: match polish, not identity.
- Each resource used once (except the confirmed duplicate, used once).
- Commit per unit; the sandbox sweeps unpushed work — commits are local this
  session; worklog records it.
- No emoji in chrome, no em-dashes in copy, spacing on scale, three weights,
  monochrome + one teal moment per screen, 44dp targets, reduced motion everywhere.
