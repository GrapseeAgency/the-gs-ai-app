#!/usr/bin/env python3
"""Source-only overlay audit. No SDK, emulator, browser, network or CI required.

Scan every Kotlin line under the Android UI root and every Swift line under
the iOS source root. Raw platform presentation calls are explicitly reviewed
exceptions, not permission for arbitrary new calls in the same file.
This is a lexical guard, not a compiler or a guarantee about runtime pixels.
"""
from collections import Counter
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android/app/src/main/java/com/grapsee/gsai/ui"
IOS = ROOT / "ios/App/Sources"

PATTERN = re.compile(
    r"\b(?:Dialog|Popup|ModalBottomSheet|DropdownMenu|AlertDialog|"
    r"FloatingActionButton|ExtendedFloatingActionButton|SnackbarHost|Snackbar)\s*\("
    r"|\.(?:sheet|popover|alert|contextMenu|fullScreenCover|confirmationDialog)\s*[\({]"
    r"|\bMenu\s*\{|metalEdge\s*[:=]\s*false"
    r"|\.gsGlass\(\.modal|gsGlass\(GsGlassRole.Modal"
    r"|\.gsGlass\(\.elevated|gsGlass\(GsGlassRole.Elevated"
)

# Android: shared presentation hosts, media viewers (their content must not
# be painted with a full-screen metal fill), decorative brand and dock frame.
A = {
    "attachment/MediaPreview.kt": ["Dialog(onDismissRequest = onDismiss) {"] * 2,
    "auth/AuthScreen.kt": [".gsGlass(GsGlassRole.Elevated, RoundedCornerShape(28.dp)),"],
    "chat/ChatScreen.kt": [
        ".gsGlass(GsGlassRole.Elevated, CircleShape)",
        ".gsGlass(GsGlassRole.Elevated, CircleShape, metalEdge = true)",
    ],
    "chat/content/MermaidDiagram.kt": ["Dialog("],
    "chat/content/RichBlocks.kt": ["androidx.compose.ui.window.Dialog("],
    "components/GlassMaterial.kt": ["gsGlass(GsGlassRole.Modal, shape, metalEdge = true)"],
    "components/GsDialogs.kt": ["Dialog("],
    "components/GsOverlay.kt": ["Dialog("] * 2,
    "components/GsOverlays.kt": [
        "DropdownMenu(", "ModalBottomSheet(",
        "SnackbarHost(hostState = hostState, modifier = modifier) { data ->",
        "Snackbar(",
    ],
    "explore/ExploreScreen.kt": [".gsGlass(GsGlassRole.FloatingControl, CircleShape, metalEdge = false)"],
    "home/HomeDashboardScreen.kt": [".gsGlass(GsGlassRole.FloatingControl, GsRadius.pill, metalEdge = false)"],
}

# iOS: shared host fallback, native OS-owned share/camera/Safari, full-screen
# diagram viewer, actual presented sheets with shared glass, and brand/dock.
I = {
    "Components/GsPopover.swift": [".popover(isPresented: $fallbackVisible) {"],
    "Components/InAppBrowser.swift": [".sheet(item: $presenter.presenting) { box in"],
    "Components/PlatformRuntimeProbe.swift": [
        ".sheet(isPresented: $show" + name + ") {"
        for name in ("Timer", "Booking", "Dashboard", "Checklist", "Calculator",
                     "Terminal", "Table", "Scatter", "Chart", "Code")
    ],
    "Features/Chats/AttachmentSheetView.swift": [".fullScreenCover(isPresented: $showCamera) {"],
    "Features/Chats/ChatDetailView.swift": [
        ".sheet(isPresented: $showInfo) {",
        ".sheet(item: $translationCard) { card in",
    ],
    "Features/Chats/ChatSearchView.swift": [
        ".sheet(isPresented: $showFacets) {", ".sheet(isPresented: $showExport) {",
    ],
    "Features/Chats/Content/MermaidDiagram.swift": [".fullScreenCover(isPresented: $viewerOpen) {"],
    "Features/Chats/Content/RichBlocks.swift": [
        ".sheet(item: $presentedSheet, onDismiss: { cleanPreparedExport() }) { sheet in",
    ],
    "Features/Chats/NexlyChatShell.swift": [
        ".gsGlass(.elevated, corner: 22, metalEdge: true)",
        ".gsGlass(.elevated, corner: 22)",
        ".gsGlass(.elevated, corner: 22, metalEdge: true)",
    ],
    "Features/Explore/ExploreDock.swift": [".gsCapsuleGlass(.floatingControl, metalEdge: false)"],
    "Features/Home/HomeDashboardView.swift": [".gsCapsuleGlass(.floatingControl, metalEdge: false)"],
    "Features/Integrations/ConnectedAppsView.swift": [".sheet(item: $sheet) { which in"],
    "Features/Settings/AiIntelligenceView.swift": [".sheet(isPresented: $showModelDetails) {"],
    "Features/Settings/DataExportAccountDeletionView.swift": [".sheet(isPresented: $showExport) {"],
    "Features/Settings/ProfilePreferencesView.swift": [
        ".sheet(isPresented: $showIdentityEditor) { IdentityEditSheet() }",
    ],
    "Features/Support/HelpSupportView.swift": [
        ".sheet(item: $library) { category in", ".sheet(isPresented: $showAllTopics) {",
    ],
}

