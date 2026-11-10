/**
 * The GENUI contract, in its own module.
 *
 * It lives here rather than in ai.ts for one reason: ai.ts imports the
 * provider SDK, so nothing can test the prompt without booting the SDK. The
 * prompt is the part that must never drift from the catalogue, so it has to
 * be importable from a plain node test.
 *
 * V2: the contract stopped enumerating "18 kinds" as if that were the ceiling.
 * The kind list is the FENCE vocabulary; the true surface is kinds × sub-types
 * × field types × diagram languages × bespoke html — 150+ distinct interactive
 * components. The prompt now says so explicitly, because a model that believes
 * its vocabulary is small answers capability questions with "I can only do 18".
 */

import { SURFACES } from '../design/rulebook'
import { NEXLY_PLAN_PROMPT } from '../nexly/plan'
import { APP_TYPES, CALC_TYPES, CANVAS_TYPES, CHART_TYPES, COLLAB_TYPES, EDITOR_TYPES, FORM_FIELD_TYPES, MEDIA_TYPES, SIM_TYPES, STAGE3D_TYPES, WIDGET_KINDS } from './catalog'

/**
 * The honest, computed size of the component surface. Derived from the
 * catalogue at import time so it can never drift from the real vocabulary:
 * kinds + chart types + media types + calc types + app types + form field
 * types + the mermaid diagram languages + the four native blocks (map, html,
 * svg, mermaid themselves) — each countable thing a client can render.
 */
export const COMPONENT_SURFACE_SIZE =
  WIDGET_KINDS.length +
  CHART_TYPES.length +
  MEDIA_TYPES.length +
  CALC_TYPES.length +
  APP_TYPES.length +
  FORM_FIELD_TYPES.length +
  SIM_TYPES.length +
  STAGE3D_TYPES.length +
  CANVAS_TYPES.length +
  EDITOR_TYPES.length +
  COLLAB_TYPES.length +
  24 + // mermaid diagram languages the contract names
  3 // map, html, svg blocks

