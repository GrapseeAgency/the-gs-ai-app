# WIDGET COVERAGE

> **V3 (2026-10-20).** The 60-item "impossible" mega-list is now a tier map:
> **34 kinds, 34 chart types, 8 live simulations, 8 three-dimensional stage
> views, 4 canvas surfaces, 7 app editors, 1 terminal runtime, 20 app
> experiences, 14 calculators, 17 form-field types, 16 media forms, 24
> mermaid languages, 3 native blocks — 189 declared component types**, plus
> unlimited bespoke ```html. Tier 1 is native and real; Tier 2 ships
> degraded and labelled; Tier 3 is refused with stated reasons — never faked.
>
> **V2 (2026-10-20).** The catalogue stopped at 18 kinds and became a
> compositional vocabulary: 29 kinds, 28 chart types — 151 declared types
> (see git history for the V2 header).
>
> | Layer | Status |
> |---|---|
> | Catalogue + validators + compiler + palettes (`src/lib/widgets/`) | shipped, 107/107 tests |
> | Web renderers (`src/components/genui/`, 115 files) | shipped — gallery at `/genui` |
> | Android renderers (Compose) | v1 kinds shipped; v2 kinds to sync |
> | Prompt contract (`src/lib/widgets/prompt.ts`) | shipped — full vocabulary + palette system |

# Widget coverage — the 150-item interactive set

Route legend:

- **native** — first-class Compose renderer (chart / control / media / panel / actions)
- **mermaid** — first-class Mermaid diagram renderer
- **map** — Leaflet map renderer (pins, live routing, clustering, comparison)
- **html** — sandboxed WebView mini-app (model-generated, CSP-locked)
- **partial** — works, with the stated caveat

Status: `done` = shipped in v0.80.0 · `partial` = works with a caveat · `planned` = not built yet.

---

## Interactive controls (22)

| # | Item | Route | Status |
|---|------|-------|--------|
| 1 | Buttons | `actions` (kind=prompt/copy/open) | done |
| 2 | Text inputs | `form` type=text | done |
| 3 | Multiline text areas | `form` type=textarea | done |
| 4 | Checkboxes | `form` type=checkbox | done |
| 5 | Radio buttons | `form` type=radio | done |
| 6 | Dropdown selectors | `form` type=select | done |
| 7 | Segmented controls | `form` type=segmented | done |
| 8 | Sliders | `form` type=slider | done |
| 9 | Date pickers | `form` type=date | done |
| 10 | Forms | `form` | done |
| 11 | Submit actions | `form` submit + send | done |
| 12 | Toggle-like checkbox controls | `form` type=toggle | done |
| 13 | Clickable cards | `cards` | done |
| 14 | Clickable surfaces | `panel` header / `cards` | done |
| 15 | Hover-triggered badges | `panel` badges (tap-to-show on mobile) | done |
| 16 | Popovers | `panel` popover | done |
| 17 | Expandable or revealable panels | `panel` expand/collapse | done |
| 18 | Carousel navigation | `carousel` | done |
| 19 | Interactive selection menus | `form` select / segmented | done |
| 20 | Copy-to-clipboard actions | `actions` kind=copy | done |
| 21 | Open-link actions | `actions` kind=open | done |
| 22 | Follow-up prompt actions | `followups` | done |

## Charts and data visualization (39)

| # | Item | Route | Status |
|---|------|-------|--------|
| 23 | Bar charts | `chart` bar | done |
| 24 | Horizontal bar charts | `chart` hbar | done |
| 25 | Grouped bar charts | `chart` grouped | done |
| 26 | Stacked bar charts | `chart` stacked | done |
| 27 | Line charts | `chart` line | done |
| 28 | Multi-series line charts | `chart` line (N series) | done |
| 29 | Pie charts | `chart` pie | done |
| 30 | Donut charts | `chart` donut | done |
| 31 | Scatter plots | `chart` scatter | done |
| 32 | Bubble charts | `chart` bubble | done |
| 33 | Time-series charts | `chart` line (x = time) | done |
| 34 | Area charts | `chart` area | done |
| 35 | Stacked area charts | `chart` stackedArea | done |
| 36 | Histograms | `chart` histogram | done |
| 37 | Box plots | `chart` boxplot | done |
| 38 | Heat maps | `chart` heatmap | done |
| 39 | Treemaps | `chart` treemap | done |
| 40 | Radar charts | `chart` radar | done |
| 41 | Waterfall charts | `chart` waterfall | done |
| 42 | Funnel charts | `chart` funnel | done |
| 43 | Gauge charts | `gauge` | done |
| 44 | Sankey diagrams | mermaid `sankey-beta` | done |
| 45 | Chord diagrams | `chart` type=chord | done |
| 46 | Network graphs | mermaid flowchart / `html` | done |
| 47 | Geographical data visualizations | `map` | done |
| 48 | Statistical plots | `chart` boxplot / histogram | done |
| 49 | Correlation matrices | `chart` correlation | done |
| 50 | Distribution plots | `chart` histogram / boxplot | done |
| 51 | Financial charts | `chart` candlestick | done |
| 52 | Candlestick charts | `chart` candlestick | done |
| 53 | Gantt charts | `chart` gantt / mermaid | done |
| 54 | KPI cards | `stat` | done |
| 55 | Progress indicators | `progress` | done |
| 56 | Data tables | `table` | done |
| 57 | Comparison tables | `compare` | done |
| 58 | Filterable data views | `table` live filter field | done |
| 59 | Sortable data views | `table` click-to-sort columns | done |
| 60 | Dashboard layouts | `app` type=dashboard | done |

## Maps and location (10)

| # | Item | Route | Status |
|---|------|-------|--------|
| 61 | Interactive pin maps | `map` pins | done |
| 62 | Multi-location maps | `map` pins | done |
| 63 | Route maps | `map` route | done |
| 64 | Driving directions | `map` mode=driving + stops | done — live OSRM route + turn-by-turn steps |
| 65 | Walking routes | `map` mode=walking + stops | done — routed-foot profile |
| 66 | Cycling routes | `map` mode=cycling + stops | done — routed-bike profile |
| 67 | Multi-stop itineraries | `map` stops[] | done — ordered waypoints, per-leg steps |
| 68 | Grouped map locations | `map` cluster=true | done — counted cluster bubbles, tap to expand |
| 69 | Place-detail carousels | `map` cards=true | done — map + fused place carousel |
| 70 | Map-based comparisons | `map` compare[] | done — 2–4 panels side by side |

## Diagrams and structured visuals (20)

| # | Item | Route | Status |
|---|------|-------|--------|
| 71 | Flowcharts | mermaid `flowchart` | done |
| 72 | Decision trees | mermaid `flowchart` | done |
| 73 | Process diagrams | mermaid `flowchart` | done |
| 74 | System architecture diagrams | mermaid `flowchart` | done |
| 75 | Sequence diagrams | mermaid `sequenceDiagram` | done |
| 76 | Entity-relationship diagrams | mermaid `erDiagram` | done |
| 77 | Class diagrams | mermaid `classDiagram` | done |
| 78 | State diagrams | mermaid `stateDiagram` | done |
| 79 | Dependency graphs | mermaid `flowchart` | done |
| 80 | Mind maps | mermaid `mindmap` | done |
| 81 | Organization charts | mermaid `flowchart` | done |
| 82 | Timeline diagrams | mermaid `timeline` / `timeline` widget | done |
| 83 | Network diagrams | mermaid `flowchart` | done |
| 84 | Mermaid diagrams | mermaid | done |
| 85 | UML diagrams | mermaid class/sequence | done |
| 86 | Data-flow diagrams | mermaid `flowchart` | done |
| 87 | Swimlane diagrams | mermaid `flowchart` / `sequenceDiagram` | done |
| 88 | Wireframes | `html` (SVG wireframe) | done |
| 89 | Concept maps | mermaid `mindmap` | done |
| 90 | Tree diagrams | mermaid `flowchart` | done |

## Media and content (18)

| # | Item | Route | Status |
|---|------|-------|--------|
| 91 | Single images | `media` type=image | done |
| 92 | Image galleries | `media` type=gallery | done |
| 93 | Image carousels | `carousel` / `media` gallery | done |
| 94 | Video embeds | `media` type=video | done — inline `<video>` player in the sandboxed WebView |
| 95 | Video previews | `media` type=video (poster) | done |
| 96 | Audio experiences | `media` type=audio | done — inline `<audio>` player |
| 97 | Voice conversations | `voice` widget → app voice mode | done — mic card opens the real SpeechRecognizer screen |
| 98 | Interactive code previews | `media` type=code → sandbox | done — JS runs with captured console, HTML previews in a sandboxed iframe |
| 99 | Editable code blocks | `media` type=code → sandbox | done — inline editor, Run, Copy |
| 100 | Rich-text writing blocks | `html` (contenteditable) | done |
| 101 | Document previews | `media` type=doc | done — PDF page-by-page (PdfRenderer), inline image and text |
| 102 | File cards | `media` type=file | done |
| 103 | Linked resources | `media` type=link | done |
| 104 | Entity cards | `media` type=entity | done |
| 105 | Product cards | `media` type=product | done |
| 106 | Business listings | `media` type=listing | done |
| 107 | Source citations | `media` type=citation | done |
| 108 | Search-result presentations | `media` type=search | done |

## Calculators and simulations (20)

All 20 render through the sandboxed `html` mini-app kind (the model writes one
self-contained document; the app renders it in the CSP-locked WebView).

| # | Item | Route | Status |
|---|------|-------|--------|
| 109 | Unit converters | `calc` type=unit | done |
| 110 | Currency converters | `calc` type=currency | done |
| 111 | Financial calculators | `calc` type=finance | done |
| 112 | Loan calculators | `calc` type=loan | done |
| 113 | Mortgage calculators | `calc` type=mortgage | done |
| 114 | Pricing calculators | `calc` type=pricing | done |
| 115 | Tax estimators | `calc` type=tax | done |
| 116 | Budget planners | `calc` type=budget | done |
| 117 | Investment scenario calculators | `calc` type=investment | done |
| 118 | Probability calculators | `calc` type=probability | done |
| 119 | Statistical calculators | `calc` type=statistics | done |
| 120 | Scientific calculators | `calc` type=scientific | done |
| 121 | Date and time calculators | `calc` type=datetime | done |
| 122 | Interactive quizzes | `quiz` (native) | done |
| 123 | Knowledge tests | `quiz` (native) | done |
| 124 | Decision-support tools | `app` type=decision | done |
| 125 | Scenario comparison tools | `app` type=scenario | done |
| 126 | What-if simulators | `calc` (live sliders) | done |
| 127 | Parameter-driven visualizations | `calc` + `chart` | done |
| 128 | Interactive educational demonstrations | `html` (interactive demo) | done |

## Application-style experiences (22)

All 22 render through the sandboxed `html` mini-app kind, or compose existing
native widgets where that is simpler and more robust.

| # | Item | Route | Status |
|---|------|-------|--------|
| 129 | Multi-step wizards | `app` type=wizard | done |
| 130 | Onboarding flows | `app` type=onboarding | done |
| 131 | Configuration panels | `app` type=config / `form` | done |
| 132 | Search interfaces | `app` type=search | done |
| 133 | Filter panels | `app` type=filter | done |
| 134 | Product configurators | `app` type=configurator | done |
| 135 | Booking interfaces | `app` type=booking | done |
| 136 | Scheduling interfaces | `app` type=schedule | done |
| 137 | Task-management interfaces | `app` type=tasks | done |
| 138 | Kanban boards | `app` type=kanban | done |
| 139 | Calendar views | `app` type=calendar | done |
| 140 | Spreadsheet-style interfaces | `app` type=sheet | done |
| 141 | Form-based workflows | `form` / `app` type=wizard | done |
| 142 | Analytics explorers | `app` type=analytics | done |
| 143 | Interactive reports | `app` type=report | done |
| 144 | File-analysis interfaces | `app` type=files | done |
| 145 | Connected-app interfaces | `app` type=connected | done |
| 146 | Custom mini-apps | `html` (custom mini-app) | done |
| 147 | Stateful interactive tools | `app` / `calc` (stateful) | done |
| 148 | Responsive dashboard interfaces | `app` type=dashboard | done |
| 149 | Interactive prototypes | `html` (prototype) | done |
| 150 | Custom HTML/JavaScript application experiences | `html` (custom app) | done |

---

## Totals

| Status | Count |
|--------|-------|
| done (native renderer / mermaid / map / html) | 150 |
| partial (stated caveat) | 0 |
| planned | 0 |
| **total** | **150** |

All 150 items have a dedicated route. Nothing is left on the html
fallback except the categories that are custom HTML by definition. The remaining limits are
platform facts, not missing widgets, and each is stated in-product rather than
hidden:

- Map routing calls the public OSRM demo servers; without a network connection
  the map falls back to the `route` polyline and the static `steps` list.
- Code execution is JavaScript/HTML only, inside the sandboxed WebView with
  `default-src 'none'` — no network, no file access, no storage. Other
  languages are editable and copyable, and the card says so.
- PDF previews are capped at 15 MB and rasterized page by page on device.


---

## Model independence (v0.81.5)

Rendering must not depend on the model knowing it is talking to this client.
A flash or text-only model answers "show me a bar chart" with prose —
*"I'm unable to create or display visualizations like bar charts"* — and the
old pipeline rendered that refusal verbatim, even though every renderer below
was already shipped.

`WidgetRecovery` (`ui/chat/content/WidgetRecovery.kt`) closes that gap:

- detects a visual request in the user's turn (`chart`, `bar chart`, `pie`,
  `plot`, `table`, …); diagram requests (`flowchart`, `mindmap`) are never
  hijacked into data charts;
- when the answer carries no widget fence, extracts labelled numbers from the
  answer itself — or from the previous assistant turn, for the common
  "show me a chart" follow-up that carries no data of its own;
- builds a native `chart` (all 22 types), `table`, `stat`, `progress`,
  `timeline` or `carousel` widget locally, titled
  "… — from the answer above" so the user can see it was reconstructed;
- removes only the *false* capability claim ("I can't display charts"), and
  only after a widget was recovered; a genuine "I don't have that data" is
  left untouched;
- never invents a number: with fewer than two labelled values it renders
  nothing rather than drawing an empty chart.

28 unit tests (`WidgetRecoveryTest`) cover the exact on-device failure,
including the sector-list → bar-chart case, and assert the recovered block
survives `parseWidget` — the real renderer gate — with a composition key that
cannot collide with a parsed block.

---

# Catalogue v3 — the 60-item mega-list, tier by tier

Research anchors: OpenAI Apps SDK (sandboxed widget iframes, `window.openai`
bridge, `requestDisplayMode`), Google A2UI v1.0 (flat ID-referenced component
lists, BYO-design-system catalogs), three.js examples (OrbitControls,
molecular/cad conventions), marching-squares literature (contours), Streamlit
`data_editor` (spreadsheet semantics), Mermaid mindmap/kanban grammar.

## Tier 1 — native, real, interactive (shipped)

| Mega-list item | Route | Notes |
|---|---|---|
| Physics — cloth | `sim` view=`cloth` | Verlet integration, mouse tearing, pin row |
| Physics — soft body | `sim` view=`softbody` | pressure-soft ring, pokeable |
| Physics — fluid | `sim` view=`fluid` | semi-Lagrangian stable fluids |
| Physics — smoke | `sim` view=`smoke` | advected density field |
| Physics — particles | `sim` view=`particles` | gravity/emitter, tap to burst |
| Physics — rigid bodies | `sim` view=`rigid` | stacked impulse bodies |
| Terrain editor | `sim` view=`terrain` | sculptable heightbrush |
| Playable game | `sim` view=`game` | keyboard+touch, localStorage best score |
| 3D scatter / surface | `stage3d` view=`scatter3d`/`surface3d` | lazy three.js, z = real expression |
| Molecule viewer | `stage3d` view=`molecule` | ball-and-stick from nodes+bonds |
| CAD part | `stage3d` view=`cad` | articulated primitives |
| Anatomy | `stage3d` view=`anatomy` | layered shells |
| 3D scene | `stage3d` view=`scene` | composed node graph |
| VR/AR | `stage3d` view=`vr`/`ar` | **Tier 2** magic-window framing, labelled |
| Whiteboard | `canvas` surface=`whiteboard` | pens, eraser, DPR-clean |
| Mind map | `canvas` surface=`mindmap` | draggable, curved edges, branch button |
| Diagram editor | `canvas` surface=`diagram` | shapes, arrow connect, orthogonal drag |
| GIS polygon drawing | `canvas` surface=`polygons` | Leaflet draw, spherical area |
| Spreadsheet + formulas | `editor` editor=`sheet` | A1 refs, SUM/AVG/MIN/MAX/COUNT, cycle guard |
| Node/visual programming | `editor` editor=`nodes` | live dataflow evaluation |
| Pivot table | `editor` editor=`pivot` | group-by + sum/avg/count |
| Website builder | `editor` editor=`site` | block list, DnD reorder, preview |
| CSS editor | `editor` editor=`css` | flex controls + live codegen |
| Video editor | `editor` editor=`video` | multi-track timeline, rAF playhead |
| Audio editor/DAW | `editor` editor=`audio` | WebAudio playback, waveform, STFT spectrogram, note sequencer |
| Terminal | `terminal` | toy fs, history, sandboxed JS REPL (no network/DOM/storage ids) |
| Charts — violin | `chart` type=`violin` | Gaussian KDE, Silverman bandwidth |
| Charts — contour | `chart` type=`contour` | marching squares, 3–12 iso levels |
| Charts — parallel coordinates | `chart` type=`parallel` | per-axis normalisation |
| Charts — dendrogram | `chart` type=`dendrogram` | merge-distance tree |
| Charts — force graph | `chart` type=`force` | rAF simulation, draggable nodes |
| Charts — depth (order book) | `chart` type=`depth` | bids/asks ladder |
| Streaming dashboards | `app` type=`dashboard` + SSE broker (v1) | live sections |
| Kanban drag-and-drop | `app` type=`kanban` | pointer DnD columns |

## Tier 2 — degraded, labelled (honest capability ceiling)

| Item | Shipped as | Why degraded |
|---|---|---|
| VR/AR | `stage3d` `vr`/`ar` magic window (gyro-free orbit + framed passthrough styling) | Per the WebXR spec, immersive sessions require the `xr-spatial-tracking` permissions policy (default allowlist `'self'`, embeddable only via `allow="xr-spatial-tracking"`) AND user consent — a chat widget iframe cannot claim one unilaterally (w3.org/TR/webxr §3.2, immersiveweb.dev). Labelled in-renderer; a host that opts in with the iframe allow attribute unlocks full VR later. |
| Multiplayer collaboration | shared-state prototype via the SSE stream broker | True CRDT co-editing needs a presence backend; the broker gives one-writer-many-readers sync. |

## Tier 3 — refused, with reasons (never faked)

| Item | Reason |
|---|---|
| Full IDE + debugger in chat | A debugger needs an execution target; the chat sandbox has no user processes to attach to. Anything rendered would be a lying mock. |
| Persistent apps with their own backend + database | Chat surfaces are stateless between turns by design; granting per-app DBs means granting storage privileges the runtime does not have. |
| Device-hardware panels (camera/mic/battery/sensors) | Iframe sandbox denies device prompts by policy; a fake battery meter is worse than none. |
| Notification UIs | Notifications require a service worker + user permission; both are outside the widget sandbox. |
| Unrestricted execution | The model's code runs behind CSP + blocked identifiers (`fetch`, storage, DOM) — by threat model, not by omission. |
