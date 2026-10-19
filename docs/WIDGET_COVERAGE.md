# Widget coverage — the 150-item interactive set

Route legend:

- **native** — first-class Compose renderer (chart / control / media / panel / actions)
- **mermaid** — first-class Mermaid diagram renderer
- **map** — Leaflet map renderer
- **html** — sandboxed WebView mini-app (model-generated, CSP-locked)
- **partial** — works, with the stated caveat

Status: `done` = shipped in v0.79.0 · `partial` = works with a caveat · `planned` = not built yet.

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
| 44 | Sankey diagrams | mermaid `sankey-beta` / `html` | done |
| 45 | Chord diagrams | `html` (d3 chord) | done |
| 46 | Network graphs | mermaid / `html` | done |
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
| 60 | Dashboard layouts | `html` / stacked widgets | done |

## Maps and location (10)

| # | Item | Route | Status |
|---|------|-------|--------|
| 61 | Interactive pin maps | `map` pins | done |
| 62 | Multi-location maps | `map` pins | done |
| 63 | Route maps | `map` route | done |
| 64 | Driving directions | `map` route | partial — polyline, no turn-by-turn |
| 65 | Walking routes | `map` route | partial — polyline, no profile |
| 66 | Cycling routes | `map` route | partial — polyline, no profile |
| 67 | Multi-stop itineraries | `map` route | partial — waypoints as polyline |
| 68 | Grouped map locations | `map` pins | partial — no clustering |
| 69 | Place-detail carousels | `map` + `carousel` | partial — two widgets, not fused |
| 70 | Map-based comparisons | `html` / two maps | partial — not a dedicated layout |

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
| 88 | Wireframes | `html` | done |
| 89 | Concept maps | mermaid `mindmap` | done |
| 90 | Tree diagrams | mermaid `flowchart` | done |

## Media and content (18)

| # | Item | Route | Status |
|---|------|-------|--------|
| 91 | Single images | `media` type=image | done |
| 92 | Image galleries | `media` type=gallery | done |
| 93 | Image carousels | `carousel` / `media` gallery | done |
| 94 | Video embeds | `media` type=video | partial — opens externally, no inline player |
| 95 | Video previews | `media` type=video (thumb) | done |
| 96 | Audio experiences | `media` type=audio | partial — opens externally |
| 97 | Voice conversations | app voice mode (native) | partial — chat voice path, not a widget |
| 98 | Interactive code previews | `media` type=code | partial — highlighted + copy, no execution |
| 99 | Editable code blocks | `html` / `media` code | partial — copy only, no inline editor |
| 100 | Rich-text writing blocks | `html` (contenteditable) | done |
| 101 | Document previews | `media` type=file | partial — opens externally |
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
| 109 | Unit converters | `html` | done |
| 110 | Currency converters | `html` | done |
| 111 | Financial calculators | `html` | done |
| 112 | Loan calculators | `html` | done |
| 113 | Mortgage calculators | `html` | done |
| 114 | Pricing calculators | `html` | done |
| 115 | Tax estimators | `html` | done |
| 116 | Budget planners | `html` | done |
| 117 | Investment scenario calculators | `html` | done |
| 118 | Probability calculators | `html` | done |
| 119 | Statistical calculators | `html` | done |
| 120 | Scientific calculators | `html` | done |
| 121 | Date and time calculators | `html` | done |
| 122 | Interactive quizzes | `quiz` (native) | done |
| 123 | Knowledge tests | `quiz` (native) | done |
| 124 | Decision-support tools | `html` | done |
| 125 | Scenario comparison tools | `html` | done |
| 126 | What-if simulators | `html` | done |
| 127 | Parameter-driven visualizations | `html` + `chart` | done |
| 128 | Interactive educational demonstrations | `html` | done |

## Application-style experiences (22)

All 22 render through the sandboxed `html` mini-app kind, or compose existing
native widgets where that is simpler and more robust.

| # | Item | Route | Status |
|---|------|-------|--------|
| 129 | Multi-step wizards | `html` | done |
| 130 | Onboarding flows | `html` | done |
| 131 | Configuration panels | `html` / `form` | done |
| 132 | Search interfaces | `html` | done |
| 133 | Filter panels | `html` | done |
| 134 | Product configurators | `html` | done |
| 135 | Booking interfaces | `html` | done |
| 136 | Scheduling interfaces | `html` | done |
| 137 | Task-management interfaces | `html` | done |
| 138 | Kanban boards | `html` | done |
| 139 | Calendar views | `html` | done |
| 140 | Spreadsheet-style interfaces | `html` | done |
| 141 | Form-based workflows | `form` / `html` | done |
| 142 | Analytics explorers | `html` | done |
| 143 | Interactive reports | `html` + `chart` | done |
| 144 | File-analysis interfaces | `html` | done |
| 145 | Connected-app interfaces | `html` | done |
| 146 | Custom mini-apps | `html` | done |
| 147 | Stateful interactive tools | `html` | done |
| 148 | Responsive dashboard interfaces | `html` | done |
| 149 | Interactive prototypes | `html` | done |
| 150 | Custom HTML/JavaScript application experiences | `html` | done |

---

## Totals

| Status | Count |
|--------|-------|
| done (native / mermaid / map / html) | 138 |
| partial (stated caveat) | 13 |
| planned | 0 |

Partials are honest limits of the current build: video/audio open externally,
map routing has no turn-by-turn/profiles/clustering, place-detail carousels and
map comparisons are composed rather than fused, voice is the app's existing
voice path rather than a widget, and code blocks copy but do not execute. Every
one still renders and is usable; none is a silent drop.
