'use client'

/**
 * GenUI Gallery — the live showcase of every component the catalogue names.
 *
 * This page is the proof of the contract: 29 kinds × their sub-types, all 28
 * charts, the 20 app experiences, the calculators, the controls, and the four
 * native blocks — each one rendered by the same MessageContent pipeline the
 * chat uses, with real sample payloads, and a palette switcher so you can
 * watch a reply change its whole face.
 */

import { useState } from 'react'
import { MessageContent, PALETTES, type Citation } from '@/components/genui'

const C = {
  bg: '#0A0D12',
  surface: '#11151C',
  raised: '#181E28',
  outline: '#232B37',
  text: '#EDEFF2',
  muted: '#8B93A1',
  accent: '#2DD4A8',
}

type Section = { id: string; name: string; md: string }

/** Sample answers. Each is EXACTLY what the model emits on the wire. */
const SECTIONS: Section[] = [
  {
    id: 'charts-core',
    name: 'Charts — core',
    md: `## Weekly revenue, both stores

Revenue crossed **4.2M** in Q3 — the downtown store is pulling ahead.

\`\`\`chart
{"type":"line","title":"Revenue by month","unit":"M $","labels":["Apr","May","Jun","Jul","Aug","Sep"],"series":[{"name":"Downtown","data":[[0,0.9],[1,1.1],[2,1.0],[3,1.3],[4,1.5],[5,1.7]]},{"name":"Airport","data":[[0,0.7],[1,0.8],[2,0.9],[3,0.9],[4,1.1],[5,1.2]]}],"palette":"aurora","accent":"#4C6FFF","variant":"editorial"}
\`\`\`

Same data, ranked:

\`\`\`hbar
{"title":"Best months","labels":["Sep","Aug","Jul","Jun","May","Apr"],"series":[{"name":"Total","data":[[0,2.9],[1,2.6],[2,2.2],[3,1.9],[4,1.9],[5,1.6]]}],"palette":"citrus","accent":"#F08C00","variant":"tinted"}
\`\`\`

\`\`\`donut
{"title":"Share of September","series":[{"name":"Downtown","data":[[0,1.7]]},{"name":"Airport","data":[[0,1.2]]}],"palette":"reef","accent":"#1098AD"}
\`\`\``,
  },
  {
    id: 'charts-advanced',
    name: 'Charts — advanced',
    md: `## Flow, structure and markets

Budget flow for the launch:

\`\`\`sankey
{"type":"sankey","title":"Launch budget, $80k","nodes":["Budget","Ads","Events","Content","Digital","Print"],"links":[{"source":"Budget","target":"Ads","value":30},{"source":"Budget","target":"Events","value":20},{"source":"Budget","target":"Content","value":30},{"source":"Ads","target":"Digital","value":24},{"source":"Ads","target":"Print","value":6},{"source":"Content","target":"Digital","value":18},{"source":"Content","target":"Print","value":12}],"palette":"harbour","accent":"#0B7285","variant":"plain"}
\`\`\`

Team topology:

\`\`\`network
{"type":"network","title":"Service dependencies","nodes":[{"id":"API","label":"API"},{"id":"Auth","label":"Auth"},{"id":"Billing"},{"id":"Web"},{"id":"Worker"},{"id":"DB"}],"edges":[{"source":"Web","target":"API"},{"source":"API","target":"Auth"},{"source":"API","target":"Billing"},{"source":"API","target":"DB"},{"source":"Worker","target":"DB"},{"source":"Worker","target":"Billing"}],"palette":"glacier","accent":"#4C6FFF","variant":"outline"}
\`\`\`

Price action:

\`\`\`candlestick
{"type":"candlestick","title":"GSX, last 8 sessions","unit":"$","labels":["Mon","Tue","Wed","Thu","Fri","Mon","Tue","Wed"],"series":[{"name":"GSX","data":[[101,104,100,103],[103,105,102,102],[102,103,99,100],[100,101,97,98],[98,102,97,102],[102,106,101,105],[105,107,103,104],[104,108,103,107]]}],"palette":"ember","accent":"#0CA678","variant":"editorial"}
\`\`\`

Skill coverage:

\`\`\`radar
{"type":"radar","title":"Team skills, 0-100","labels":["Frontend","Backend","Design","Data","DevOps","Writing"],"series":[{"name":"Current","data":[[0,72],[1,88],[2,54],[3,63],[4,41],[5,77]]},{"name":"Target","data":[[0,85],[1,90],[2,75],[3,75],[4,65],[5,80]]}],"palette":"orchid","accent":"#9C36B5","variant":"tinted"}
\`\`\`

\`\`\`heatmap
{"type":"heatmap","title":"Deploys per weekday","labels":["Mon","Tue","Wed","Thu","Fri"],"series":[{"name":"9am","data":[[0,3],[1,5],[2,4],[3,6],[4,2]]},{"name":"1pm","data":[[0,4],[1,7],[2,5],[3,8],[4,3]]},{"name":"5pm","data":[[0,2],[1,3],[2,2],[3,4],[4,1]]}],"palette":"forest","accent":"#0CA678"}
\`\`\``,
  },
  {
    id: 'charts-v2',
    name: 'Charts — v2 family',
    md: `## The v2 chart family

Hierarchy, words, targets:

\`\`\`sunburst
{"type":"sunburst","title":"Support volume by area","root":{"label":"Support","children":[{"label":"Billing","value":120,"children":[{"label":"Refunds","value":70},{"label":"Invoices","value":50}]},{"label":"Technical","value":200,"children":[{"label":"Login","value":120},{"label":"Sync","value":80}]},{"label":"Sales","value":80}]},"palette":"berry","accent":"#D6336C"}
\`\`\`

\`\`\`wordcloud
{"type":"wordcloud","title":"What you asked this week","items":[{"text":"pricing","weight":40},{"text":"charts","weight":34},{"text":"android","weight":30},{"text":"palette","weight":24},{"text":"widgets","weight":22},{"text":"maps","weight":18},{"text":"quizzes","weight":16},{"text":"diff","weight":14},{"text":"mermaid","weight":12},{"text":"kanban","weight":10},{"text":"gantt","weight":9},{"text":"sankey","weight":8}],"palette":"dusk","accent":"#6741D9","variant":"plain"}
\`\`\`

\`\`\`bullet
{"type":"bullet","title":"Q4 targets","unit":"%","items":[{"label":"Activation","value":64,"target":75,"ranges":[50,70,100]},{"label":"Retention","value":81,"target":80,"ranges":[60,75,100]},{"label":"NPS","value":47,"target":60,"ranges":[40,55,100]}],"palette":"sunrise","accent":"#E8590C"}
\`\`\`

\`\`\`quadrant
{"type":"quadrant","title":"Roadmap: impact vs effort","labels":["Quick wins","Big bets","Fill-ins","Money pit"],"series":[{"name":"Items","data":[[0.8,0.7],[0.3,0.8],[0.7,0.25],[0.2,0.2],[0.55,0.5]]}],"max":1,"palette":"aurora","accent":"#5F3DC4","variant":"editorial"}
\`\`\`

\`\`\`waterfall
{"type":"waterfall","title":"From 1.0M to profit","unit":"k $","labels":["Start","Pricing","Churn","Support","Hires"],"series":[{"name":"Δ","data":[[0,1000],[1,180],[2,-90],[3,-45],[4,-120]]}],"palette":"citrus","accent":"#66A80F"}
\`\`\`

\`\`\`boxplot
{"type":"boxplot","title":"Response time by region (ms)","labels":["EU","US","APAC"],"series":[{"name":"EU","data":[[0,120],[1,132],[2,118],[3,145],[4,127],[5,122]]},{"name":"US","data":[[0,180],[1,205],[2,175],[3,230],[4,190],[5,185]]},{"name":"APAC","data":[[0,240],[1,260],[2,238],[3,290],[4,255]]}],"palette":"glacier","accent":"#0B7285"}
\`\`\``,
  },
  {
    id: 'interactive',
    name: 'Interactive inputs',
    md: `## Try them — every control is live

A real quiz:

\`\`\`quiz
{"title":"GS pipeline check","questions":[{"q":"Which fence language renders a funnel?","options":["funnel","chart-funnel","plot"],"answer":0,"explain":"Sub-type fences collapse to the chart kind."},{"q":"What carries the reply's palette?","options":["The theme file","The \\"palette\\" key","Android only"],"answer":1,"explain":"The model picks a palette per reply; the compiler enforces it."},{"q":"What renders while a fence is still streaming?","options":["Raw JSON","A skeleton","Nothing"],"answer":1,"explain":"Never raw JSON — skeleton, then the widget."}],"palette":"meadow","accent":"#0CA678","variant":"inset"}
\`\`\`

A form with eight field types:

\`\`\`form
{"title":"Ship checklist","submitLabel":"Request review","fields":[{"type":"text","label":"Feature name","value":"genui-v2"},{"type":"select","label":"Platform","options":["Web","Android","Both"]},{"type":"radio","label":"Risk","options":["Low","Medium","High"]},{"type":"slider","label":"Rollout %","min":0,"max":100,"value":10,"unit":"%"},{"type":"date","label":"Target date"},{"type":"toggle","label":"Needs design review"},{"type":"email","label":"Reviewer","value":"dev@grapsee.com"},{"type":"rating","label":"Excitement","max":5}],"palette":"aurora","accent":"#4C6FFF","variant":"inset"}
\`\`\`

\`\`\`rating
{"title":"Rate this drop","prompt":"How does the v2 library feel?","style":"star","max":5,"labels":["rough","stunning"],"palette":"berry","accent":"#D6336C","variant":"inset"}
\`\`\`

\`\`\`poll
{"title":"Next drop","question":"Which surface should get the extra polish?","options":[{"label":"Maps","votes":14},{"label":"Calculators","votes":9},{"label":"App shells","votes":17},{"label":"Charts","votes":22}],"palette":"sunrise","accent":"#F08C00","variant":"tinted"}
\`\`\`

\`\`\`checklist
{"title":"Your launch list","items":[{"label":"Pin the palette","detail":"one palette per reply","done":true},{"label":"Name the accents","done":true},{"label":"Keep prose between blocks","done":false},{"label":"Never quote a component count","done":false}],"palette":"forest","accent":"#66A80F","variant":"outline"}
\`\`\`

\`\`\`chips
{"title":"Filter the demo","options":[{"label":"Charts","prompt":"Show me chart examples"},{"label":"Apps","prompt":"Show me app examples"},{"label":"Inputs","prompt":"Show me input examples"}],"multi":false,"palette":"reef","accent":"#1098AD","variant":"outline"}
\`\`\`

\`\`\`menu
{"title":"Where to next?","options":[{"label":"Deep-dive charts","detail":"All 28 types, live","prompt":"Show me all charts"},{"label":"Open the app shells","detail":"Kanban, wizard, calendar, timer…","prompt":"Show me the apps"},{"label":"Stress the calculator","detail":"Sliders + live formulas","prompt":"Show me the calculators"}],"palette":"dusk","accent":"#9C36B5","variant":"outline"}
\`\`\``,
  },
  {
    id: 'layout',
    name: 'Layout surfaces',
    md: `## Tabs, accordions, steps, trees, flashcards, diffs

\`\`\`tabs
{"title":"One topic, three facets","tabs":[{"label":"Summary","body":"The v3.1 library covers 190 declared component types — every kind, sub-type and control the catalogue names, rendered natively on web and Android."},{"label":"For the model","body":"The prompt carries the full vocabulary, a palette system, and a hard ban on capability self-doubt. The compiler enforces the art direction."},{"label":"For clients","body":"Web renders this library; Android renders its Compose twin from the same wire format."}],"palette":"harbour","accent":"#0B7285","variant":"plain"}
\`\`\`

\`\`\`accordion
{"title":"Design questions","sections":[{"label":"Why palettes instead of free colour?","body":"A reply reads as one designed object when its hues come from one family. Free colour produced the rainbow-and-grey-card era.","expanded":true},{"label":"Can the model still pick any hue?","body":"Yes — an explicit accent always wins, as long as it does not repeat the block above."},{"label":"What about dark mode?","body":"Every surface has a dark counterpart; the tinted wash flips from accent-on-light to accent-on-dark automatically."}],"palette":"orchid","accent":"#9C36B5","variant":"outline"}
\`\`\`

\`\`\`steps
{"title":"Rollout plan","steps":[{"title":"Catalogue v3","body":"35 kinds, 190 component types, palettes","state":"done"},{"title":"Web GenUI library","body":"sims, 3D stages, canvases, editors, terminal","state":"done"},{"title":"Android v2 renderers","body":"new kinds in Compose","state":"current"},{"title":"Measure","body":"parse-rate + art-direction metrics","state":"todo"}],"palette":"meadow","accent":"#0CA678","variant":"editorial"}
\`\`\`

\`\`\`tree
{"title":"The catalogue","roots":[{"label":"Data surfaces","children":[{"label":"chart","detail":"28 types"},{"label":"stat"},{"label":"table"},{"label":"compare"},{"label":"timeline"},{"label":"gauge"}]},{"label":"Interactive","children":[{"label":"quiz"},{"label":"form","detail":"17 field types"},{"label":"calc","detail":"14 engines"},{"label":"rating"},{"label":"poll"},{"label":"checklist"}]},{"label":"Apps","children":[{"label":"wizard"},{"label":"kanban"},{"label":"calendar"},{"label":"timer"},{"label":"+16 more"}]}],"palette":"glacier","accent":"#4C6FFF","variant":"editorial"}
\`\`\`

\`\`\`flashcards
{"title":"GenUI flashcards","cards":[{"front":"What is the fence grammar?","back":"Three backticks + kind on the opening line, strict JSON body."},{"front":"Who picks the accent?","back":"The model first; the compiler only breaks ties."},{"front":"What renders mid-stream?","back":"A skeleton — never raw JSON."}],"palette":"citrus","accent":"#F08C00","variant":"inset"}
\`\`\`

\`\`\`diff
{"title":"The 18-kind bug, fixed","language":"ts","before":"const WIDGET_KINDS = [chart, stat, table, compare, progress, timeline, gauge, quiz, form, carousel, cards, followups, media, actions, panel, voice, calc, app]","after":"const WIDGET_KINDS = [ ...18 originals, tabs, accordion, steps, menu, chips, rating, poll, diff, tree, flashcards, checklist ]","palette":"ember","accent":"#E03131","variant":"plain"}
\`\`\``,
  },
  {
    id: 'apps',
    name: 'App experiences',
    md: `## Real state, real interactions

\`\`\`kanban
{"type":"kanban","title":"Sprint board","columns":[{"title":"Todo","items":[{"title":"Palette rotation","subtitle":"art-direct","badge":"P1"},{"title":"Mermaid themes","badge":"P2"}]},{"title":"Doing","items":[{"title":"Sankey layout","subtitle":"web"}]},{"title":"Done","items":[{"title":"Catalogue v2"},{"title":"90 tests"}]}],"palette":"orchid","accent":"#6741D9","variant":"inset"}
\`\`\`

\`\`\`wizard
{"type":"wizard","title":"Create a release","steps":[{"title":"Basics","body":"What is shipping?","fields":[{"type":"text","label":"Name"},{"type":"select","label":"Channel","options":["canary","beta","stable"]}]},{"title":"Rollout","body":"Who sees it first?","fields":[{"type":"slider","label":"Percentage"},{"type":"radio","label":"Audience","options":["Internal","Beta","Everyone"]}]},{"title":"Confirm","body":"Ready?","fields":[{"type":"toggle","label":"I checked the palette"}]}],"palette":"aurora","accent":"#4C6FFF","variant":"inset"}
\`\`\`

\`\`\`booking
{"type":"booking","title":"Design review","slots":["09:00","10:30","12:00","14:00","15:30","17:00"],"submitLabel":"Book it","palette":"reef","accent":"#1098AD","variant":"inset"}
\`\`\`

\`\`\`configurator
{"type":"configurator","title":"Build your plan","groups":[{"title":"Tier","options":[{"label":"Solo","price":0},{"label":"Team","price":12},{"label":"Business","price":39}]},{"title":"Add-ons","options":[{"label":"None","price":0},{"label":"Priority render","price":6},{"label":"Dedicated model","price":18}]}],"palette":"citrus","accent":"#E8590C","variant":"inset"}
\`\`\`

\`\`\`timer
{"type":"timer","title":"Focus block","items":[{"title":"minutes","price":25}],"palette":"sunrise","accent":"#E03131","variant":"inset"}
\`\`\`

\`\`\`calendar
{"type":"calendar","title":"October","month":"2026-10","items":[{"date":"2026-10-20","title":"GenUI v2 ships"},{"date":"2026-10-24","title":"Android sync"}],"palette":"dusk","accent":"#5F3DC4","variant":"inset"}
\`\`\`

\`\`\`sheet
{"type":"sheet","title":"Edit me — sums are live","rows":[["Item","Cost"],["Charts","0"],["Widgets","0"],["Coffee","4"]],"palette":"harbour","accent":"#0B7285","variant":"plain"}
\`\`\``,
  },
  {
    id: 'calc',
    name: 'Calculators',
    md: `## Live formulas

\`\`\`calc
{"type":"loan","title":"Loan payment","fields":[{"key":"p","label":"Principal","value":250000,"unit":"$","min":10000,"max":1000000,"step":5000},{"key":"r","label":"Rate (annual %)","value":6.5,"unit":"%","min":1,"max":18,"step":0.1},{"key":"n","label":"Years","value":30,"min":5,"max":40,"step":1}],"outputs":[{"label":"Monthly payment","expr":"p*(r/100/12)*pow(1+r/100/12,n*12)/(pow(1+r/100/12,n*12)-1)","unit":"$","format":"money","primary":true},{"label":"Total interest","expr":"p*(r/100/12)*pow(1+r/100/12,n*12)/(pow(1+r/100/12,n*12)-1)*n*12-p","unit":"$","format":"money"}],"note":"Drag the sliders — everything recomputes locally.","presets":[{"label":"15-year","values":{"n":15}},{"label":"30-year","values":{"n":30}}],"palette":"citrus","accent":"#E8590C","variant":"inset"}
\`\`\`

\`\`\`calc
{"type":"health","title":"BMI","fields":[{"key":"w","label":"Weight","value":72,"unit":"kg","min":35,"max":160,"step":1},{"key":"h","label":"Height","value":172,"unit":"cm","min":130,"max":210,"step":1}],"outputs":[{"label":"BMI","expr":"w/pow(h/100,2)","format":"","primary":true},{"label":"Healthy range","expr":"18.5*pow(h/100,2)","unit":"kg"}],"note":"Illustrative, not medical advice.","palette":"forest","accent":"#0CA678","variant":"inset"}
\`\`\``,
  },
  {
    id: 'blocks',
    name: 'Native blocks',
    md: `## Mermaid, maps, bespoke html, svg

\`\`\`mermaid
flowchart LR
  U[User] --> P{Prompt}
  P -->|visual ask| W[Widget fence]
  P -->|anything else| T[Prose]
  W --> V[Validator]
  V -->|ok| C[Compiler + art director]
  V -->|broken| R[Repair]
  R --> C
  C --> UI[Inline native render]
\`\`\`

\`\`\`map
{"title":"Two offices","center":[23.8103,90.4125],"zoom":11,"pins":[{"lat":23.8103,"lng":90.4125,"label":"Dhaka HQ"},{"lat":22.3569,"lng":91.7832,"label":"Chattogram"}],"cards":true,"palette":"reef","accent":"#1098AD"}
\`\`\`

A bespoke interactive — the escape hatch:

\`\`\`html
<div style="font-family:inherit">
  <h3 style="margin:0 0 8px;color:var(--gs-text)">Mini pendulum</h3>
  <canvas id="p" width="320" height="180" style="background:var(--gs-surface);border-radius:12px;border:1px solid var(--gs-border)"></canvas>
  <p style="color:var(--gs-text-3);font-size:12px;margin:8px 0 0">Bespoke HTML/JS — physics the catalogue does not name.</p>
</div>
<script>
  const c = document.getElementById('p'), x = c.getContext('2d');
  let a = 0.6, v = 0, t = 0;
  function loop() {
    t += 0.016; v += (-9.8 * Math.sin(a) * 0.016); v *= 0.995; a += v * 0.016;
    x.clearRect(0, 0, 320, 180);
    x.strokeStyle = getComputedStyle(document.documentElement).getPropertyValue('--gs-accent') || '#2DD4A8';
    x.lineWidth = 2;
    x.beginPath(); x.moveTo(160, 20);
    x.lineTo(160 + 110 * Math.sin(a), 20 + 110 * Math.cos(a)); x.stroke();
    x.fillStyle = x.strokeStyle;
    x.beginPath(); x.arc(160 + 110 * Math.sin(a), 20 + 110 * Math.cos(a), 11, 0, 7); x.fill();
    requestAnimationFrame(loop);
  }
  loop();
</script>
\`\`\`

\`\`\`svg
<svg viewBox="0 0 120 60" width="220" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="GS wordmark">
  <rect x="2" y="12" width="44" height="36" rx="10" fill="var(--gs-accent)"/>
  <text x="24" y="37" text-anchor="middle" font-size="20" font-weight="800" fill="#0F1216">G</text>
  <text x="56" y="38" font-size="20" font-weight="700" fill="var(--gs-text)">GenUI</text>
  <text x="56" y="50" font-size="8" fill="var(--gs-text-3)">190 components, one wire format</text>
</svg>
\`\`\``,
  },
  {
    id: 'streaming',
    name: 'Streaming',
    md: `## This answer is arriving right now

Counting the fences as they land:

\`\`\`progress
{"title":"Drop rollout","items":[{"label":"Catalogue","percent":100},{"label":"Web library","percent":100},{"label":"Android v2","percent":45},{"label":"Parse-rate eval","percent":20}],"palette":"meadow","accent":"#66A80F","variant":"outline"}
\`\`\`

\`\`\`stat
{"title":"The count that matters","cards":[{"label":"Declared component types","value":"190"},{"label":"Kinds","value":"35","delta":"+6","direction":"up"},{"label":"Chart types","value":"34","delta":"+6","direction":"up"},{"label":"Live simulations","value":"8","delta":"new","direction":"up"}],"palette":"aurora","accent":"#4C6FFF","variant":"hero"}
\`\`\`

\`\`\`gauge
{"title":"Library completeness","items":[{"label":"Kinds rendered on web","value":29,"max":29,"unit":""},{"label":"Charts rendered","value":28,"max":28,"unit":""},{"label":"Apps rendered","value":20,"max":20,"unit":""}],"palette":"berry","accent":"#9C36B5","variant":"hero"}
\`\`\`

…and a follow-up affordance:

\`\`\`followups
{"prompts":["Show me every chart type","Build me a pricing calculator","Render a kanban board","Draw the pipeline in mermaid"]}
\`\`\``,
  },
  {
    id: 'sims',
    name: 'Live simulations',
    md: `## Physics you can poke — eight real-time simulations

Verlet cloth — drag to tear it:

\`\`\`cloth
{"title":"Silk drop","gravity":9.8,"speed":1,"damping":0.99,"count":64,"size":72,"seed":4,"interactive":true,"palette":"orchid","accent":"#9C36B5","variant":"inset"}
\`\`\`

Smoothed-particle fluid:

\`\`\`fluid
{"title":"Water tank","gravity":12,"count":80,"size":64,"seed":9,"interactive":true,"palette":"reef","accent":"#1098AD","variant":"inset"}
\`\`\`

Fireworks — tap the stage to launch:

\`\`\`particles
{"title":"Particle fountain","gravity":-14,"count":90,"speed":1.2,"seed":11,"interactive":true,"palette":"ember","accent":"#E03131","variant":"hero"}
\`\`\`

And a playable arcade game — arrows or swipe, best score sticks:

\`\`\`game
{"title":"Dodge","count":7,"speed":1.1,"size":64,"seed":6,"interactive":true,"palette":"citrus","accent":"#F08C00","variant":"inset"}
\`\`\``, 
  },
  {
    id: 'stage3d',
    name: '3D stage',
    md: `## Real WebGL — ball-and-stick, heightfields, scatter clouds

A molecule you can orbit and zoom:

\`\`\`molecule
{"title":"Caffeine","nodes":[{"id":"C1","label":"C","x":0,"y":0,"z":0,"r":0.7,"group":"C"},{"id":"N1","label":"N","x":1.4,"y":0.4,"z":0.2,"r":0.65,"group":"N"},{"id":"O1","label":"O","x":-0.6,"y":1.3,"z":0.4,"r":0.6,"group":"O"},{"id":"O2","label":"O","x":-1.1,"y":-1.1,"z":-0.3,"r":0.6,"group":"O"},{"id":"C2","label":"C","x":1.1,"y":-1.2,"z":0.1,"r":0.7,"group":"C"},{"id":"C3","label":"C","x":2.6,"y":-0.1,"z":-0.2,"r":0.7,"group":"C"},{"id":"N2","label":"N","x":0.3,"y":2.4,"z":-0.5,"r":0.65,"group":"N"}],"bonds":[[0,1],[0,2],[0,3],[0,4],[1,5],[2,6]],"palette":"forest","accent":"#0CA678","variant":"hero","spin":true}
\`\`\`

A procedural heightfield — z is a real expression over x and y:

\`\`\`surface3d
{"title":"Ripple field — z = sin(x)*cos(y)","z":"sin(x)*cos(y)","span":6,"palette":"harbour","accent":"#0B7285","variant":"hero"}
\`\`\`

3D scatter:

\`\`\`scatter3d
{"title":"Customers in product space","nodes":[{"label":"Basic","x":-2.1,"y":0.4,"z":1.2,"r":0.3,"group":"free"},{"label":"Plus","x":0.2,"y":-1.3,"z":0.4,"r":0.4,"group":"paid"},{"label":"Pro","x":1.8,"y":1.1,"z":-0.8,"r":0.5,"group":"paid"},{"label":"Team","x":2.9,"y":-0.2,"z":1.6,"r":0.6,"group":"enterprise"},{"label":"Trial","x":-0.8,"y":1.9,"z":-1.4,"r":0.25,"group":"free"},{"label":"Flagship","x":3.4,"y":2.2,"z":0.2,"r":0.7,"group":"enterprise"}],"palette":"dusk","accent":"#5F3DC4","variant":"hero"}
\`\`\`

The magic window — VR/AR framing of the same scene graph:

\`\`\`ar
{"title":"Showroom, camera passthrough frame","nodes":[{"label":"Sofa","x":0,"y":-0.5,"z":0,"r":0.8,"group":"furniture"},{"label":"Table","x":1.6,"y":-0.5,"z":0.4,"r":0.5,"group":"furniture"},{"label":"Lamp","x":-1.2,"y":0.2,"z":-0.4,"r":0.3,"group":"decor"}],"bonds":[[0,1]],"palette":"sunrise","accent":"#F08C00","variant":"hero"}
\`\`\``, 
  },
  {
    id: 'canvas',
    name: 'Canvas surfaces',
    md: `## Draw, branch, connect, measure — four pointer surfaces

Freehand whiteboard with pen + eraser:

\`\`\`whiteboard
{"title":"Sketch it out","palette":"berry","accent":"#D6336C","variant":"inset"}
\`\`\`

Draggable mind map — tap + to branch:

\`\`\`mindmap
{"title":"Q4 launch map","nodes":[{"id":0,"label":"Launch","x":0.5,"y":0.2},{"id":1,"label":"Pricing","x":0.22,"y":0.55,"group":"go"},{"id":2,"label":"Press","x":0.5,"y":0.62,"group":"go"},{"id":3,"label":"Analytics","x":0.78,"y":0.5,"group":"post"}],"edges":[[0,1],[0,2],[0,3]],"palette":"meadow","accent":"#66A80F","variant":"inset"}
\`\`\`

Flowchart editor — drag nodes, tap two to connect:

\`\`\`diagram
{"title":"Signup flow","nodes":[{"id":0,"label":"Start","x":0.12,"y":0.5},{"id":1,"label":"Email?","x":0.38,"y":0.5},{"id":2,"label":"Verify","x":0.65,"y":0.3},{"id":3,"label":"Login","x":0.65,"y":0.72},{"id":4,"label":"Done","x":0.88,"y":0.5}],"edges":[[0,1]],"palette":"glacier","accent":"#4C6FFF","variant":"inset"}
\`\`\`

Draw zones over a live map, area computed:

\`\`\`polygons
{"title":"Delivery zone","palette":"citrus","accent":"#E8590C","variant":"inset"}
\`\`\``, 
  },
  {
    id: 'editors',
    name: 'App editors',
    md: `## Seven editors that actually compute

Spreadsheet — tap cells, formulas evaluate live (SUM/AVG/MIN/MAX/COUNT, A1 refs, cycle guard):

\`\`\`editor
{"editor":"sheet","title":"Margin model","cells":{"B1":"120","B2":"180","B3":"95","B4":"=SUM(B1:B3)","B5":"=AVG(B1:B3)","C1":"=B4*1.2","C2":"=B5-MIN(B1:B3)"},"palette":"money","accent":"#E8590C","variant":"plain"}
\`\`\`

Visual programming — wire nodes, numbers flow through:

\`\`\`nodes
{"title":"Signal chain","nodes":[{"id":0,"label":"A","kind":"input","x":0.1,"y":0.3,"value":6},{"id":1,"label":"B","kind":"input","x":0.1,"y":0.7,"value":9},{"id":2,"label":"A+B","kind":"add","x":0.45,"y":0.5},{"id":3,"label":"x2","kind":"multiply","x":0.75,"y":0.5},{"id":4,"label":"Out","kind":"output","x":0.92,"y":0.5}],"edges":[[0,2],[1,2],[2,3],[3,4]],"palette":"glacier","accent":"#1098AD","variant":"plain"}
\`\`\`

Pivot builder — real grouping + aggregation:

\`\`\`pivot
{"title":"Revenue by region","rows":[["Region","Product","Revenue"],["Dhaka","Pro","420"],["Chattogram","Basic","180"],["Dhaka","Basic","260"],["Sylhet","Pro","310"],["Chattogram","Pro","290"],["Dhaka","Team","510"],["Sylhet","Basic","120"]],"palette":"money","accent":"#F08C00","variant":"plain"}
\`\`\`

Site builder — drag rows, live preview:

\`\`\`site
{"title":"Landing page","blocks":[{"type":"hero","text":"Launch day"},{"type":"text","text":"Everything ships today."},{"type":"image","text":"banner"},{"type":"button","text":"Get started"}],"palette":"orchid","accent":"#6741D9","variant":"plain"}
\`\`\`

CSS layout editor — flip flex props, code emits live:

\`\`\`css
{"title":"Flexbox playground","layout":{"direction":"row","gap":16,"justify":"space-between","align":"center","wrap":"wrap"},"palette":"reef","accent":"#0B7285","variant":"plain"}
\`\`\`

Clip timeline with running playhead:

\`\`\`editor
{"editor":"video","title":"Launch film","duration":30,"tracks":[{"name":"Video A","clips":[{"start":0,"dur":8,"label":"Intro"},{"start":10,"dur":12,"label":"Demo"}]},{"name":"Audio","clips":[{"start":0,"dur":20,"label":"Voiceover"}]}],"palette":"ember","accent":"#D6336C","variant":"plain"}
\`\`\`

Audio — waveform + spectrogram + real WebAudio playback, tap notes to retune:

\`\`\`editor
{"editor":"audio","title":"Chime sequencer","palette":"berry","accent":"#9C36B5","variant":"plain"}
\`\`\``, 
  },
  {
    id: 'terminal',
    name: 'Terminal',
    md: `## A sandboxed shell in chat — toy filesystem + JS REPL

Try \`ls\`, \`cat notes.txt\`, \`js [1,2,3].map(x => x*x)\`, arrow-up for history:

\`\`\`terminal
{"title":"guest@gs","welcome":"guest shell — type help","commands":[{"name":"deploy","description":"staged deploy plan (demo)"},{"name":"metrics","description":"print the live counters"}],"palette":"forest","accent":"#0CA678","variant":"outline"}
\`\`\``, 
  },
  {
    id: 'collab',
    name: 'Shared state (Tier 2)',
    md: `## LIVE shared widgets — everyone viewing this page edits the same state

All four widgets below share the room \`gs-launch\` — tap in one, watch the others' counts, votes and cursors move.

Shared counter — server-side deltas, concurrent taps never lose state:

\`\`\`counter
{"surface":"counter","room":"gs-launch","title":"Release taps","label":"this number is the same for everyone, right now","start":0,"palette":"forest","accent":"#0CA678","variant":"hero"}
\`\`\`

Live vote — one vote per option per viewer, counts aggregate live:

\`\`\`vote
{"surface":"vote","room":"gs-launch","title":"Ship date","items":["Ship Monday","Ship Friday","Add a week of polish"],"palette":"glacier","accent":"#4C6FFF","variant":"hero"}
\`\`\`

Shared todo list — list-replace, last-write-wins (the widget says so):

\`\`\`todo
{"surface":"todo","room":"gs-launch","title":"Launch checklist","items":["Read the answer","Tap a button","See presence dots"],"palette":"citrus","accent":"#E8590C","variant":"inset"}
\`\`\`

Live cursors — presence only, nothing stored, cursors expire 30s after movement stops:

\`\`\`pointer
{"surface":"pointer","room":"gs-launch","title":"Who's here","palette":"orchid","accent":"#9C36B5","variant":"hero"}
\`\`\``, 
  },
]