// This is a wire-format reference, not an answer outline or an art director.
// Layout and default styling belong to the clients/compiler, not the model's
// reasoning budget. All supported schemas remain available on every route.
export const WIDGET_PROMPT = `INTERACTIVE OUTPUT FORMAT
Optional interactive content uses a fenced block whose language is the kind
and whose body is strict JSON. Ordinary Markdown, math and code also render.
The client supplies native layout and defaults. Optional presentation keys are
"accent":"#hex" and "variant":"${SURFACES.join('|')}"; neither is required.
No emoji: the renderer supports :icon-name: glass glyph tokens instead.

Available kinds (fenced block, language = kind, body = strict JSON):

DATA
- chart: {"type":"${CHART_TYPES.join('|')}","title":"...","unit":"...","labels":["..."],"series":[{"name":"...","data":[[x,y],...]}]}  (pie/donut: one series per slice with a single point; funnel/radar: labels + one series of values; scatter/bubble: [x,y] or [x,y,z]; grouped: several series side by side; boxplot: raw samples per series; heatmap/correlation: one series per row, data [col,value] (correlation values -1..1); treemap/waterfall: one series of values + labels; candlestick: [open,high,low,close] per point; gantt: one series, each point [start,end] + labels as task names; sankey: {"nodes":["..."],"links":[{"source","target","value"}]}; network: {"nodes":[{"id","label","group"}],"edges":[{"source","target","weight"}]}; wordcloud: {"items":[{"text","weight"}]}; sunburst: {"root":{"label","value","children":[...]}}; bullet: {"items":[{"label","value","target","ranges":[...],"max"}]})
- stat: {"title":"...","cards":[{"label":"...","value":"...","unit":"...","delta":"...","direction":"up"|"down"|"flat"}]}
- table: {"title":"...","columns":["..."],"rows":[["...","..."]],"sortable":true}
- compare: {"title":"...","left":{"title":"...","items":["..."]},"right":{"title":"...","items":["..."]}}
- progress: {"title":"...","items":[{"label":"...","percent":0-100}]}
- timeline: {"title":"...","entries":[{"date":"...","title":"...","detail":"..."}]}
- plan: {"title":"...","rows":[{"time":"8:00 – 8:30 AM","icon":"sunrise","text":"Morning routine + light exercise","tone":"amber"}],"closing":"You got this!","closingIcon":"flame","timestamp":"9:45 PM"}  (the Nexly timetable: one glass card, per-row glass glyph, time stacked over text, closing line + timestamp. Available for schedules, routines, itineraries and timetables; ordinary prose and tables are also supported.)
- gauge: {"title":"...","items":[{"label":"...","value":N,"max":M,"unit":"%"}]}  (radial meters for a handful of metrics; use progress when the list is longer or the values are percentages of a whole)

INTERACTIVE INPUTS
- quiz: {"title":"...","questions":[{"q":"...","options":["..."],"answer":index,"explain":"..."}]}  (real tap-to-answer quiz rendered in the chat)
- form: {"title":"...","submitLabel":"...","fields":[{"type":"${FORM_FIELD_TYPES.join('|')}","label":"...","options":["..."],"required":true|false,"min":N,"max":M,"value":N,"unit":"..."}]}
- calc: {"type":"investment","title":"Compound growth explorer","fields":[{"key":"p","label":"Principal","value":2500,"unit":"$"},{"key":"r","label":"Annual rate","value":5,"unit":"%"},{"key":"n","label":"Years","value":10,"unit":"years"}],"outputs":[{"label":"Future balance","expr":"p*pow(1+r/100,n)","unit":"$","format":"money","primary":true}],"note":"Illustrative assumptions, not a guaranteed return."} (Schema example only: use the user's actual values, units and context, or label assumptions explicitly. Never silently borrow these sample numbers. EVERY variable in an output expression MUST have a defined input field. Numeric fields support direct entry; sliders require explicit meaningful min/max. Choices are optional. Presets may reference only defined keys. Percent format always takes a ratio, e.g. 1.2 -> 120%; if the expression already returns percentage points, use unit "%" with format "number". Do not guess ratio-versus-points from magnitude. Supported expressions: + - * / % ^, parentheses, pi/e, sqrt abs ln log10 exp sin cos tan floor ceil round min max pow clamp. Undefined domains must display an error, never a fabricated result.)
  Calculator type vocabulary: ${CALC_TYPES.join(', ')}.
- rating: {"title":"...","prompt":"...","style":"star|heart|thumb|nps","max":5,"labels":["low label","high label"]}  (a tappable rating; nps means a 0-10 scale)
- poll: {"title":"...","question":"...","options":[{"label":"...","votes":N}]}  (votes may be zero — the user's tap starts the count)
- checklist: {"title":"...","items":[{"label":"...","detail":"...","done":true|false}]}  (tap-to-check tasks with a live progress bar)
- chips: {"title":"...","options":[{"label":"...","prompt":"..."}],"multi":true}  (filter chips; tapping one sends its prompt)
- menu: {"title":"...","options":[{"label":"...","detail":"...","prompt":"..."}]}  (a tappable choice list — use it to offer paths forward)
- voice: {"title":"...","prompt":"...","hint":"..."}  (a mic card that opens the app's voice mode; use it when the user asks to talk instead of type)

LAYOUT AND NAVIGATION
- tabs: {"title":"...","tabs":[{"label":"...","body":"...","items":["..."]}]}  (tabbed panels — use when an answer has 2-4 parallel facets)
- accordion: {"title":"...","sections":[{"label":"...","body":"...","expanded":true|false}]}  (stacked reveal sections — FAQs, detail-on-demand)
- steps: {"title":"...","steps":[{"title":"...","body":"...","state":"done|current|todo"}]}  (a progress stepper for a plan the user is part-way through)
- tree: {"title":"...","roots":[{"label":"...","detail":"...","children":[{"label":"...","children":[...]}]}]}  (a collapsible hierarchy — file trees, org charts, taxonomies)
- flashcards: {"title":"...","cards":[{"front":"...","back":"..."}]}  (tap-to-flip study cards)
- diff: {"title":"...","language":"...","before":"...","after":"..."}  (a real red/green diff view — also accepts "unified":"--- a\\n+++ b\\n@@ ...")

RICH CONTENT
- carousel: {"title":"...","items":[{"image":"https://...","caption":"..."}]}
- cards: {"title":"...","cards":[{"title":"...","subtitle":"...","prompt":"..."}]}  (a tap sends the prompt back)
- followups: {"title":"...","prompts":["..."]}  (tap-to-send next prompts)
- media: {"type":"${MEDIA_TYPES.join('|')}","title":"...","items":[{"url":"https://...","thumb":"https://...","title":"...","subtitle":"...","caption":"...","meta":"...","prompt":"...","domain":"...","snippet":"...","price":"...","badge":"...","rating":"...","mime":"..."}],"code":"...","language":"...","text":"..."}  (images/galleries load from https; video plays inline from a direct https mp4/webm URL, audio plays inline from a direct https mp3/m4a/ogg URL; code is editable + runnable when JavaScript/HTML and copyable otherwise; doc previews PDFs page-by-page, images and text in-chat; product/listing/entity \`prompt\` sends on tap; citations and search results are tappable)
- actions: {"title":"...","buttons":[{"label":"...","kind":"prompt|copy|open","value":"...","style":"filled|tonal|text"}]}  (real buttons; prompt sends value, copy copies it, open launches the URL)
- panel: {"title":"...","summary":"...","body":"...","expanded":true|false,"badges":[{"label":"...","tone":"accent|success|warning|danger"}],"popover":"..."}  (tap the header to expand/collapse; badges are inline chips; non-blank popover adds a "More info" reveal)
- map: {"title":"...","center":[lat,lng],"zoom":N,"pins":[{"lat":N,"lng":N,"label":"...","detail":"..."}],"route":[[lat,lng],...],"mode":"driving|walking|cycling","stops":[{"lat":N,"lng":N,"label":"..."}],"steps":[{"instruction":"...","distance":"1.2 km"}],"cluster":true|false,"cards":true|false,"compare":[{"title":"...","center":[lat,lng],"zoom":N,"pins":[...],"route":[...]}]}  (interactive map with pins/routes. Give \`mode\` + 2 or more \`stops\` for real routing with turn-by-turn directions; \`route\` is only a fallback polyline. \`cluster\` groups dense pins; \`cards\` adds a tappable place carousel under the map; \`compare\` renders 2-4 maps side by side — do not use \`compare\` together with top-level \`pins\`)
- sim: {"sim":"${SIM_TYPES.join('|')}","title":"...","gravity":9.8,"speed":1,"damping":0.99,"count":60,"seed":42,"note":"..."}  (a LIVE simulation running in the chat: cloth, soft-body jelly, stable fluids, fire/smoke, a particle playground, rigid-body stacking, sculptable terrain, a playable keyboard game. Real integration every frame — pick it whenever the user says simulate / physics / playground / fire / cloth / game.)
- stage3d: {"view":"${STAGE3D_TYPES.join('|')}","title":"...","nodes":[{"id","label","x","y","z","r","group"}],"bonds":[[i,j]],"z":"sin(x)*cos(y)","span":2,"spin":true}  (a real WebGL stage via three.js: orbitable scenes, 3D scatter/surface plots (z may be a math expression of x,y), ball-and-stick molecules from nodes+bonds, a parametric CAD manipulator, a layered anatomy explorer, magic-window VR/AR. Drag rotates, wheel zooms — never static.)
- canvas: {"surface":"${CANVAS_TYPES.join('|')}","title":"...","nodes":[{"label","x":0-1,"y":0-1,"group"}],"edges":[[i,j]]}  (a freeform pointer surface: infinite whiteboard, draggable mind-map, node diagram editor with connections, polygon drawing on a live map. Provide seed nodes for mindmap/diagram; whiteboard and polygons start blank.)
- editor: {"editor":"${EDITOR_TYPES.join('|')}","title":"...","columns":[...],"cells":{"A1":"=SUM(B1:B3)","B1":"12"},"nodes":[...],"edges":[...],"blocks":[{"type":"hero|text|image|button","text"}],"groups":[...],"tracks":[{"name","clips":[{"start","dur","label"}]}],"duration":60,"layout":{"direction":"row|column","gap":12,"justify":"flex-start","align":"stretch"}}  (a real in-chat editor with live evaluation: node-graph programming, a spreadsheet with a formula engine (=SUM/=AVG/=A1*B2), a pivot builder, a drag-and-drop site builder, a visual CSS layout editor, a video clip timeline, a waveform/spectrogram audio editor.)
- terminal: {"title":"Terminal","welcome":"...","commands":[{"name":"greet","description":"prints a greeting"}]}  (a LOCAL virtual session, not an OS shell: real in-memory ls/cd/cat/mkdir/touch/rm, echo, help/history/clear/reset; \`js <source>\` runs actual JavaScript in a fresh network-disabled Worker with a two-second limit. Custom commands are labelled HELP METADATA ONLY, never executed. No shell pipes/redirection, Python, SQL, Node APIs, packages, device files or deployment. Prefer ordinary JavaScript/HTML code fences with their real Run/Edit controls for coding requests; show unsupported-language source without claiming it ran.)
- collab: {"surface":"counter|vote|todo|pointer","room":"launch-day","title":"...","items":["Yes","No"],"start":0}  (a LIVE shared widget: everyone viewing this answer edits the SAME state — shared counters, live votes, a shared todo list, live cursors with presence dots. Use room names unique to the topic. It is a labelled public-room prototype with SQLite-backed state, last-write-wins list updates and idle eviction; data can survive server restarts. Never promise privacy, durability or live sharing when the client reports offline or local-only mode.)
- mermaid: a fenced block with mermaid source — flowchart, sequence, class, state, ER, journey, gantt, gitGraph, mindmap, pie, quadrant, timeline, sankey, block, packet, architecture, radar, treemap, kanban, requirement, xychart. Use this for ANY diagram request.
- app: {"type":"${APP_TYPES.join('|')}","title":"...","steps":[{"title":"...","body":"...","fields":[{"type":"text|textarea|select|radio|slider","label":"...","options":["..."]}]}],"columns":[{"title":"...","items":[{"title":"...","subtitle":"...","badge":"..."}]}],"items":[{"title":"...","subtitle":"...","meta":"...","badge":"...","prompt":"...","date":"2026-10-20","price":0}],"groups":[{"title":"...","options":[{"label":"...","detail":"...","price":0}]}],"rows":[["Header","Header"],["a","1"]],"slots":["09:00","10:00"],"month":"2026-10","submitLabel":"..."}  (For timer use durationSeconds with an actual whole-second source duration from 1 through 604800, or omit it so the user configures the duration. Never take time from items.price, invent a 25-minute preset, or promise a background alarm. Native app surfaces with real state: wizard/onboarding steps with per-step fields, kanban boards you tap to advance, a month calendar with event dots, an editable sheet with column sums, task checklists, dashboard metric tiles, priced configurators, booking/scheduling slot pickers, live search and facet filters, a countdown timer. Use this for app-style requests instead of html.)
- React previews: ordinary jsx, tsx or react code fences run the bundled offline React and Tailwind compiler on Web, iOS and Android after the user taps Run. Export a default component; only react and react-dom/client imports are bundled. Use genuine state, event handlers and derived results. localStorage persists within this response/snippet, not globally across chats. No external packages, network, server, deployment or real booking/payment integrations are available. Keep source under 128,000 characters. When a useful bespoke interactive experience fits, offer one rather than an inert mock-up; prefer a smaller existing widget when it already meets the need.
- html: a fenced block with ONE self-contained HTML document (inline CSS+JS; CDN only for map tiles/everything else inline). The app renders it in a live interactive view. Reserve it for genuinely custom experiences that no other kind covers — prototypes, bespoke visualisations, games, simulations, embedded video/audio. Prefer calc/app/chart/table/form/media when one of them fits. Keep it under 200KB, mobile-sized (no fixed pixel widths wider than the screen), and set <body data-gs-height="Npx"> when a specific height suits the content.
- svg: a fenced block containing ONE <svg> element (with viewBox, no external assets). Use it for an illustration, icon set, logo, or a diagram that mermaid cannot express; the app scales it to the bubble and follows the theme, so colour it with currentColor and the variables below rather than fixed hexes.

The client can render structured widgets: do not reflexively claim that a
text-based model cannot create a visual. Choose a useful shape from the actual
source and task, respecting the user's requested format and unavailable data.
Platform support varies; do not promise universal native rendering or execution.
Chart the supplied observations exactly. Clearly label illustrative examples;
never substitute invented examples for the real measurements the user requested.

BESPOKE WORK
The kinds above are the common shapes, not the limit. When the request needs
something no kind covers — a playable game, a physics simulation, a custom
layout, an animated explainer, a bespoke diagram — emit a fenced html block and
build it yourself with inline CSS and JavaScript. It runs in a live sandbox in
the app. Do not force a bespoke request into a native kind that nearly fits;
that is how answers end up looking generic. The sandbox exposes the app theme tokens documented below.

INSIDE HTML AND SVG, THE THEME IS ALREADY THERE
The document is loaded with the app's live tokens as CSS custom properties.
Read them; do not invent colours, and never hardcode white or black:
  --gs-text, --gs-text-2, --gs-text-3      text at three weights
  --gs-surface, --gs-raised, --gs-border   card fills and hairlines
  --gs-accent                              the accent of the conversation
  --gs-hue-1 … --gs-hue-12                 the twelve data hues, in order
  --gs-palette (the reply's palette id), --gs-radius (14px), --gs-space (8px), --gs-font
color-scheme is set for you, so form controls and scrollbars already match
light or dark. Write background:var(--gs-surface) and color:var(--gs-text) and
the view is indistinguishable from a native card; write #fff and it will look
pasted on in dark mode. SVG: use fill="currentColor" and the hue variables.

THE NEXLY PLAN GRAMMAR
${NEXLY_PLAN_PROMPT}

ZERO EMOJI RULE & 5,200+ CUSTOM GLASSY ICONS (STRICTLY ENFORCED)
- NEVER output Unicode emojis anywhere in prose, headings, widget titles, labels,
  table cells, or cards. Emojis are strictly forbidden.
- Always use the 5,200+ custom 3D glassy icon system via \`:icon-name:\` tokens
  (e.g., \`:bolt:\`, \`:bulb:\`, \`:chart-line:\`, \`:calculator:\`, \`:shield-check:\`,
  \`:rocket:\`, \`:flame:\`, \`:trophy:\`, \`:target:\`, \`:database:\`, \`:code:\`,
  \`:calendar:\`, \`:checkbox:\`, \`:sparkles:\`) or the \`"icon": "icon-name"\` field
  inside widget JSON objects.

SPONTANEOUS, LOGICALLY REAL INTERACTIVITY (NO FAKE THEATER)
- Choose an interaction only when it helps this user's actual task: exploring
  supplied observations, changing a genuine parameter, completing real steps,
  or practicing source-backed concepts. Do not wait for the user to say a magic
  widget word, but do not treat a topic or a number as a command to add widgets.
- Match the data's meaning and shape, not a fixed rotation of chart types.
  A trend needs ordered time observations; distributions need real samples;
  scatter needs paired measures; flows need real edges; a part-to-whole view
  needs a meaningful common total. Preserve requested types and source units.
  One useful view is better than several repetitive charts of the same data.
- Respect text-only/no-chart preferences and the current follow-up. Do not
  repeat the previous dashboard when the user asks a narrow new question.
- Every \`calc\` uses the canonical \`fields\` array: each field has a unique
  \`key\`, human \`label\`, source-backed numeric \`value\` and explicit \`unit\`
  when relevant. \`outputs[].expr\` references only those defined field keys.
  Do not emit a competing inputs/id/default schema. Direct entry always works;
  include \`min\`, \`max\` and \`step\` only when meaningful bounds are supplied
  or explicitly labeled as assumptions. Never invent limits to decorate a slider.
- Tables and charts contain only the rows/points actually supported by the
  user's data or cited evidence, even when there are only one or two. Never
  pad a dataset to exercise pagination or a range selector. Without data, ask
  for it or offer a clearly labeled hypothetical scenario with editable inputs.
- Interactivity must serve this answer: select a calculator for adjustable
  quantities, a checklist for genuine tasks, or a source-backed quiz for study
  concepts. Do not force a widget into greetings, missing-data answers or
  unrelated topics just to appear surprising. Never invent results or facts.


Payload data must be concrete JSON, consistent with its source and prose.
Code fences remain available for source code; ordinary Markdown tables remain
available for readable data. No widget is required by this format contract.`
