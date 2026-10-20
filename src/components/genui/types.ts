/**
 * Shared payload types for the web GenUI renderers.
 *
 * The backend validator (src/lib/widgets/validate.ts) guarantees these shapes
 * before they reach the client; the types here are the client-side contract
 * mirror, kept deliberately loose on fields the renderers tolerate.
 */

import type { Surface } from '@/lib/design/rulebook'

/** Every validated widget payload carries the compiler's art direction. */
export interface StyledPayload extends Record<string, unknown> {
  accent?: string
  variant?: Surface
  palette?: string
  title?: string
}

export interface ChartPayload extends StyledPayload {
  type: string
  unit?: string
  labels?: string[]
  series?: Array<{ name: string; data: number[][] }>
  // graph families
  nodes?: Array<string | { id: string; label?: string; group?: string }>
  links?: Array<{ source: string; target: string; value: number }>
  edges?: Array<{ source: string; target: string; weight?: number }>
  items?: Array<{ text?: string; weight?: number; label?: string; value?: number; target?: number | null; max?: number | null; ranges?: number[]; unit?: string }>
  root?: TreeNode
  max?: number | null
}

export interface TreeNode {
  label: string
  value?: number
  detail?: string
  children?: TreeNode[]
}

export interface StatPayload extends StyledPayload {
  cards: Array<{ label: string; value: string; unit?: string; delta?: string; direction?: string }>
}

export interface TablePayload extends StyledPayload {
  columns: string[]
  rows: string[][]
  sortable?: boolean
}

export interface ComparePayload extends StyledPayload {
  left: { title: string; items: string[] }
  right: { title: string; items: string[] }
}

export interface ProgressPayload extends StyledPayload {
  items: Array<{ label: string; percent: number }>
}

export interface TimelinePayload extends StyledPayload {
  entries: Array<{ date: string; title: string; detail: string }>
}

export interface GaugePayload extends StyledPayload {
  items: Array<{ label: string; value: number; max: number; unit: string }>
}

export interface QuizPayload extends StyledPayload {
  questions: Array<{ q: string; options: string[]; answer: number; explain?: string }>
}

export interface FormFieldPayload {
  type: string
  label: string
  options?: string[]
  required?: boolean
  min?: number
  max?: number
  value?: number | string
  unit?: string
}

export interface FormPayload extends StyledPayload {
  submitLabel?: string
  fields: FormFieldPayload[]
}

export interface CalcPayload extends StyledPayload {
  type: string
  fields: Array<{
    key: string
    label: string
    value: number
    unit?: string
    min?: number | null
    max?: number | null
    step?: number | null
    choices?: Array<{ label: string; value: number }>
  }>
  outputs: Array<{ label: string; expr: string; unit?: string; format?: string; primary?: boolean }>
  note?: string
  presets?: Array<{ label: string; values: Record<string, number> }>
}

export interface MediaPayload extends StyledPayload {
  type: string
  items?: Array<Record<string, string>>
  code?: string
  language?: string
  text?: string
}

export interface AppPayload extends StyledPayload {
  type: string
  steps?: Array<Record<string, unknown>>
  columns?: Array<{ title: string; items: Array<Record<string, unknown>> }>
  items?: Array<Record<string, unknown>>
  groups?: Array<{ title: string; options: Array<Record<string, unknown>> }>
  rows?: string[][]
  slots?: string[]
  month?: string
  submitLabel?: string
}

export interface TabsPayload extends StyledPayload {
  tabs: Array<{ label: string; body?: string; items?: string[] }>
}

export interface AccordionPayload extends StyledPayload {
  sections: Array<{ label: string; body: string; expanded?: boolean }>
}

export interface StepsPayload extends StyledPayload {
  steps: Array<{ title: string; body?: string; state?: string }>
}

export interface MenuPayload extends StyledPayload {
  options: Array<{ label: string; detail?: string; prompt?: string }>
}

export interface ChipsPayload extends StyledPayload {
  options: Array<{ label: string; prompt?: string }>
  multi?: boolean
}

export interface RatingPayload extends StyledPayload {
  prompt?: string
  style?: string
  max: number
  labels?: string[]
}

export interface PollPayload extends StyledPayload {
  question?: string
  options: Array<{ label: string; votes: number }>
}

export interface ChecklistPayload extends StyledPayload {
  items: Array<{ label: string; detail?: string; done?: boolean }>
}

export interface TreePayload extends StyledPayload {
  roots: TreeNode[]
}

export interface FlashcardsPayload extends StyledPayload {
  cards: Array<{ front: string; back: string }>
}

export interface DiffPayload extends StyledPayload {
  language?: string
  before?: string
  after?: string
  unified?: string
}

export interface CarouselPayload extends StyledPayload {
  items: Array<{ image?: string; caption?: string }>
}

export interface CardsPayload extends StyledPayload {
  cards: Array<{ title: string; subtitle?: string; prompt?: string }>
}

export interface FollowupsPayload extends StyledPayload {
  prompts: string[]
}

export interface ActionsPayload extends StyledPayload {
  buttons: Array<{ label: string; kind: string; value: string; style?: string }>
}

export interface PanelPayload extends StyledPayload {
  summary?: string
  body?: string
  expanded?: boolean
  badges?: Array<{ label: string; tone?: string }>
  popover?: string
}

export interface VoicePayload extends StyledPayload {
  prompt?: string
  hint?: string
}
