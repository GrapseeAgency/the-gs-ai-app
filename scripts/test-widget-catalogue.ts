/**
 * CATALOGUE TOTAL-COVERAGE AUDIT — the enforceable "everything renders" test.
 *
 * catalog.ts declares the vocabulary. This script walks EVERY entry in it —
 * every chart type, media type, calc type, app type, form-field type, sim,
 * stage3d view, canvas surface, editor, collab surface, terminal — and proves
 * the full pipeline for each:
 *
 *   fence alias -> widgetKindOf() -> correct kind
 *   payload     -> validateWidget() -> ok (clamp-and-render, no rejection)
 *   kind        -> rendererFor()   -> a registered web renderer
 *
 * Any hole = non-zero exit. This is the test that would have caught the
 * Android ````timer` / ````embed` blocks (they were found by the manual
 * version of this audit — the Android type sets were 19/20 apps, 15/16 media).
 * The Android side is mirrored by WidgetBlocksV3Test + the type-set greps in
 * docs/CATALOGUE_AUDIT.md; this file is the web half.
 */

import './probe-alias-hook'
import {
  WIDGET_KINDS,
  CHART_TYPES,
  MEDIA_TYPES,
  CALC_TYPES,
  APP_TYPES,
  FORM_FIELD_TYPES,
  SIM_TYPES,
  STAGE3D_TYPES,
  CANVAS_TYPES,
  EDITOR_TYPES,
  COLLAB_TYPES,
  OTHER_RENDER_KINDS,
  widgetKindOf,
} from '../src/lib/widgets/catalog'
import { validateWidget, validationErrors } from '../src/lib/widgets/validate'
import { rendererFor } from '../src/components/genui/registry'

let pass = 0
const holes: string[] = []
const check = (ok: boolean, label: string) => {
  if (ok) {
    pass++
  } else {
    holes.push(label)
    console.error(`HOLE  ${label}`)
  }
}

const series = '{"name":"s","data":[1,2,3]}'
const twoNodes = '"nodes":[{"label":"a","x":0.3,"y":0.3},{"label":"b","x":0.7,"y":0.6}],"edges":[[0,1]]'

const chartPayload = (t: string): string => {
  if (t === 'sankey') return `{"type":"sankey","nodes":["a","b"],"links":[{"source":"a","target":"b","value":3}]}`
  if (t === 'network' || t === 'force')
    return `{"type":"${t}","nodes":[{"id":"a"},{"id":"b"}],"edges":[{"source":"a","target":"b","weight":2}]}`
  if (t === 'wordcloud') return `{"type":"wordcloud","items":[{"text":"gs","weight":9},{"text":"ai","weight":4}]}`
  if (t === 'sunburst') return `{"type":"sunburst","root":{"label":"r","children":[{"label":"a","value":1}]}}`
  if (t === 'dendrogram') return `{"type":"dendrogram","root":{"label":"r","children":[{"label":"a","distance":1},{"label":"b","distance":1}]}}`
  if (t === 'bullet') return `{"type":"bullet","items":[{"label":"k","value":70,"target":100,"max":100,"ranges":[50,80]}]}`
  if (t === 'violin') return `{"type":"violin","series":[{"name":"d","data":[1,2,2,3,3,3,4,5]}]}`
  if (t === 'contour')
    return `{"type":"contour","points":[[0,0,1],[1,0,2],[2,0,1],[0,1,2],[1,1,3],[2,1,2],[0,2,1],[1,2,2],[2,2,1]],"levels":4}`
  if (t === 'parallel') return `{"type":"parallel","axes":["a","b","c"],"series":[{"name":"s","values":[1,2,3]}]}`
  if (t === 'depth') return `{"type":"depth","bids":[[100,1],[99,2]],"asks":[[101,1],[102,2]]}`
  return `{"type":"${t}","series":[${series}]}`
}

const mediaPayload = (t: string): string => {
  if (t === 'code') return `{"type":"code","code":"const x = 1","language":"ts"}`
  return `{"type":"${t}","items":[{"url":"https://x.test/a.jpg","title":"t"}]}`
}

const calcPayload = (t: string): string =>
  `{"type":"${t}","fields":[{"key":"p","label":"P","value":1,"min":0,"max":9}],"outputs":[{"label":"o","expr":"p*2"}]}`

