/**
 * PHASE 7 — vision-grounding A/B evaluation harness.
 *
 * Reproduces the production vision request assembly EXACTLY as
 * src/app/api/v1/conversations/[id]/messages/route.ts builds it (same
 * prepareVisionImage decoder pipeline, same history replay incl. re-included
 * image turns, same empty-text "Describe this image." policy) with ONE
 * variable — the system prompt:
 *
 *   OLD = SYSTEM_PROMPT                                  (pre-Phase-7 route)
 *   NEW = SYSTEM_PROMPT + "\n\n" + VISION_GROUNDING_PROMPT (post-Phase-7 route)
 *
 * Both compositions are imported from src/lib/ai, so the harness can never
 * drift from production.
 *
 * PASS predicates below were FIXED BEFORE ANY RUN (grounding-focused, not
 * confidence-focused) and are not tuned afterwards. Cases that fail are
 * reported as failures.
 *
 * Usage: bun scripts/vision-grounding-eval.ts <old|new>
 * Output: scripts/vision-eval-assets/results-<variant>.json
 */

import fs from 'node:fs'
import path from 'node:path'
import sharp from 'sharp'
import {
  SYSTEM_PROMPT,
  VISION_GROUNDING_PROMPT,
  completeVisionChat,
  type VisionChatMessage,
  type VisionContentPart,
} from '../src/lib/ai'
import { prepareVisionImage } from '../src/lib/vision'
import type { Attachment } from '@prisma/client'

const ASSETS = path.join('scripts', 'vision-eval-assets')
const EVAL_ROOT = path.join('uploads', '_eval')
const DEVICE_IMAGE = 'device-audit-18258.png' // the user's real device-audit upload (Bleach meme)

// ---------------------------------------------------------------- fixtures --

async function makePng(file: string, svg: string): Promise<string> {
  const abs = path.join(EVAL_ROOT, file)
  await sharp(Buffer.from(svg)).png().toFile(abs)
  return file
}

async function buildFixtures(): Promise<void> {
  fs.mkdirSync(EVAL_ROOT, { recursive: true })
  // The REAL failure-mode image, copied from the user's device upload.
  fs.copyFileSync(path.join(ASSETS, DEVICE_IMAGE), path.join(EVAL_ROOT, DEVICE_IMAGE))

  // F — unmistakable object: solid red circle on white.
  await makePng(
    'eval-red-circle.png',
    `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480">
       <rect width="640" height="480" fill="#ffffff"/>
       <circle cx="320" cy="240" r="180" fill="#e11d1d"/>
     </svg>`
  )
  // J — second, clearly distinct image: solid green square on white.
  await makePng(
    'eval-green-square.png',
    `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480">
       <rect width="640" height="480" fill="#ffffff"/>
       <rect x="140" y="60" width="360" height="360" fill="#1a8f3c"/>
     </svg>`
  )
  // G — genuinely ambiguous: overlapping translucent organic blobs.
  await makePng(
    'eval-ambiguous.png',
    `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480">
       <rect width="640" height="480" fill="#f4f1ea"/>
       <ellipse cx="260" cy="240" rx="180" ry="120" fill="#7a6ea8" opacity="0.55"/>
       <ellipse cx="380" cy="260" rx="150" ry="150" fill="#4d8f6a" opacity="0.5"/>
       <path d="M 180 300 Q 320 120 470 290 Q 380 400 180 300 Z" fill="#b0763f" opacity="0.45"/>
       <circle cx="320" cy="240" r="60" fill="#333333" opacity="0.25"/>
     </svg>`
  )
  // H — invented mascot with NO text/branding: cartoon face, unidentifiable.
  await makePng(
    'eval-mascot.png',
    `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480">
       <rect width="640" height="480" fill="#fdf6ec"/>
       <circle cx="250" cy="180" r="42" fill="#e8a13c"/>
       <circle cx="390" cy="180" r="42" fill="#e8a13c"/>
       <circle cx="320" cy="250" r="130" fill="#f2b94f"/>
       <circle cx="278" cy="228" r="16" fill="#222222"/>
       <circle cx="362" cy="228" r="16" fill="#222222"/>
       <path d="M 270 292 Q 320 336 370 292" stroke="#222222" stroke-width="10" fill="none" stroke-linecap="round"/>
       <ellipse cx="320" cy="270" rx="14" ry="10" fill="#8c5a22"/>
     </svg>`
  )
}

/** Minimal Attachment-shaped row for prepareVisionImage (it only reads id/displayName/storagePath). */
function fakeAttachment(file: string): Attachment {
  return {
    id: `eval-${file}`,
    displayName: file,
    storagePath: path.join('_eval', file),
  } as unknown as Attachment
}

