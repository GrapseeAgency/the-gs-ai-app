/**
 * The kind registry: every catalogue kind -> its web renderer.
 *
 * This is the web twin of RichBlocks.kt's dispatch — one line per kind. A kind
 * added to the catalogue without a renderer here degrades to a code block, so
 * keep the two in step (the backend test counts them apart).
 */

import type { ComponentType } from 'react'
import { ChartWidget } from './widgets/ChartWidget'
import { StatWidget } from './widgets/StatWidget'
import { TableWidget } from './widgets/TableWidget'
import { CompareWidget } from './widgets/CompareWidget'
import { ProgressWidget } from './widgets/ProgressWidget'
import { TimelineWidget } from './widgets/TimelineWidget'
import { GaugeWidget } from './widgets/GaugeWidget'
import { QuizWidget } from './widgets/QuizWidget'
import { FormWidget } from './widgets/FormWidget'
import { CalcWidget } from './widgets/CalcWidget'
import { RatingWidget } from './widgets/RatingWidget'
import { PollWidget } from './widgets/PollWidget'
import { ChecklistWidget } from './widgets/ChecklistWidget'
import { ChipsWidget } from './widgets/ChipsWidget'
import { MenuWidget } from './widgets/MenuWidget'
import { VoiceWidget } from './widgets/VoiceWidget'
import { TabsWidget } from './widgets/TabsWidget'
import { AccordionWidget } from './widgets/AccordionWidget'
import { StepsWidget } from './widgets/StepsWidget'
import { TreeWidget } from './widgets/TreeWidget'
import { CarouselWidget } from './widgets/CarouselWidget'
import { CardsWidget } from './widgets/CardsWidget'
import { FollowupsWidget } from './widgets/FollowupsWidget'
import { ActionsWidget } from './widgets/ActionsWidget'
import { PanelWidget } from './widgets/PanelWidget'
import { FlashcardsWidget } from './widgets/FlashcardsWidget'
import { DiffWidget } from './widgets/DiffWidget'
import { MediaWidget } from './widgets/MediaWidget'
import { AppWidget } from './widgets/AppWidget'
import { MapBlock } from './blocks/MapBlock'
import { SimWidget } from './sims/SimWidget'
import { Stage3DWidget } from './stage3d/Stage3DViews'
import { CanvasWidget } from './canvas/CanvasWidget'
import { EditorWidget } from './editor/EditorWidget'
import { TerminalWidget } from './terminal/TerminalWidget'

export type PromptHandler = (prompt: string) => void

// Each renderer declares its own precise payload type; the registry stores
// them under the loose wire shape. The backend validator owns the contract.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type WidgetComponent = ComponentType<{ payload: any; onPrompt?: PromptHandler }>

/** kind -> renderer. `map` renders natively on web via Leaflet. */
export const REGISTRY: Record<string, WidgetComponent> = {
  chart: ChartWidget,
  stat: StatWidget,
  table: TableWidget,
  compare: CompareWidget,
  progress: ProgressWidget,
  timeline: TimelineWidget,
  gauge: GaugeWidget,
  quiz: QuizWidget,
  form: FormWidget,
  calc: CalcWidget,
  rating: RatingWidget,
  poll: PollWidget,
  checklist: ChecklistWidget,
  chips: ChipsWidget,
  menu: MenuWidget,
  voice: VoiceWidget,
  tabs: TabsWidget,
  accordion: AccordionWidget,
  steps: StepsWidget,
  tree: TreeWidget,
  carousel: CarouselWidget,
  cards: CardsWidget,
  followups: FollowupsWidget,
  actions: ActionsWidget,
  panel: PanelWidget,
  flashcards: FlashcardsWidget,
  diff: DiffWidget,
  media: MediaWidget,
  app: AppWidget,
  map: MapBlock,
  sim: SimWidget,
  stage3d: Stage3DWidget,
  canvas: CanvasWidget,
  editor: EditorWidget,
  terminal: TerminalWidget,
}

export function rendererFor(kind: string): WidgetComponent | null {
  return REGISTRY[kind] ?? null
}