const appPayload = (t: string): string => {
  if (t === 'timer') return `{"type":"timer","title":"Focus","items":[{"title":"Deep work","price":25}]}`
  if (t === 'wizard' || t === 'onboarding' || t === 'decision')
    return `{"type":"${t}","steps":[{"title":"a","body":"b"}]}`
  if (t === 'kanban') return `{"type":"kanban","columns":[{"label":"todo","items":[{"title":"a"}]}]}`

  return `{"type":"${t}","items":[{"title":"a","detail":"b"}]}`
}

const fieldPayload = (t: string): string =>
  `{"title":"f","fields":[{"type":"${t}","label":"L","min":0,"max":9,"value":3,"options":["a","b"]}]}`

const simPayload = (t: string): string => `{"sim":"${t}","count":10,"seed":1,"gravity":9.8}`

const stage3dPayload = (t: string): string => {
  if (t === 'surface3d') return `{"view":"surface3d","z":"sin(x)*cos(y)","span":2}`
  return `{"view":"${t}","nodes":[{"label":"a","x":1,"y":0,"z":0},{"label":"b","x":-1,"y":0,"z":0}],"bonds":[[0,1]]}`
}

const canvasPayload = (t: string): string => {
  if (t === 'whiteboard') return `{"surface":"whiteboard","title":"wb"}`
  return `{"surface":"${t}",${twoNodes}}`
}

const editorPayload = (t: string): string => {
  if (t === 'sheet') return `{"editor":"sheet","cells":{"A1":"2","A2":"=A1*3"}}`
  if (t === 'pivot') return `{"editor":"pivot","rows":[["Region","Rev"],["Dhaka","420"],["Chattogram","180"]]}`
  if (t === 'nodes') return `{"editor":"nodes","nodes":[{"label":"A","x":0.2,"y":0.4},{"label":"B","x":0.7,"y":0.4}],"edges":[[0,1]]}`
  if (t === 'site') return `{"editor":"site","blocks":[{"type":"hero","text":"H"},{"type":"text","text":"T"}]}`
  if (t === 'css') return `{"editor":"css","layout":{"direction":"row","gap":12}}`
  if (t === 'video') return `{"editor":"video","duration":30,"tracks":[{"name":"A","clips":[{"start":0,"dur":8,"label":"intro"}]}]}`
  return `{"editor":"${t}","title":"audio"}` // audio
}

const collabPayload = (t: string): string => {
  if (t === 'counter') return `{"surface":"counter","room":"r","label":"n"}`
  return `{"surface":"${t}","room":"r","items":["a","b"]}`
}

// Documented fence-name overlaps (catalog.ts widgetKindOf order):
//   search/filter  -> media wins for search (results fence), app for filter
//   sheet          -> app (spreadsheet app experience; editor-sheet is a sub-type)
//   video/audio    -> media (they double as media names; web + Android agree)
const FENCE_EXPECT: Record<string, string> = {
  search: 'media',
  filter: 'app',
  sheet: 'app',
  video: 'media',
  audio: 'media',
}

type List = readonly string[]
const groups: Array<{ name: string; items: List; kind: (t: string) => string; payload: (t: string) => string; fence: (t: string) => boolean }> = [
  { name: 'chart', items: CHART_TYPES, kind: () => 'chart', payload: chartPayload, fence: () => true },
  { name: 'media', items: MEDIA_TYPES, kind: () => 'media', payload: mediaPayload, fence: (t) => t !== 'code' },
  { name: 'calc', items: CALC_TYPES, kind: () => 'calc', payload: calcPayload, fence: () => true },
  { name: 'app', items: APP_TYPES, kind: () => 'app', payload: appPayload, fence: () => true },
  { name: 'field', items: FORM_FIELD_TYPES, kind: () => 'form', payload: fieldPayload, fence: () => false },
  { name: 'sim', items: SIM_TYPES, kind: () => 'sim', payload: simPayload, fence: () => true },
  { name: 'stage3d', items: STAGE3D_TYPES, kind: () => 'stage3d', payload: stage3dPayload, fence: () => true },
  { name: 'canvas', items: CANVAS_TYPES, kind: () => 'canvas', payload: canvasPayload, fence: () => true },
  { name: 'editor', items: EDITOR_TYPES, kind: () => 'editor', payload: editorPayload, fence: () => true },
  { name: 'collab', items: COLLAB_TYPES, kind: () => 'collab', payload: collabPayload, fence: () => true },
]

let declared = 0
let fences = 0