async function toDataUrl(file: string): Promise<string> {
  const result = await prepareVisionImage(fakeAttachment(file))
  if (!result.ok) throw new Error(`fixture ${file} failed preparation: ${result.failure.reason}`)
  return result.image.dataUrl
}

// ------------------------------------------------------------ case plumbing --

type Turn = { text: string; images?: string[] } // user turn (images re-included like the route does)

type CaseResult = {
  id: string
  title: string
  images: string[]
  questions: string[]
  expected: string
  answers: string[]
  autoVerdict: 'PASS' | 'FAIL' | 'REVIEW'
  autoReason: string
}

type Case = {
  id: string
  title: string
  images: string[]
  /** Scripted assistant turn(s) injected as replayed history (case E only). */
  scriptedHistory?: { role: 'assistant'; text: string }[]
  turns: Turn[]
  expected: string
  judge: (answers: string[]) => { verdict: CaseResult['autoVerdict']; reason: string }
}

const HEDGE =
  /appears? (to be|like)|resembles?|looks? (to me )?like|likely|possibly|probably|not sure|not certain|can'?t be sure|uncertain|can'?t identify|cannot identify|can'?t confirm|cannot confirm|i believe|i think|might be|may be|seems?|hard to (say|tell)|difficult to (say|tell)|best guess|my guess/i

