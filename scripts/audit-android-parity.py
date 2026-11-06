#!/usr/bin/env python3
"""
ANDROID PARITY AUDIT — the Kotlin twin must accept every type catalog.ts declares.

Walks src/lib/widgets/catalog.ts and verifies, against the Android sources:

  1. every sub-type list (charts, media, calc, apps, sims, stage3d, canvas,
     editors, collab) exists VERBATIM in the Kotlin type sets;
  2. every form-field type has a render branch in the Kotlin form when-chain;
  3. every v3 kind dispatches (sim/stage3d/canvas/editor/collab view functions
     branch on every declared sub-type, with a documented default);
  4. every head in KNOWN_MERMAID_HEADS is a family the BUNDLED
     assets/mermaid.min.js actually registers (a gated head the engine cannot
     draw renders a parse-error graphic — this caught packet-beta /
     architecture-beta / kanban), and reports bundle families still ungated.

Exit code 1 on any mismatch. Run: python3 scripts/audit-android-parity.py
Complements scripts/test-widget-catalogue.ts (the web half, runs in CI).
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
K = ROOT / "android/app/src/main/java/com/grapsee/gsai/ui/chat/content"
CATALOG = ROOT / "src/lib/widgets/catalog.ts"

failures: list[str] = []
notes: list[str] = []


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


catalog = read(CATALOG)
kt_all = "".join(read(f) for f in K.glob("*.kt"))


def ts_list(name: str) -> list[str]:
    m = re.search(rf"export const {name} = \[(.*?)\] as const", catalog, re.S)
    if not m:
        failures.append(f"catalog.ts: list {name} not found")
        return []
    return re.findall(r"'([^']+)'", m.group(1))


def kt_set(name: str, src: str) -> list[str]:
    for prefix in (f"internal val {name} = setOf(", f"val {name} = setOf("):
        i = src.find(prefix)
        if i >= 0:
            break
    else:
        failures.append(f"Android: type set {name} not found")
        return []
    j = src.index("(", i)
    depth = 0
    for k in range(j, len(src)):
        if src[k] == "(":
            depth += 1
        elif src[k] == ")":
            depth -= 1
            if depth == 0:
                return re.findall(r'"([^"]+)"', src[j + 1 : k])
    return []


# --- 0. top-level widget kinds (WIDGET_KINDS == WIDGET_LANGUAGES) -----------
web_kinds = ts_list("WIDGET_KINDS")
android_kinds = kt_set("WIDGET_LANGUAGES", kt_all)
missing_kinds = [k for k in web_kinds if k not in android_kinds]
extra_kinds = [k for k in android_kinds if k not in web_kinds]
if missing_kinds:
    failures.append(f"WIDGET_LANGUAGES: Android missing {missing_kinds}")
if extra_kinds:
    failures.append(f"WIDGET_LANGUAGES: Android has extras {extra_kinds}")
wb_src = read(K / "WidgetBlocks.kt")
unparsed_kinds = [k for k in web_kinds if k != "plan" and f'"{k}" ->' not in wb_src]
if unparsed_kinds:
    failures.append(f"parseWidget: Android missing decoder branch for {unparsed_kinds}")
if not missing_kinds and not extra_kinds and not unparsed_kinds:
    notes.append(f"WIDGET_LANGUAGES + parseWidget: {len(web_kinds)}/{len(web_kinds)} top-level kinds match")

# --- 1. sub-type lists, exact match ------------------------------------------
LISTS = [
    ("CHART_TYPES", "CHART_TYPES"),
    ("MEDIA_TYPES", "MEDIA_TYPES"),
    ("CALC_TYPES", "CALC_TYPES"),
    ("APP_TYPES", "APP_TYPES"),
    ("SIM_TYPES", "SIM_TYPES"),
    ("STAGE3D_TYPES", "STAGE3D_TYPES"),
    ("CANVAS_TYPES", "CANVAS_TYPES"),
    ("EDITOR_TYPES", "EDITOR_TYPES"),
    ("COLLAB_TYPES", "COLLAB_TYPES"),
]
for ts_name, kt_name in LISTS:
    web = ts_list(ts_name)
    android = kt_set(kt_name, kt_all)
    missing = [t for t in web if t not in android]
    extra = [t for t in android if t not in web]
    if missing:
        failures.append(f"{kt_name}: Android missing {missing}")
    if extra:
        failures.append(f"{kt_name}: Android has extras {extra}")
    if not missing and not extra:
        notes.append(f"{kt_name}: {len(web)}/{len(web)} match")

# --- 2. form fields: every type has a Kotlin branch ---------------------------
fields = ts_list("FORM_FIELD_TYPES")
for f in fields:
    if f'"{f}" ->' not in kt_all:
        failures.append(f"form field '{f}' has no Kotlin when-branch")
notes.append(f"form fields: {len(fields)}/{len(fields)} branches")

# --- 3. v3 kind dispatches ----------------------------------------------------
# Sub-types handled by a when-chain's else/default branch — dispatched, but
# with no literal string in the file (particles IS the default sim world, etc).
DEFAULT_TYPES = {
    "sim": {"particles"},
    "stage3d": {"scene", "scatter3d", "molecule", "cad", "anatomy"},
    "canvas": {"whiteboard"},
    "editor": {"sheet"},
    "collab": {"counter"},
}
DISPATCH_FILES = {
    "sim": ("WidgetsSims.kt", "SIM_TYPES"),
    "stage3d": ("WidgetsStage3D.kt", "STAGE3D_TYPES"),
    "canvas": ("WidgetsCanvasSurfaces.kt", "CANVAS_TYPES"),
    "editor": ("WidgetsEditor2.kt", "EDITOR_TYPES"),
    "collab": ("WidgetsCollab.kt", "COLLAB_TYPES"),
}
for kind, (fname, list_name) in DISPATCH_FILES.items():
    src = read(K / fname)
    defaults = DEFAULT_TYPES[kind]
    for t in ts_list(list_name):
        if t in defaults:
            continue
        if f'"{t}"' not in src:
            failures.append(f"{kind} '{t}' not dispatched in {fname}")
    notes.append(f"{kind}: dispatch checked in {fname} ({len(defaults)} default-branch type(s))")
# app + media dispatch
app_types = ts_list("APP_TYPES")
app_src = read(K / "WidgetsApp.kt")
missing_app = [t for t in app_types if f'"{t}"' not in app_src and t != "timer"]
if missing_app:
    failures.append(f"app types with no branch in WidgetsApp.kt: {missing_app}")
notes.append("app: dispatch checked (timer covered by APP_TYPES set + TimerLayout)")
media_types = ts_list("MEDIA_TYPES")
media_src = read(K / "WidgetsMedia.kt")
blocks_missing = [t for t in media_types if f'"{t}"' not in media_src and t != "code"]
if blocks_missing:
    failures.append(f"media types with no branch in WidgetsMedia.kt: {blocks_missing}")
notes.append("media: dispatch checked (code shares the CodePreview path)")

# --- 4. mermaid heads vs the bundled engine ----------------------------------
mermaid_kt = read(K / "MermaidDiagram.kt")
m = re.search(r"KNOWN_MERMAID_HEADS = listOf\((.*?)\)", mermaid_kt, re.S)
if not m:
    failures.append("KNOWN_MERMAID_HEADS not found")
    heads = []
else:
    heads = re.findall(r'"([^"]+)"', m.group(1))
bundle = read(ROOT / "android/app/src/main/assets/mermaid.min.js")
for h in heads:
    if h not in bundle:
        failures.append(
            f"mermaid head '{h}' gated as known but NOT registered by the bundled "
            "engine — it would render a parse-error graphic; remove it or upgrade the asset"
        )
unsupported_ok = ["packet-beta", "architecture-beta", "kanban", "radar-beta", "radar",
                  "treemap-beta", "treemap", "venn-beta", "venn", "ishikawa", "fishbone",
                  "wardley-beta", "wardley", "treeView-beta", "treeView", "swimlane-beta",
                  "eventmodeling", "zenuml"]
for h in unsupported_ok:
    if h in heads and h not in bundle:
        failures.append(f"mermaid head '{h}' must not be gated (engine lacks it)")
notes.append(f"mermaid: {len(heads)} heads verified against the bundled engine")

# --- 5. iOS parity (WidgetBlocks.swift) --------------------------------------
IOS_WB = ROOT / "ios/App/Sources/Features/Chats/Content/WidgetBlocks.swift"
if not IOS_WB.exists():
    failures.append("iOS: WidgetBlocks.swift missing")
else:
    ios_src = read(IOS_WB)

    def swift_set(name: str) -> list[str]:
        m_s = re.search(rf"let {name}: Set<String> = \[(.*?)\]", ios_src, re.S)
        if not m_s:
            failures.append(f"iOS: set {name} not found in WidgetBlocks.swift")
            return []
        return [s.strip().lower() for s in re.findall(r'"([^"]+)"', m_s.group(1))]

    ios_kinds = swift_set("WIDGET_LANGUAGES")
    missing_ios_kinds = [k for k in web_kinds if k not in ios_kinds]
    if missing_ios_kinds:
        failures.append(f"iOS WIDGET_LANGUAGES missing {missing_ios_kinds}")
    else:
        notes.append(f"iOS WIDGET_LANGUAGES: {len(web_kinds)}/{len(web_kinds)} top-level kinds match")

    for ts_name, sw_name in LISTS:
        web_items = [t.lower() for t in ts_list(ts_name)]
        ios_items = swift_set(sw_name)
        miss = [t for t in web_items if t not in ios_items]
        if miss:
            failures.append(f"iOS {sw_name} missing {miss}")
    notes.append(f"iOS sub-type sets: {len(LISTS)}/{len(LISTS)} lists verified")

# --- 6. Cross-file AST & Symbol Consistency (Android + iOS Section 01 & 02) ---
cb_kt = read(K / "ContentBlocks.kt")
rb_kt = read(K / "RichBlocks.kt")
cbc_kt = read(K / "CodeBlockCard.kt")
wv2_kt = read(K / "WidgetsV2.kt")

declared_kt_spans = set(
    re.findall(r"data class (\w+)\s*\([^)]*\)\s*:\s*InlineSpan", cb_kt)
)
used_kt_spans = set(re.findall(r"\bInlineSpan\.(\w+)\b", kt_all))
unknown_kt_spans = used_kt_spans - declared_kt_spans
if unknown_kt_spans:
    failures.append(f"Android InlineSpan unknown symbol(s): {sorted(unknown_kt_spans)} (declared: {sorted(declared_kt_spans)})")
else:
    notes.append(f"Android InlineSpan symbols: {len(used_kt_spans)}/{len(declared_kt_spans)} verified across all .kt files")

block_section = cb_kt.split("sealed interface Block")[1].split("\n}\n")[0]
declared_kt_blocks = set(
    re.findall(r"data class (\w+)\s*\(", block_section)
)
ext_kt_block_fns = set(re.findall(r"fun Block\.(\w+)", kt_all))
used_kt_blocks = set(re.findall(r"\bBlock\.(\w+)\b", kt_all)) - {"sourceStart"} - ext_kt_block_fns
unknown_kt_blocks = used_kt_blocks - declared_kt_blocks
if unknown_kt_blocks:
    failures.append(f"Android Block unknown symbol(s): {sorted(unknown_kt_blocks)}")
else:
    notes.append(f"Android Block symbols: {len(used_kt_blocks)}/{len(declared_kt_blocks)} verified across all .kt files")

if "val checked: Boolean?" not in cb_kt:
    failures.append("Android ContentBlocks.kt: ListItem missing `val checked: Boolean?`")
if "val url: String" not in cb_kt:
    failures.append("Android ContentBlocks.kt: CitationEntry missing `val url: String`")

for kt_file in sorted(K.glob("*.kt")):
    src = read(kt_file)
    has_wildcard = "import androidx.compose.foundation.layout.*" in src
    if not has_wildcard:
        for mod_fn in ("height", "width", "size", "padding", "fillMaxWidth"):
            if re.search(rf"\.{mod_fn}\s*\(", src) and f"import androidx.compose.foundation.layout.{mod_fn}" not in src:
                failures.append(f"Android {kt_file.name}: uses `.{mod_fn}(...)` without importing `androidx.compose.foundation.layout.{mod_fn}`")
notes.append("Android Compose layout modifier imports verified across all .kt files")

IOS_CB = ROOT / "ios/App/Sources/Features/Chats/Content/ContentBlocks.swift"
IOS_RB = ROOT / "ios/App/Sources/Features/Chats/Content/RichBlocks.swift"
if IOS_CB.exists() and IOS_RB.exists():
    cb_sw = read(IOS_CB)
    rb_sw = read(IOS_RB)
    if "var checked: Bool?" not in cb_sw and "let checked: Bool?" not in cb_sw:
        failures.append("iOS ContentBlocks.swift: ListItem missing `checked: Bool?`")
    if "var url: String" not in cb_sw and "let url: String" not in cb_sw:
        failures.append("iOS ContentBlocks.swift: CitationEntry missing `url: String`")
    for bad_pattern, msg in [
        (r"case \.strikethrough\b", "iOS RichBlocks.swift uses `.strikethrough` instead of `.strike`"),
        (r"case \.citation\(", "iOS RichBlocks.swift uses `.citation(` which is not a case of InlineSpan"),
        (r"if case \.code\b", "iOS RichBlocks.swift uses `case .code` instead of `case .codeBlock`"),
        (r"CitationsBlockView\(entries:", "iOS RichBlocks.swift uses `CitationsBlockView(entries:)` instead of `CitationsBlockView(citations:)`"),
        (r"let thinkingPhase:\s*String\?", "iOS RichBlocks.swift uses `let thinkingPhase: String?` instead of `NexlyThinkingPhase`"),
    ]:
        if re.search(bad_pattern, rb_sw):
            failures.append(msg)
    notes.append("iOS ContentBlocks.swift + RichBlocks.swift symbol & enum case consistency verified")

    # Section 01 (01–05) & Section 02 (06–07) feature parity across Android & iOS
    wb_sw = read(IOS_WB)
    ios_all = cb_sw + rb_sw + wb_sw
    for bad_wb, msg in [
        (r"\bpeakPoint\b", "iOS WidgetBlocks.swift references undeclared `peakPoint` instead of `peakPt`"),
        (r"\bformatChartMetric\b", "iOS WidgetBlocks.swift references undeclared `formatChartMetric` instead of `compactNumber`"),
    ]:
        if re.search(bad_wb, wb_sw):
            failures.append(msg)
    if "sourceStart: records[from].offset" not in cb_sw:
        failures.append("iOS ContentBlocks.swift: buildList must set real `sourceStart: records[from].offset`")
    if "extractCopyableProseSummary" not in rb_sw or "extractCopyableBlockSummary" not in kt_all:
        failures.append("Copy/Share contextual action pill must extract real prose/citation summary instead of copying button label")
    SECTION_FEATURES = [
        ("NexlyCalloutCard", "02. Structured Answers: Callout cards (Quick summary / Important / Key Insights)"),
        ("NexlyQuickCalculatorCard", "01. Dashboard: Interactive Quick Calculator card from ordinary Markdown"),
        ("InteractiveChecklistItemRow", "02. Structured Answers: Interactive Next Actions / Todo checklists"),
        ("+ Add Task", "01/02. Interactive + Add Task button on Todo checklists"),
        ("StepOrderedListItemRow", "02. Structured Answers: Numbered step-by-step plan circles & bold step split"),
        ("Trusted Source", "03. Citations & References: Trusted Source / Academic pills"),
        ("Run in Browser", "04/05. Code Blocks & Execution: Run in Browser / Run Code bar"),
        ("extractCodeSeriesPreview", "04/05. Code Blocks & Execution: Inline Revenue Trend bar chart preview"),
        ("localhost:3000", "05. Code Execution: Interactive Component localhost:3000 preview"),
        ("High Priority", "05. Code Execution: Task Overview 4-KPI strip"),
        ("Show:", "06. Tables & Data Grids: Pagination & Show: N display controls"),
        ("Last 6 Months", "07. Interactive Charts: Date range selector"),
        ("Key Insights", "06/07. Tables & Charts: Key Insights summary card"),
        ("Ask follow-up", "01–07. Bottom contextual action pills row"),
    ]
    for token, desc in SECTION_FEATURES:
        if token not in kt_all:
            failures.append(f"Android missing '{token}' ({desc})")
        if token not in ios_all:
            failures.append(f"iOS missing '{token}' ({desc})")
    notes.append(f"Section 01 & 02 feature parity: {len(SECTION_FEATURES)}/{len(SECTION_FEATURES)} features verified on both Android and iOS")

# --- report ------------------------------------------------------------------
print("== android parity audit ==")
for n in notes:
    print(f"ok   {n}")
if failures:
    print(f"ANDROID PARITY FAIL — {len(failures)} hole(s):")
    for f in failures:
        print(f"  - {f}")
    sys.exit(1)
print("ANDROID PARITY PASS — every catalog.ts type is accepted by the Kotlin twin.")
