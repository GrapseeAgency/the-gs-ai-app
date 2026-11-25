# Reflective transient UI — source audit

Scope: app-owned native Android/iOS transient surfaces and the corresponding web controls. Existing permanent page backgrounds, content cards, the brand mark and intentionally rim-free dock containers are not redesigned.

| Entry point | Android | iOS | Web |
|---|---|---|---|
| Chat header options, plus / attachments | GsAnchoredPopover, GsPopoverRow | GsAnchoredPopover, AttachmentSheetView | Composer options |
| Scroll to latest | JumpToLatestPill | ChatDetailView.jumpToLive | No pre-existing floating control |
| Message actions, agent, response mode | GsDropdownMenu | GsReflectiveMenuHost, NexlyComposerBar | Shared dropdown/context primitives |
| History, archived chats, drawer actions and filters | ConversationActionsSheet, DrawerNavSheet, FacetSheet, drawer surface | AeroDrawer, ChatsListView, ArchivedChatsView, ChatSearchView | Settings history popover |
| Prompt, confirm, choices and facts | GsModalFrame | GsDialogFrame | Dialog/alert-dialog primitives |
| Translation, chat info, model info, integration, identity, support | GsModalSheet or existing shared dialogs | gsPresentedSheetGlass | Shared sheet/drawer primitives |
| Attachment media, report/source details, chart and widget selections, diagram close | MediaPreview, RichBlocks, GsDropdownMenu, MermaidViewerDialog | RichBlocks, WidgetBlocks, MermaidViewerView | Shared select, hover-card, navigation-menu, popover |
| Voice calibration reset and floating controls | GsConfirmDialog, shared glass and native action fill | GsDialog, shared glass and native action fill | N/A |

Native material: existing Argent field and theme, opt-in larger bounds for transient panels only. High contrast falls back to the existing accessible border; no continuous per-panel animation. iOS uses app-owned anchored menus for the formerly stock menu actions. Native share, photo/camera/document picker, text selection, swipe actions, Safari and full-screen media content remain OS-owned; their app-owned close controls and surrounding surfaces use the shared material. Web uses the installed ArgentUI native shader as a masked static raster over CSS backdrop glass; a neutral border remains if WebGL is unavailable or contrast is increased.

Validation: sizing unit tests, actual iOS menu UI harness, actual web browser rim/scroll/resize/dismissal test. These are not physical-device appearance measurements. Existing unrelated streaming and historical reference-copy gates are not treated as proof of overlay appearance. The iOS project pins the last compatible public MIT source revision of Libraries.dev because the moving upstream branch removed VoicePositionKit on November 24, 2026. No new model asset was downloaded.

The iOS build also exposed an existing use of nonexistent GraphicsContext gradient extension flags in FluidResponseText. The compile correction retains the original cached gradient and explicitly paints only endpoint overhangs; it does not change the streaming motion curve, timing, or transport.

## Source-only follow-up review

Reviewed the native chat, history, attachment, drawer, settings, home, explore, library, integration, account, voice, and generated-content entry points for direct presentation calls and explicit rim opt-outs, then traced shared overlay modifiers into the native shader. The voice calibration reset on iOS was still stock system chrome; it now uses GsDialog. Voice controls on both platforms are now reflective without changing microphone, calibration or navigation actions. Explicit transient rims cover tablet and landscape dimensions up to 2048 logical units on either axis; ordinary cards retain the original 720×320 limit, with intentional dock/brand exceptions unchanged. Android anchored menus now clamp their insets and panel size on constrained viewports. System-owned pickers/share UI and diagnostic probes are not production overlay chrome. This is a source review, not a claim of device-rendered acceptance.

## Additional source review — overlay controls

The overlay shells were correctly routed but their internal controls still had independent paint paths. Reviewed the confirm/prompt/fact/choice dialogs, media preview and report dismissals, integration connect/close, model-details Done, and help/report navigation toolbar close buttons. Replaced fixed white outlines with the existing Argent border renderer, preserving accent/destructive fills, and routed neutral dismiss pills through the shared floating-control glass. The unused legacy Android menu shell now delegates to the overlay glass rather than stock Surface paint. Bare text actions and selected-row accents remain intentionally unplated. This follow-up is source-only; no device visual or CI result is claimed.

## Custom transient controls and notices

The follow-up source pass covered non-modal implementations as well as platform presentation calls. Attachment removal/retry/open discs, archived chat action discs, the expandable source selector, billing close and chat confirmation notices now share the native Argent glass paths. Android auth/chat snackbar hosts keep Material lifecycle and action handling with the shared reflective surface; the offline banner uses the same surface. Inline transcript content, media artwork, status badges and system-owned previews are not converted into overlay chrome.

## Custom settings dialogs and sheet layering

The source pass found independent paint on model-consent, Quiet Hours, memory-list, integration connect, and translation controls. Those now use the shared native Argent paths, retaining existing action state, disabled behavior, and accent fills. The iOS AccountSheetView is an in-app floating panel, not a system sheet: its AeroSheetShell now uses content-sized overlay glass rather than the full-height presented-sheet modifier. Native iOS sheets now put their glass body in the actual presentation background and draw a separate noninteractive Argent perimeter above the content, so scrolling/navigation content cannot obscure the rim. The same shader, fallback and size policy are used, with no double rim or second metal body.

## Exhaustive native lexical scan

At native source revision `4df7252268188b02a9757e00691fc2cb915b8c67`, every file under Android's UI Kotlin root and iOS's Swift source root was fetched and scanned line by line: **276 files, 123,020 newline-delimited entries**, with no unreadable files after retrying HomeDispatch.swift. The scan looked for direct presentation calls, stock menu/dialog/snackbar/floating APIs and explicit modal/elevated rim bypasses. Every match was reviewed against its owning surface. Exceptions are the shared host implementations, OS-owned presentation (camera/share/Safari), diagnostic probe, full-screen media content whose close controls are reflective, and the deliberately rim-free decorative brand/dock containers. This is exhaustive lexical coverage of those roots, not a claim of manually reading every unrelated line or proof of runtime behavior. The targeted web follow-up removed flat paint from the composer attachment/mode controls, custom response-report and status panels, settings toast, and shared dialog/sheet dismiss discs. `python3 scripts/check-reflective-overlay-source.py` provides a repeatable source-only guard; no SDK, emulator or CI is needed. The guard is based on the completed connector scan, and should be run on a local checkout when one is available.
