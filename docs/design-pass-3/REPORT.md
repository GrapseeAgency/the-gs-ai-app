# GS AI — REDESIGN PASS 3 · FINAL REPORT

Scope: full structural redesign of the shipped v0.73.0 interface (Android Compose +
iOS SwiftUI mirror), executed against `DESIGN_CONTRACT.md` in this folder.
Session constraint recorded up front: the GitHub credential was swept by the
platform, so every commit is LOCAL on `main` (verified `git push` fails with
"could not read Password"). An operator token restore lets one `git push` ship
all 22 commits and re-arms the CI emulator evidence run.

---

## 1. COMMITS (one per unit of work)

| Commit | Unit |
|---|---|
| d35901f | Foundation: the one accent (GS Aurora teal), exact motion table, tokens (control 12, radii 8/12/24, iconBox 12), primitives (GsIconBadge/GsGroupCard/GsGroupDivider/GsSystemLine), quiet-teal chip selection, ink primaries, springs deleted, deletions, material-icons dependency removed |
| fed75dc | Screen 10 drawer: badge-led Library group + Archived row |
| 3776d44 | Screen 6 content: code ink-well, tokened table, neutral quote, caret on streaming teal, trace timeline, glyph sweep |
| 3c15b1f | Screens 4-9 chat: composer anatomy, empty state, attach sheet, flip cards, failed notice |
| 6eadc92 | 3-a fix: flip spec compiles |
| e724b40 | Screens 12/13 settings verify + SettingsStore.rememberModelChoice |
| 98c36b9 | Chats list: badges + press-sweep New chat invitation |
| 09115c7 | Screen 15 consent rebuild (jargon out, remember-my-choice) |
| 791191f | Screen 2 auth welcome rebuild |
| 4e28d5f | Screen 11 search history + archived |
| af305c8 + 1fe09e0 | Screen 3 onboarding: segments, glow checkboxes, MD3 carousel, group card, swatches, continue pill, tooltip |
| a612562 | Shared polish: empty states on badges, confirm dialog icon slot, voice mic accent, profile badges |
| 936830e / e52ddc9 / d5592ec / bd3c4ca / d19bd7c / b2e28f9 | iOS mirror: DesignSystem tokens+motion, IconBadge primitives, auth, drawer, settings, composer+empty |
| b05655b | Design-review gallery at `/` (18 frames + icon grid + motion table) |
| 7668533 | After-evidence capture + screenshot suite extension (31, 60) |

