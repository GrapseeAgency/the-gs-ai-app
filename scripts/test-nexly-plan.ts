/**
 * Nexly response/icon contract checks. Run with: bun run test:nexly
 */
import { strict as assert } from 'node:assert'
import { NEXLY_DEMO_PLAN, NEXLY_DEMO_WIRE } from '../src/lib/nexly/demo'
import {
  canonicalIconName,
  emojiToIconTokens,
  glassToneFor,
  ICON_GUIDE_GROUPS,
  iconNameFor,
  NEXLY_ICON_COUNT,
  NEXLY_ICON_GUIDE,
} from '../src/lib/nexly/icons'
import {
  isNexlyPlanPayload,
  NEXLY_PLAN_PROMPT,
  parseNexlyPlan,
  parseNexlyReply,
} from '../src/lib/nexly/plan'
import { extractWidgetFences, inferWidgetKind } from '../src/lib/widgets/validate'
import { WIDGET_PROMPT } from '../src/lib/widgets/prompt'

assert.ok(NEXLY_ICON_COUNT >= 2000, 'the glass vocabulary must exceed 2,000 glyphs')
assert.equal(NEXLY_ICON_COUNT, 5184, 'Tabler outline manifest changed; regenerate the sprite')

assert.equal(canonicalIconName('study'), 'book-2')
assert.equal(canonicalIconName('book-open'), 'book-2')
assert.equal(canonicalIconName('device-laptop'), 'device-laptop')
assert.equal(iconNameFor('not-a-real-icon'), 'sparkles')

// The prompt guide is a promise to the model: every name in it must resolve
// exactly, or the plan silently falls back to a sparkle.
for (const group of ICON_GUIDE_GROUPS) {
  for (const name of group.names) {
    assert.equal(canonicalIconName(name), name, `icon guide name does not resolve: ${name}`)
  }
}
assert.ok(NEXLY_ICON_GUIDE.includes('sunrise'))
assert.equal(glassToneFor('sunrise'), 'amber')
assert.equal(glassToneFor('book-2'), 'lavender')
assert.equal(glassToneFor('device-laptop'), 'blue')
assert.equal(glassToneFor('tools-kitchen-2'), 'green')
assert.equal(glassToneFor('run'), 'pink')
assert.equal(glassToneFor('device-gamepad-2'), 'blue')
assert.equal(glassToneFor('moon'), 'lilac')
assert.equal(glassToneFor('book-2', 'blue'), 'blue', 'an explicit tone must win')
assert.ok(NEXLY_PLAN_PROMPT.includes(NEXLY_ICON_GUIDE), 'prompt must carry the icon guide')
assert.ok(NEXLY_PLAN_PROMPT.includes('tone: optional'), 'prompt must explain the semantic tones')
assert.ok(NEXLY_PLAN_PROMPT.includes('"tone": "amber"'))
assert.ok(NEXLY_PLAN_PROMPT.includes('```plan'))
assert.ok(NEXLY_PLAN_PROMPT.includes('5–9'))
assert.ok(WIDGET_PROMPT.includes('THE NEXLY PLAN GRAMMAR'), 'the live widget prompt must include the plan grammar')
assert.ok(WIDGET_PROMPT.includes(NEXLY_ICON_GUIDE), 'the live widget prompt must carry the icon guide')

assert.ok(NEXLY_DEMO_PLAN.rows.length === 9)
for (const row of NEXLY_DEMO_PLAN.rows) {
  assert.ok(canonicalIconName(row.icon), `unknown plan icon: ${row.icon}`)
}

const parsed = parseNexlyPlan(JSON.parse(JSON.stringify(NEXLY_DEMO_PLAN)))
assert.ok(parsed)
assert.equal(parsed?.rows.length, 9)
assert.equal(parsed?.rows[0]?.time, '8:00 – 8:30 AM')
assert.equal(parsed?.closingIcon, 'flame')

// Models sometimes wrap the payload or use the synonyms the prompt allows.
const wrapped = parseNexlyPlan({
  schedule: {
    heading: 'Sunday',
    items: [
      { start: '8:00 AM', activity: 'Run', icon: 'run' },
      { from: '9:00 AM', task: 'Study', icon: 'book-2' },
    ],
    footer: 'Go get it',
    timestamp: '8:00 PM',
  },
})
assert.ok(wrapped, 'wrapped schedule payload must parse')
assert.equal(wrapped?.title, 'Sunday')
assert.equal(wrapped?.rows.length, 2)
assert.equal(wrapped?.rows[0]?.text, 'Run')
assert.equal(wrapped?.closing, 'Go get it')

assert.equal(isNexlyPlanPayload({ rows: [{ time: '9:00', text: 'Class' }] }), true)
assert.equal(isNexlyPlanPayload({ rows: [{ label: 'Revenue', value: 10 }] }), false)
assert.equal(isNexlyPlanPayload({ rows: [{ time: '9:00' }] }), false)
assert.equal(parseNexlyPlan({ rows: [{ time: '9:00' }] }), null)
assert.equal(
  parseNexlyPlan({ rows: [{ time: '9:00', text: '**Classes** and `notes`' }] })?.rows[0]?.text,
  'Classes and notes'
)
assert.equal(inferWidgetKind(JSON.stringify(NEXLY_DEMO_PLAN)), 'plan')
assert.equal(inferWidgetKind(JSON.stringify({ rows: [{ label: 'Revenue', value: 10 }] })), null)

// Natural fence names canonicalise to the plan card when the payload is timed.
const timedRows = JSON.stringify({ rows: [{ time: '8:00', icon: 'sunrise', text: 'Run' }] })
for (const lang of ['plan', 'schedule', 'timetable', 'routine', 'day']) {
  const fences = extractWidgetFences('```' + lang + '\n' + timedRows + '\n```')
  assert.equal(fences[0]?.kind, 'plan', `${lang} fence must canonicalise to plan`)
}
// A real interactive schedule app keeps its own kind.
const scheduleApp = extractWidgetFences('```schedule\n{"items":[{"label":"Mon 9:00"}]}\n```')
assert.equal(scheduleApp[0]?.kind, 'app')

const wire = parseNexlyReply(NEXLY_DEMO_WIRE)
assert.ok(wire.plan)
assert.equal(wire.plan?.rows.length, 9)
assert.ok(wire.preamble.includes('balanced plan'))
assert.ok(!/\p{Extended_Pictographic}/u.test(wire.preamble), 'preamble must not contain emoji')

const normalised = emojiToIconTokens('You got this, Arafat! 💪')
assert.ok(normalised.includes(':flame:'), 'known emoji must become an icon token')
assert.ok(!/\p{Extended_Pictographic}/u.test(normalised), 'no pictograph may survive normalisation')
assert.equal(emojiToIconTokens('🦄'), '')
assert.equal(emojiToIconTokens('🏃‍♂️'), ':run:')

assert.ok(!/\p{Extended_Pictographic}/u.test(JSON.stringify(NEXLY_DEMO_PLAN)))

console.log('Nexly contract OK')
console.log(`  ${NEXLY_ICON_COUNT.toLocaleString('en-US')} glass glyphs`)
console.log(`  ${NEXLY_DEMO_PLAN.rows.length} plan rows`)
console.log('  0 emoji in demo wire')