export default function GenUIGallery() {
  const [active, setActive] = useState(SECTIONS[0].id)
  const [palette, setPalette] = useState<string>('as-emitted')
  const [raw, setRaw] = useState(false)

  const section = SECTIONS.find((s) => s.id === active) ?? SECTIONS[0]

  return (
    <div style={{ background: C.bg, color: C.text, minHeight: '100vh', fontFamily: "-apple-system, 'SF Pro Text', 'Segoe UI', Roboto, sans-serif" }}>
      <header style={{ padding: '28px 20px 18px', borderBottom: `1px solid ${C.outline}`, position: 'sticky', top: 0, background: `${C.bg}F2`, backdropFilter: 'blur(8px)', zIndex: 10 }}>
        <div style={{ maxWidth: 720, margin: '0 auto' }}>
          <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
            <h1 style={{ fontSize: 24, fontWeight: 800, margin: 0 }}>
              GenUI Gallery <span style={{ color: C.accent }}>— {SECTIONS.length} live sections, 190 component types</span>
            </h1>
            <button
              onClick={() => setRaw((r) => !r)}
              style={{ padding: '7px 14px', borderRadius: 999, border: `1px solid ${C.outline}`, background: 'transparent', color: C.muted, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}
            >
              {raw ? 'Show rendered' : 'Show raw wire format'}
            </button>
          </div>
          <p style={{ color: C.muted, fontSize: 13, margin: '8px 0 0', lineHeight: 1.6 }}>
            Every block below is the real pipeline: fence → validate → compile → art-direct → render. Palette override re-themes every widget in the section.
          </p>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 12 }}>
            <PaletteChip label="as emitted" id="as-emitted" active={palette === 'as-emitted'} onClick={() => setPalette('as-emitted')} />
            {PALETTES.map((p) => (
              <PaletteChip key={p.id} label={p.name} id={p.id} hues={p.hues} active={palette === p.id} onClick={() => setPalette(p.id)} />
            ))}
          </div>
          <nav style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 12 }}>
            {SECTIONS.map((s) => (
              <button
                key={s.id}
                onClick={() => setActive(s.id)}
                style={{
                  padding: '7px 13px',
                  borderRadius: 999,
                  fontSize: 12,
                  fontWeight: 600,
                  cursor: 'pointer',
                  border: `1px solid ${active === s.id ? C.accent : C.outline}`,
                  background: active === s.id ? 'rgba(45,212,168,0.14)' : 'transparent',
                  color: active === s.id ? C.accent : C.muted,
                }}
              >
                {s.name}
              </button>
            ))}
          </nav>
        </div>
      </header>

      <main style={{ maxWidth: 720, margin: '0 auto', padding: '24px 20px 80px' }}>
        {raw ? (
          <pre style={{ whiteSpace: 'pre-wrap', fontSize: 12, lineHeight: 1.7, padding: 18, borderRadius: 14, border: `1px solid ${C.outline}`, background: C.surface, color: C.muted, overflowX: 'auto' }}>
            {section.md}
          </pre>
        ) : (
          <GalleryBody md={section.md} palette={palette} />
        )}
      </main>
    </div>
  )
}

function GalleryBody({ md, palette }: { md: string; palette: string }) {
  // The palette override rewrites the wire format itself — proof that the
  // palette is a single key that re-faces a whole reply.
  const themed = palette === 'as-emitted' ? md : md.replace(/"palette":"[a-z]+"/g, `"palette":"${palette}"`)
  return <MessageContent text={themed} dark onPrompt={(prompt) => alert(`The model would receive: "${prompt}"`)} />
}

function PaletteChip({ label, id, hues, active, onClick }: { label: string; id: string; hues?: readonly [string, string, string]; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      title={id}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        padding: '5px 11px',
        borderRadius: 999,
        fontSize: 11,
        fontWeight: 600,
        cursor: 'pointer',
        border: `1px solid ${active ? C.accent : C.outline}`,
        background: active ? 'rgba(45,212,168,0.12)' : 'transparent',
        color: active ? C.accent : C.muted,
      }}
    >
      {hues ? (
        <span style={{ display: 'inline-flex' }}>
          {hues.map((h) => (
            <span key={h} style={{ width: 8, height: 8, borderRadius: 999, background: h, marginLeft: -2, border: '1px solid #0A0D12' }} />
          ))}
        </span>
      ) : null}
      {label}
    </button>
  )
}