Every commit compiled at its gate (`:app:compileDebugKotlin`; final run also
`:app:compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL). HEAD now compiles for
the first time since the platform checkpoint swallowed stray files.

## 2. SCREENS (before → after)

Before set = the shipped v0.73.0 CI emulator shots (`docs/design-pass/before|after/*.png`).
After set = `docs/design-pass-3/after/frame-*.png`, captured from the design-review
gallery rendered at the app's `/` route (labeled as design evidence; device-true
afters require the CI emulator run once push returns — the suite is extended and ready).

| # | Screen | Before | After | What changed, why | Resource |
|---|---|---|---|---|---|
| 1 | Splash | after/10-splash | frame-10 | No splash composable exists; launch window theme already matches; a startup animation would violate the 600ms rule for zero value | none (recorded skip) |
| 2 | Auth gate | after/11-auth-gate | frame-11 | Serif brand moment + plain line replaces "command centre" speak; value rows on badges; ink CTAs | 21st.dev prompt-input patterns (quiet chrome) |
| 3 | Onboarding | after/12-onboarding | frame-12a/b/c | Segments replace bar; interests as glow checkbox cards; capabilities as MD3 contained carousel; grouped notifications; Aurora/Graphite/Ink swatches; chevron-cascade continue; ONE hint tooltip | tiny-moose-42 · MD3 carousel · clever-insect-61 · sour-dragon-56 |
| 4 | Empty chat | after/20-empty-chat | frame-20 | Canvas gains identity: orb + serif greeting + ONE capability card (masked hero, scan beam) + 2 pills + floating composer; first-run jargon dialog replaced (see 15) | big-chipmunk-8 |
| 5 | Streaming chat | after/40 | frame-40 | Entrance 200ms; caret = teal life-sign; stop control; mode pill visible; actions quiet | 21st.dev chat-message |
| 6 | Rich reply | after/41-rendered | frame-41 | Ink-well code card; tokened table with right-aligned numerals; neutral quote; trace as dot timeline with fading connector | 21st.dev chat-reasoning |
| 7 | Image attach | after/22-attach-sheet | frame-22 | Badged tiles in-container; dimmed "Not available yet" kept; chip thumbs 12dp | cowardly-goat-90 (mechanics) |
| 8 | Composer states | after/20/40/22 | frame-20/40/21/22 | One container: attachments strip + field + toolbar; focus brightens + expands; send: idle raised → armed teal, layered press | ai-prompt-box · dangerous-dolphin-47 (no gradient) · dull-ladybug-18 · cowardly-goat-90 |
| 9 | Mode picker sheet | after/21 | frame-21 | 148dp mode cards, flip for what-it-changes; snap settle on the app curve | tricky-robin-67 · MD3 timing (documented: app curve family) |
| 10 | Drawer | after/20b-drawer | frame-20b | Library group on 40dp badges; Archived surfaced; 52dp rows; calm footer | 21st.dev ibelick patterns |
| 11 | Search history | (none in before set) | frame-11b | Badges, accentSoft match spans, plain empties | none |
| 12 | Settings | after/30-settings | frame-30 | THE rebuild: eight identical cards → grouped sections (header outside, one card per group, inset dividers, values visible) | evil-shrimp-6 (AI toggle) |
| 13 | Settings sub | (none) | frame-31 | AI group detail with glossy check + download row | evil-shrimp-6 |
| 14 | Error states | (none) | frame-14 | Quiet system lines (failed turn, offline) replace banner chrome | 21st.dev system-message |
| 15 | Model download | after/20 (dialog) | frame-15a/15b | Consent in plain words + remember-choice glossy check; downloading = staggered dots + 3px teal bar + honest sizes | evil-shrimp-6 · fast-vampirebat-53 |
| 16 | Confirm dialogs | (none) | frame-16 | Icon slot, sans title, ink confirm / red destructive, plain copy ("Delete this chat? This cannot be undone.") | 21st.dev feedback-bar |

## 3. COMPONENT USAGE (no silent skips)

| Resource | Where | Why there |
|---|---|---|
| uiverse evil-shrimp-6 | Settings AI toggle + consent remember-choice | The two high-emphasis yes/no moments |
| uiverse tiny-moose-42 | Onboarding interests | Multi-select delight with glow + drawn check |
| uiverse fast-vampirebat-53 | Model download progress | Falling pieces = arrival; matches a download |
| uiverse dull-ladybug-18 | Composer send | Layered press on the most-pressed control |
| uiverse stupid-vampirebat-24 | Chats empty "New chat" | The one big invitation; sweep on press only |
| uiverse clever-insect-61 | Onboarding continue | Forward-momentum chevrons |
| uiverse dangerous-dolphin-47 | Composer container anatomy (shape/focus ONLY; gradient + glimmer dropped) | Calm anatomy right, decoration wrong |
| uiverse great-wombat-13 | UNUSED — recorded: no folder/workspace surface survives (the folders demo is deleted); a 3D folder explosion has no honest home | Contract §5 |
| uiverse big-chipmunk-8 | Empty-chat capability card (ONE) | Shows what GS does; masked hero + beam |
| uiverse tricky-robin-67 | Mode cards flip | One-tap "what does this change" |
| uiverse young-walrus-64 | UNUSED — recorded: its target (a reachable models surface) died with the orphaned model screens deletion; two download loaders in one state is banned | Contract §5 |
| uiverse sour-dragon-56 | ONE onboarding hint (pop/arrow mechanics; bark texture dropped) | Tooltip where a first-timer hesitates |
| uiverse cowardly-goat-90 | Composer focus expansion + send scale-in | Expanding input with ambient controls |
| uiverse hot-dodo-99 | The existing ThinkingOrb (pulse + bloom interaction path; duplicate reference byte-verified identical, used once) | The AI orb exists; bloom is its upgrade |
| 21st.dev ai-prompt-box | Composer anatomy | Best prompt anatomy available |
| 21st.dev chat-message | Message roles / reasoning collapse / tool states | Production chat anatomy |
| 21st.dev chat-reasoning | SearchTraceCard timeline | Collapsible reasoning done right |
| 21st.dev prompt-input-with-suggestions | Empty-state suggestion pills | Calm suggestions row |
| 21st.dev system-message | Quiet inline status lines | Status without bubbles |
| 21st.dev feedback-bar | Message action row | Chrome that waits its turn |
| MD3 carousel | Onboarding capabilities + mode card snap | The two true browse surfaces |
| assistant-ui comparison | Adopted: reasoning auto-collapse, tool state styling, message actions post-settle. Already present: markdown blocks, citations, branching, attachments, streaming stop, read-aloud, translate. Rejected: token-cost displays (system-tool signal), multi-model switcher UI (models surface deleted as orphaned) | Contract §5 |

## 4. ICON SYSTEM

- Family: `GsIcons` — vendored Lucide geometry, 24dp, stroke 2, round caps; one family.
- Material icons remaining in the app: **0** (verified by grep; the last 5 lived in the
  deleted orphaned model screens) and the `material-icons-extended` dependency is removed.
- 99 distinct GsIcons now in use; decorative glyphs (✓ ✕ ↗ –) replaced with vectors.
- Containers: one rule — rounded square, radius 12, 32/40/48dp, neutral fill,
  max ONE accent-filled container per screen (implemented as `GsIconBadge`).
- Verification artifact: `docs/design-pass-3/after/icon-grid.png` (48-icon grid).

## 5. MOTION (exact table, one source: `GsEase`)

| Element | Value |
|---|---|
| Message entrance | translateY 8dp + fade, 200ms, ease-out |
| Message exit | fade, 150ms, ease-in |
| Composer expand/collapse | 180ms, ease-in-out |
| Sheet/drawer open | 250ms, cubic-bezier(0.2, 0, 0, 1) |
| Sheet/drawer close | 200ms, ease-in |
| Streaming chunk | 120ms fade |
| Press | 120ms, ease-out, scale 0.97 |
| Mode change | 180ms, ease-in-out |
| Toast | 200ms in / 150ms out |
| Reduced motion | every value becomes instant (verified gates: GsEase.ms(), kineticPress, messageAppear, orb, skeleton) |

Springs deleted; skeleton pulse 700→400ms; decorative background loops banned
(aurora survives only as the AI life-sign); the single sanctioned overshoot is the
glossy-check pop (350ms, cubic-bezier(.34,1.56,.64,1)).

## 6. DELETIONS (each recorded)

Orphaned model screens (`ui/models/*` + `data/ModelPrefs.kt` + `data/model/` — one
referenced a route constant that does not exist: HEAD did not compile), dead
search/research/vision routes + screens, static demo screens (folders/shared),
inline mode chip, always-visible timestamps, `material-icons-extended`,
spring family, decorative aurora loops. Kept after review: Mermaid "journey"
keyword (protocol discriminator, not copy). Considered and NOT deleted: VoiceScreen
(reachable, real), Billing/Notifications (honest one-card surfaces).

## 7. WHAT CHANGED, PLAIN LANGUAGE

The app had one color and no identity: gray cards floating on gray, eight identical
settings tiles, an empty opening screen, and developer jargon in the first dialog.
Now one teal — the launcher orb's own color — marks the moments where the assistant
is alive or an action is primary, and everything else stays ink on paper. Settings
read as grouped lists with visible values. The chat opens with a greeting, a card
that shows what the assistant can do, and a composer that reacts to focus and text.
Icons sit in consistent containers. Motion follows one table everywhere and stops
when the system asks it to.

## 8. KNOWN LIMITS (honest)

1. Push is dead this session (platform-swept token) — 22 local commits await one
   `git push origin main` after a token restore; CI emulator evidence runs resume then.
2. After-screenshots are design-review renders from the `/` gallery, not device
   frames; the extended CI suite produces the device-true set next run.
3. iOS changes mirror tokens/components/screens but were not compiled (no Xcode in
   sandbox); the mode flip-card interaction was deliberately NOT mirrored (segmented
   picker + per-mode hint line kept; recorded skip).
4. young-walrus-64 and great-wombat-13 are recorded unused with reasons.