# Positive route checks complement the raw-call guard. The named surfaces
# must retain their shared renderer references, even when no stock API is used.
REQUIRED = {
    ANDROID / "components/GlassMaterial.kt": [
        "gsGlass(GsGlassRole.Modal, shape, metalEdge = true)", ".argentComponentRim(",
    ],
    ANDROID / "components/GsOverlay.kt": [".gsOverlayGlass(", "fun GsAnchoredPopover("],
    ANDROID / "components/GsOverlays.kt": ["modifier = Modifier.gsOverlayGlass(shape)", "fun GsSnackbarHost("],
    ANDROID / "components/GsDialogs.kt": [".gsOverlayGlass(DialogShape)", ".argentComponentBorder("],
    ANDROID / "chat/ChatScreen.kt": ["private fun JumpToLatestPill", ".gsGlass(GsGlassRole.FloatingControl, CircleShape)"],
    ANDROID / "attachment/MediaPreview.kt": [".gsOverlayGlass(", ".gsGlass(GsGlassRole.FloatingControl"],
    IOS / "Theme/GlassMaterial.swift": ["gsGlass(.modal, corner: corner, metalEdge: true)", ".gsArgentComponentRim(", "drawsRim: false", "drawsBody: false"],
    IOS / "Components/GsPopover.swift": [".gsOverlayGlass(corner: 22)", "func gsReflectiveTapMenu"],
    IOS / "Components/GsDialog.swift": [".gsOverlayGlass(corner: 28)", ".gsArgentComponentBorder("],
    IOS / "Components/AeroComponents.swift": [".gsOverlayGlass(corner: Aero.Radius.sheet)"],
    IOS / "Features/Chats/ChatDetailView.swift": ["private func jumpToLive", ".gsGlass(.floatingControl, corner: 18)", ".gsPresentedSheetGlass()"],
    IOS / "Features/Chats/NexlyChatShell.swift": [".gsReflectiveContextMenu(button: true)", ".gsReflectiveTapMenu"],
    IOS / "Features/Chats/ChatSearchView.swift": [".gsPresentedSheetGlass()", ".gsReflectiveTapMenu"],
    IOS / "Features/Support/HelpSupportView.swift": [".gsPresentedSheetGlass()"],
    IOS / "Features/Integrations/ConnectedAppsView.swift": [".gsPresentedSheetGlass()"],
    IOS / "Features/Settings/AiIntelligenceView.swift": [".gsPresentedSheetGlass()"],
    IOS / "Features/Settings/ProfilePreferencesView.swift": [".gsPresentedSheetGlass()"],
    IOS / "Features/Chats/Content/RichBlocks.swift": [".gsPresentedSheetGlass()"],
    ROOT / "src/app/page.tsx": ["gs-reflective-overlay", "gs-reflective-control"],
    ROOT / "src/components/settings/SettingsExperience.tsx": ["gs-toast gs-reflective-overlay", "gs-modal gs-reflective-overlay"],
    ROOT / "src/components/ui/dialog.tsx": ["gs-reflective-overlay", "gs-reflective-control"],
    ROOT / "src/components/ui/sheet.tsx": ["gs-reflective-overlay", "gs-reflective-control"],
}

def audit():
    errors = []
    files = lines = 0
    for root, extension, allowed in ((ANDROID, "*.kt", A), (IOS, "*.swift", I)):
        for path in sorted(root.rglob(extension)):
            files += 1
            rel = path.relative_to(root).as_posix()
            budget = Counter(allowed.get(rel, ()))
            for number, raw in enumerate(path.read_text(encoding="utf-8").split("\n"), 1):
                lines += 1
                line = raw.strip()
                if line.startswith("//") or not PATTERN.search(line):
                    continue
                if budget[line]:
                    budget[line] -= 1
                else:
                    errors.append(f"{path.relative_to(ROOT)}:{number}: unreviewed presentation or rim opt-out: {line}")
    for path, tokens in REQUIRED.items():
        if not path.is_file():
            errors.append(f"{path.relative_to(ROOT)}: required source missing")
            continue
        source = path.read_text(encoding="utf-8")
        for token in tokens:
            if token not in source:
                errors.append(f"{path.relative_to(ROOT)}: shared route missing: {token}")
    if not files:
        errors.append("No native source files found")
    print(f"Scanned {files} native source files, {lines} lines")
    for error in errors:
        print(error, file=sys.stderr)
    print("PASS source overlay routing" if not errors else f"FAIL: {len(errors)} source routing findings")
    return bool(errors)

if __name__ == "__main__":
    sys.exit(audit())