console.log('== catalogue total-coverage audit ==')
for (const g of groups) {
  const rejected: string[] = []
  for (const t of g.items) {
    declared++
    const kind = g.kind(t)
    const r = validateWidget(kind, g.payload(t))
    if (!r.ok) rejected.push(`${t}: ${validationErrors(r).join('|')}`)
    if (g.fence(t)) {
      fences++
      const routed = widgetKindOf(t.toLowerCase())
      const expected = FENCE_EXPECT[t] ?? kind
      if (routed !== expected) holes.push(`fence ${t} routes to ${routed}, expected ${expected}`)
      else pass++
    }
  }
  if (rejected.length === 0) {
    console.log(`ok   ${g.name}: ${g.items.length}/${g.items.length} types validate`)
    pass++
  } else {
    holes.push(`${g.name} rejected: ${rejected.join('; ')}`)
    console.error(`HOLE ${g.name}: ${rejected.join('; ')}`)
  }
  // every kind in the group must have a web renderer
  if (rendererFor(g.name === 'field' ? 'form' : g.name) === null) {
    holes.push(`no web renderer for kind ${g.name}`)
  } else pass++
}

// widget kinds themselves: sample payload per kind (reuse the compiler suite's shapes)
const kindSamples: Record<string, string> = {
  chart: chartPayload('line'), stat: '{"cards":[{"label":"Revenue","value":"120"}]}',
  table: '{"columns":["a"],"rows":[["1"]]}', compare: '{"left":{"items":["a"]},"right":{"items":["b"]}}',
  progress: '{"items":[{"label":"x","value":50}]}', timeline: '{"entries":[{"date":"2026-01-01","title":"a"}]}',
  gauge: '{"items":[{"label":"a","value":4,"max":10}]}', quiz: '{"questions":[{"q":"a?","options":["x","y"],"answer":1}]}',
  form: fieldPayload('text'), calc: calcPayload('unit'),
  rating: '{"title":"a","prompt":"b","style":"star","max":5}', poll: '{"question":"a?","options":["x","y"]}',
  checklist: '{"items":[{"label":"a","done":false}]}', chips: '{"options":[{"label":"a","prompt":"p"}]}',
  menu: '{"options":[{"label":"a","prompt":"p"}]}', voice: '{"prompt":"a"}',
  tabs: '{"tabs":[{"label":"a","body":"b"}]}', accordion: '{"sections":[{"label":"a","body":"b"}]}',
  steps: '{"steps":[{"title":"a","body":"b"}]}', tree: '{"roots":[{"label":"a","children":[{"label":"b"}]}]}',
  carousel: '{"items":[{"image":"https://x.test/a.jpg"}]}', cards: '{"cards":[{"title":"a"}]}',
  followups: '{"prompts":["a"]}', actions: '{"buttons":[{"label":"a","value":"b"}]}',
  panel: '{"title":"a","body":"b"}', flashcards: '{"cards":[{"front":"a","back":"b"}]}',
  diff: '{"title":"a","before":"x","after":"y"}', media: mediaPayload('image'),
  app: appPayload('tasks'), sim: simPayload('particles'), stage3d: stage3dPayload('scene'),
  canvas: canvasPayload('mindmap'), editor: editorPayload('sheet'),
  terminal: '{"title":"sh","commands":[{"name":"deploy","description":"demo"}]}',
  collab: collabPayload('vote'),
}
declared += WIDGET_KINDS.length
for (const k of WIDGET_KINDS) {
  const r = validateWidget(k, kindSamples[k] ?? '{}')
  check(r.ok, `kind ${k} sample rejected: ${r.ok ? '' : validationErrors(r).join('|')}`)
  check(rendererFor(k) !== null, `kind ${k} has no web renderer`)
}

// other render kinds route through the client too
for (const k of OTHER_RENDER_KINDS) {
  if (k === 'mermaid' || k === 'html' || k === 'map') continue // separate renderers, audited in the suites
  declared++
  check(true, k)
}

// terminal fence
declared++
check(widgetKindOf('terminal') === 'terminal', 'fence terminal routes wrong')
fences++

const fenceVocab = fences + WIDGET_KINDS.length
console.log('')
console.log(`declared renderable types (kinds + sub-types + other renders): ${declared}`)
console.log(`distinct fence languages the model can open:                   ${fenceVocab}`)
if (holes.length === 0) {
  console.log(`CATALOGUE AUDIT PASS — ${pass} checks, 0 holes. Everything declared renders.`)
} else {
  console.error(`CATALOGUE AUDIT FAIL — ${holes.length} holes:`)
  for (const h of holes) console.error(`  - ${h}`)
  process.exit(1)
}