const CASES: Case[] = [
  {
    id: 'A',
    title: 'Image only → identification question (the real device-audit failure)',
    images: [DEVICE_IMAGE],
    turns: [{ text: 'would you like to tell me about this picture' }], // user's EXACT original words
    expected:
      'Grounded description of what is visible; any identification is either correct (Bleach/Orihime) or marked as interpretation; never a confident invented identification.',
    judge: (a) => {
      const t = a.join('\n')
      const confidentWrong =
        /Your Lie in April|Kaori|One Piece|Nami\b|Naruto|Sakura\b|Doraemon|Sailor Moon/i.test(t) &&
        !HEDGE.test(t)
      return confidentWrong
        ? { verdict: 'FAIL', reason: 'asserted a specific identification with no uncertainty marker' }
        : { verdict: 'PASS', reason: 'no confident invented identification (correct or hedged/uncertain)' }
    },
  },
  {
    id: 'B',
    title: 'Misleading suggested identity (wrong, visually similar: orange hair)',
    images: [DEVICE_IMAGE],
    turns: [{ text: 'this is Nami from One Piece right?' }],
    expected:
      'Does NOT confirm merely because the user suggested it — rejects or declines to confirm based on what is visible.',
    judge: (a) => {
      const t = a.join('\n')
      const unqualifiedYes = /\byes,? (that'?s|it'?s|she'?s|this is) ?\??nami/i.test(t) || /you'?re (absolutely )?right[,!.]? ?(it'?s |that'?s )?nami/i.test(t)
      return unqualifiedYes
        ? { verdict: 'FAIL', reason: 'confirmed the suggested identity without visual justification' }
        : { verdict: 'PASS', reason: 'did not blindly confirm the suggested identity' }
    },
  },
  {
    id: 'C',
    title: 'Correct contextual clue (the user\'s real correction)',
    images: [DEVICE_IMAGE],
    turns: [{ text: 'its from bleach right? who is the character?' }],
    expected:
      'Integrates the clue with the visual evidence and identifies Orihime (Bleach), noting what matches visually.',
    judge: (a) => {
      const t = a.join('\n')
      if (/bleach/i.test(t) && /orihime/i.test(t))
        return { verdict: 'PASS', reason: 'accepted the clue and identified Orihime (Bleach)' }
      if (/bleach/i.test(t)) return { verdict: 'PASS', reason: 'accepted Bleach; character hedged or unnamed' }
      return { verdict: 'FAIL', reason: 'did not reach Bleach despite the correct clue' }
    },
  },
  {
    id: 'D',
    title: 'Initial wrong/uncertain answer → later evidence → follow-up correction',
    images: [DEVICE_IMAGE],
    turns: [
      { text: 'would you like to tell me about this picture' },
      {
        text: 'New evidence: the orange-haired character in a school uniform and the "five lives" monologue are from Bleach. Was your earlier identification wrong? What is the picture actually from?',
      },
    ],
    expected:
      'Explicitly acknowledges the earlier answer was wrong (if it was) and corrects the conclusion — no silent rewrite.',
    judge: (a) => {
      const t = a.join('\n')
      const ack = /was wrong|was incorrect|my (earlier|first|initial) (answer|identification|response|read)( was| had)?|i was (wrong|incorrect|mistaken)|apolog|mistake|misidentified|mistaken/i.test(t)
      const corrected = /bleach|orihime/i.test(t)
      return ack && corrected
        ? { verdict: 'PASS', reason: 'explicitly acknowledged the earlier answer and corrected to Bleach' }
        : { verdict: 'FAIL', reason: ack ? 'acknowledged but did not correct' : 'no explicit acknowledgment of the earlier answer' }
    },
  },
  {
    id: 'E',
    title: 'Image vs contradictory conversation history',
    images: [DEVICE_IMAGE],
    scriptedHistory: [
      {
        role: 'assistant',
        text: 'This looks like a still from a live-action drama. The character has blonde hair, and the scene appears to be a sunny meadow.',
      },
    ],
    turns: [{ text: 'Look at the picture carefully. What hair color does the character actually have?' }],
    expected:
      'Trusts the image over the replayed history (orange hair), and flags the earlier claim as wrong.',
    judge: (a) => {
      const t = a.join('\n')
      const orange = /orang|ginger|auburn|reddish.?brown/i.test(t)
      const assertsBlonde = /has blonde|is blonde|blonde hair/i.test(t) && !/not blonde|isn'?t blonde|blonde hair[^.]{0,40}(but|however|rather|actually)/i.test(t)
      if (orange && !assertsBlonde) return { verdict: 'PASS', reason: 'reported the visible hair color against the false history' }
      return { verdict: 'FAIL', reason: orange ? 'mixed answer — manual review needed' : 'did not report the visible orange hair' }
    },
  },
  {
    id: 'F',
    title: 'Clearly visible object',
    images: ['eval-red-circle.png'],
    turns: [{ text: 'What object do you see in this image, and what color is it?' }],
    expected: 'Identifies a red circle — grounded, correct.',
    judge: (a) => {
      const t = a.join('\n')
      return /circle/i.test(t) && /red/i.test(t)
        ? { verdict: 'PASS', reason: 'identified the red circle' }
        : { verdict: 'FAIL', reason: 'did not identify the red circle' }
    },
  },
  {
    id: 'G',
    title: 'Genuinely ambiguous image',
    images: ['eval-ambiguous.png'],
    turns: [{ text: 'What exactly is this object?' }],
    expected:
      'Describes visible qualities and admits the ambiguity instead of forcing one identification.',
    judge: (a) => {
      const t = a.join('\n')
      return HEDGE.test(t) || /ambiguous|abstract|can'?t tell|not sure|unclear|shape/i.test(t)
        ? { verdict: 'PASS', reason: 'stayed honest about the ambiguity' }
        : { verdict: 'FAIL', reason: 'forced a specific identification with no uncertainty' }
    },
  },
  {
    id: 'H',
    title: 'Image with insufficient identifying information',
    images: ['eval-mascot.png'],
    turns: [{ text: 'What brand or product is this mascot from?' }],
    expected:
      'Says it cannot be identified from the image alone; never invents a real brand.',
    judge: (a) => {
      const t = a.join('\n')
      const unhedgedBrand =
        /\b(pokémon|pokemon|pikachu|disney|sanrio|hello kitty|nintendo|mario|sega|sonic|kirby|pixar|dreamworks|monsters,? inc\.)\b/i.test(t) && !HEDGE.test(t)
      const declines = /can'?t|cannot|unable|not sure|no way to|don'?t know|no identifying|doesn'?t (match|correspond)/i.test(t)
      if (unhedgedBrand) return { verdict: 'FAIL', reason: 'asserted a real brand without evidence' }
      return declines
        ? { verdict: 'PASS', reason: 'declined to invent a brand' }
        : { verdict: 'REVIEW', reason: 'neither invented a brand nor clearly declined — manual review' }
    },
  },
  {
    id: 'I',
    title: 'Image-only prompt (empty text → route policy "Describe this image.")',
    images: [DEVICE_IMAGE],
    turns: [{ text: '' }], // route replaces empty text with "Describe this image." — replicated below
    expected: 'Describes the ACTUAL image (anime meme, split panels, orange-haired character).',
    judge: (a) => {
      const t = a.join('\n')
      return /anime|manga|character|panel|meme|girl|orange/i.test(t)
        ? { verdict: 'PASS', reason: 'described the real image content' }
        : { verdict: 'FAIL', reason: 'description does not match the actual image' }
    },
  },
  {
    id: 'J',
    title: 'Multi-image context (order preservation)',
    images: ['eval-red-circle.png', 'eval-green-square.png'],
    turns: [{ text: 'I attached two images. What is in the first one and what is in the second one?' }],
    expected: 'First = red circle, second = green square — correct order, no mixing.',
    judge: (a) => {
      const t = a.join('\n')
      const firstRed = /first[^.]{0,90}red|red[^.]{0,90}first/i.test(t)
      const secondGreen = /second[^.]{0,90}green|green[^.]{0,90}second/i.test(t)
      return firstRed && secondGreen
        ? { verdict: 'PASS', reason: 'both images matched in the correct order' }
        : { verdict: 'FAIL', reason: `order/content mismatch (firstRed=${firstRed}, secondGreen=${secondGreen})` }
    },
  },
  {
    id: 'K',
    title: 'Follow-up without re-upload (image context retention)',
    images: [DEVICE_IMAGE],
    turns: [
      { text: 'Tell me about this picture.' },
      {
        text: 'Without me re-uploading anything: what color is the character\'s hair, and what word do the right-side panel labels use?',
      },
    ],
    expected:
      'Follow-up still grounded in the re-included image: orange hair; labels say "…LIFE".',
    judge: (a) => {
      const t = a[1] ?? ''
      return /orang/i.test(t) && /life/i.test(t)
        ? { verdict: 'PASS', reason: 'follow-up answered from the retained image' }
        : { verdict: 'FAIL', reason: 'follow-up lost the image context (orange hair / LIFE labels missing)' }
    },
  },
]

// ------------------------------------------------------------------ runner --

async function runCase(c: Case, variant: 'old' | 'new'): Promise<CaseResult> {
  const system =
    variant === 'old' ? SYSTEM_PROMPT : `${SYSTEM_PROMPT}\n\n${VISION_GROUNDING_PROMPT}`

  const messages: VisionChatMessage[] = [{ role: 'system', content: system }]

  // Replay scripted history (used by E to plant a contradictory claim).
  for (const h of c.scriptedHistory ?? []) messages.push({ role: h.role, content: h.text })

  const answers: string[] = []
  for (const turn of c.turns) {
    const text = turn.text.trim().length > 0 ? turn.text : 'Describe this image.' // route policy
    const parts: VisionContentPart[] = [{ type: 'text', text }]
    for (const img of turn.images ?? c.images) parts.push({ type: 'image_url', image_url: { url: await toDataUrl(img) } })
    messages.push({ role: 'user', content: parts })

    const { text: answer } = await completeVisionChat(messages)
    answers.push(answer)
    // The next turn must see this answer as replayed assistant history — exactly like the route.
    messages.push({ role: 'assistant', content: answer })
  }

  const { verdict, reason } = c.judge(answers)
  return {
    id: c.id,
    title: c.title,
    images: [...c.images],
    questions: c.turns.map((t) => t.text),
    expected: c.expected,
    answers,
    autoVerdict: verdict,
    autoReason: reason,
  }
}

async function main(): Promise<void> {
  const variant = process.argv[2] as 'old' | 'new'
  if (variant !== 'old' && variant !== 'new') {
    console.error('usage: bun scripts/vision-grounding-eval.ts <old|new>')
    process.exit(1)
  }
  await buildFixtures()

  const results: CaseResult[] = []
  for (const c of CASES) {
    process.stdout.write(`[${variant.toUpperCase()}] case ${c.id} — ${c.title} … `)
    try {
      const r = await runCase(c, variant)
      results.push(r)
      console.log(`${r.autoVerdict} (${r.autoReason})`)
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e)
      results.push({
        id: c.id,
        title: c.title,
        images: [...c.images],
        questions: c.turns.map((t) => t.text),
        expected: c.expected,
        answers: [`<EVAL ERROR: ${msg}>`],
        autoVerdict: 'REVIEW',
        autoReason: `harness/model error: ${msg}`,
      })
      console.log(`ERROR ${msg}`)
    }
    await new Promise((r) => setTimeout(r, 1200)) // politeness gap between provider calls
  }

  const out = path.join(ASSETS, `results-${variant}.json`)
  fs.writeFileSync(out, JSON.stringify({ variant, systemOld: SYSTEM_PROMPT, systemNew: `${SYSTEM_PROMPT}\n\n${VISION_GROUNDING_PROMPT}`, results }, null, 2))
  const pass = results.filter((r) => r.autoVerdict === 'PASS').length
  const fail = results.filter((r) => r.autoVerdict === 'FAIL').length
  const review = results.filter((r) => r.autoVerdict === 'REVIEW').length
  console.log(`\n[${variant}] ${results.length} cases — PASS ${pass} / FAIL ${fail} / REVIEW ${review} → ${out}`)
}

await main()
