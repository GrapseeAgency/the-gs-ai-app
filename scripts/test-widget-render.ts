/**
 * RENDER PROOF — every declared sub-type is not merely accepted and
 * registered, it is SERVER-RENDERED here with its representative payload
 * (scripts/widget-payloads.ts). A renderer that throws, renders empty, or
 * renders only an "EmptyState" placeholder fails this test.
 *
 * This is the functional arm of docs/CATALOGUE_AUDIT.md:
 *   test-widget-catalogue.ts  proves  validate -> renderer registered -> fence routes
 *   test-widget-render.ts     proves  the renderer actually DRAWS the payload
 *
 * Compile with tsconfig.probe.json, run:
 *   node dist/probe/scripts/test-widget-render.js
 */

import './probe-alias-hook'
import { renderToString } from 'react-dom/server'
import { createElement } from 'react'
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
} from '../src/lib/widgets/catalog'
import { validateWidget } from '../src/lib/widgets/validate'
import { rendererFor } from '../src/components/genui/registry'
import { MapBlock } from '../src/components/genui/blocks/MapBlock'
import {
  chartPayload,
  mediaPayload,
  calcPayload,
  appPayload,
  fieldPayload,
  simPayload,
  stage3dPayload,
  canvasPayload,
  editorPayload,
  collabPayload,
} from './widget-payloads'

let pass = 0
const holes: string[] = []

function renderKind(kind: string, json: string, label: string): void {
  const r = validateWidget(kind, json)
  if (!r.ok) {
    holes.push(`${label}: payload rejected (${r.kind})`)
    return
  }
  const Component = rendererFor(kind)
  if (!Component) {
    holes.push(`${label}: no renderer registered`)
    return
  }
  try {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const html = renderToString(createElement(Component as any, { payload: r.widget }))
    if (html.length < 80) {
      holes.push(`${label}: rendered only ${html.length} chars`)
    } else if (html.includes('misconfigured') || html.includes('EmptyState')) {
      holes.push(`${label}: rendered an empty-state placeholder instead of content`)
    } else {
      pass++
    }
  } catch (e) {
    holes.push(`${label}: RENDER THREW — ${e instanceof Error ? e.message : String(e)}`)
  }
}

const groups: Array<{ name: string; items: readonly string[]; kind: string; payload: (t: string) => string }> = [
  { name: 'chart', items: CHART_TYPES, kind: 'chart', payload: chartPayload },
  { name: 'media', items: MEDIA_TYPES, kind: 'media', payload: mediaPayload },
  { name: 'calc', items: CALC_TYPES, kind: 'calc', payload: calcPayload },
  { name: 'app', items: APP_TYPES, kind: 'app', payload: appPayload },
  { name: 'field', items: FORM_FIELD_TYPES, kind: 'form', payload: fieldPayload },
  { name: 'sim', items: SIM_TYPES, kind: 'sim', payload: simPayload },
  { name: 'stage3d', items: STAGE3D_TYPES, kind: 'stage3d', payload: stage3dPayload },
  { name: 'canvas', items: CANVAS_TYPES, kind: 'canvas', payload: canvasPayload },
  { name: 'editor', items: EDITOR_TYPES, kind: 'editor', payload: editorPayload },
  { name: 'collab', items: COLLAB_TYPES, kind: 'collab', payload: collabPayload },
]

console.log('== render proof (server-side, every declared sub-type) ==')
let total = 0
for (const g of groups) {
  const before = holes.length
  for (const t of g.items) {
    total++
    renderKind(g.kind, g.payload(t), `${g.name}/${t}`)
  }
  const ok = g.items.length - (holes.length - before)
  console.log(`${holes.length === before ? 'ok  ' : 'HOLE'} ${g.name}: ${ok}/${g.items.length} render with content`)
}

// every kind, plus terminal explicitly and the native map block
const kindSamples: Record<string, string> = {
  chart: chartPayload('line'),
  stat: '{"cards":[{"label":"Revenue","value":"120"}]}',
  table: '{"columns":["a"],"rows":[["1"]]}',
  compare: '{"left":{"items":["a"]},"right":{"items":["b"]}}',
  progress: '{"items":[{"label":"x","value":50}]}',
  timeline: '{"entries":[{"date":"2026-01-01","title":"a"}]}',
  plan: '{"title":"Tomorrow","rows":[{"time":"8:00 AM","icon":"sunrise","text":"Morning routine"}],"closing":"You got this!","closingIcon":"flame"}',
  gauge: '{"items":[{"label":"a","value":4,"max":10}]}',
  quiz: '{"questions":[{"q":"a?","options":["x","y"],"answer":1}]}',
  form: fieldPayload('text'),
  calc: calcPayload('unit'),
  rating: '{"title":"a","prompt":"b","style":"star","max":5}',
  poll: '{"question":"a?","options":["x","y"]}',
  checklist: '{"items":[{"label":"a","done":false}]}',
  chips: '{"options":[{"label":"a","prompt":"p"}]}',
  menu: '{"options":[{"label":"a","prompt":"p"}]}',
  voice: '{"prompt":"a"}',
  tabs: '{"tabs":[{"label":"a","body":"b"}]}',
  accordion: '{"sections":[{"label":"a","body":"b"}]}',
  steps: '{"steps":[{"title":"a","body":"b"}]}',
  tree: '{"roots":[{"label":"a","children":[{"label":"b"}]}]}',
  carousel: '{"items":[{"image":"https://x.test/a.jpg"}]}',
  cards: '{"cards":[{"title":"a"}]}',
  followups: '{"prompts":["a"]}',
  actions: '{"buttons":[{"label":"a","value":"b"}]}',
  panel: '{"title":"a","body":"b"}',
  flashcards: '{"cards":[{"front":"a","back":"b"}]}',
  diff: '{"title":"a","before":"x","after":"y"}',
  media: mediaPayload('image'),
  app: appPayload('tasks'),
  sim: simPayload('particles'),
  stage3d: stage3dPayload('scene'),
  canvas: canvasPayload('mindmap'),
  editor: editorPayload('sheet'),
  terminal: '{"title":"sh","commands":[{"name":"deploy","description":"demo"}]}',
  collab: collabPayload('vote'),
}
for (const k of WIDGET_KINDS) {
  total++
  renderKind(k, kindSamples[k] ?? '{}', `kind/${k}`)
}
total++
{
  // map is an OTHER_RENDER_KIND with its own block pipeline (validateWidget
  // does not own it) — render MapBlock directly with a MapPayload
  try {
    const html = renderToString(
      createElement(MapBlock, {
        payload: { title: 'Coverage', pins: [{ lat: 23.8, lng: 90.4, label: 'Dhaka' }, { lat: 22.3, lng: 91.8, label: 'Chattogram' }] },
      }),
    )
    if (html.length < 80) holes.push('map/MapBlock: rendered only ' + html.length + ' chars')
    else pass++
  } catch (e) {
    holes.push(`map/MapBlock: RENDER THREW — ${e instanceof Error ? e.message : String(e)}`)
  }
}

console.log('')
if (holes.length === 0) {
  console.log(`RENDER PROOF PASS — ${pass}/${total} declared types server-render real content. 0 crashes.`)
} else {
  console.error(`RENDER PROOF FAIL — ${holes.length}/${total}:`)
  for (const h of holes) console.error(`  - ${h}`)
  process.exit(1)
}
