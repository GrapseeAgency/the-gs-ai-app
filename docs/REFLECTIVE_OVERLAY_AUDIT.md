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
| Voice calibration reset | GsConfirmDialog | Existing shared confirmation | N/A |

Native material: existing Argent field and theme, opt-in larger bounds for transient panels only. High contrast falls back to the existing accessible border; no continuous per-panel animation. iOS uses app-owned anchored menus for the formerly stock menu actions. Native share, photo/camera/document picker, text selection, swipe actions, Safari and full-screen media content remain OS-owned; their app-owned close controls and surrounding surfaces use the shared material. Web uses the installed ArgentUI native shader as a masked static raster over CSS backdrop glass; a neutral border remains if WebGL is unavailable or contrast is increased.

Validation: sizing unit tests, actual iOS menu UI harness, actual web browser rim/scroll/resize/dismissal test. These are not physical-device appearance measurements. Existing unrelated streaming and historical reference-copy gates are not treated as proof of overlay appearance. The iOS project pins the last compatible public MIT source revision of Libraries.dev because the moving upstream branch removed VoicePositionKit on November 24, 2026. No new model asset was downloaded.
