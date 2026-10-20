"use strict";
/**
 * The kind registry: every catalogue kind -> its web renderer.
 *
 * This is the web twin of RichBlocks.kt's dispatch — one line per kind. A kind
 * added to the catalogue without a renderer here degrades to a code block, so
 * keep the two in step (the backend test counts them apart).
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.REGISTRY = void 0;
exports.rendererFor = rendererFor;
const ChartWidget_1 = require("./widgets/ChartWidget");
const StatWidget_1 = require("./widgets/StatWidget");
const TableWidget_1 = require("./widgets/TableWidget");
const CompareWidget_1 = require("./widgets/CompareWidget");
const ProgressWidget_1 = require("./widgets/ProgressWidget");
const TimelineWidget_1 = require("./widgets/TimelineWidget");
const GaugeWidget_1 = require("./widgets/GaugeWidget");
const QuizWidget_1 = require("./widgets/QuizWidget");
const FormWidget_1 = require("./widgets/FormWidget");
const CalcWidget_1 = require("./widgets/CalcWidget");
const RatingWidget_1 = require("./widgets/RatingWidget");
const PollWidget_1 = require("./widgets/PollWidget");
const ChecklistWidget_1 = require("./widgets/ChecklistWidget");
const ChipsWidget_1 = require("./widgets/ChipsWidget");
const MenuWidget_1 = require("./widgets/MenuWidget");
const VoiceWidget_1 = require("./widgets/VoiceWidget");
const TabsWidget_1 = require("./widgets/TabsWidget");
const AccordionWidget_1 = require("./widgets/AccordionWidget");
const StepsWidget_1 = require("./widgets/StepsWidget");
const TreeWidget_1 = require("./widgets/TreeWidget");
const CarouselWidget_1 = require("./widgets/CarouselWidget");
const CardsWidget_1 = require("./widgets/CardsWidget");
const FollowupsWidget_1 = require("./widgets/FollowupsWidget");
const ActionsWidget_1 = require("./widgets/ActionsWidget");
const PanelWidget_1 = require("./widgets/PanelWidget");
const FlashcardsWidget_1 = require("./widgets/FlashcardsWidget");
const DiffWidget_1 = require("./widgets/DiffWidget");
const MediaWidget_1 = require("./widgets/MediaWidget");
const AppWidget_1 = require("./widgets/AppWidget");
const MapBlock_1 = require("./blocks/MapBlock");
const SimWidget_1 = require("./sims/SimWidget");
const Stage3DViews_1 = require("./stage3d/Stage3DViews");
const CanvasWidget_1 = require("./canvas/CanvasWidget");
const EditorWidget_1 = require("./editor/EditorWidget");
const TerminalWidget_1 = require("./terminal/TerminalWidget");
/** kind -> renderer. `map` renders natively on web via Leaflet. */
exports.REGISTRY = {
    chart: ChartWidget_1.ChartWidget,
    stat: StatWidget_1.StatWidget,
    table: TableWidget_1.TableWidget,
    compare: CompareWidget_1.CompareWidget,
    progress: ProgressWidget_1.ProgressWidget,
    timeline: TimelineWidget_1.TimelineWidget,
    gauge: GaugeWidget_1.GaugeWidget,
    quiz: QuizWidget_1.QuizWidget,
    form: FormWidget_1.FormWidget,
    calc: CalcWidget_1.CalcWidget,
    rating: RatingWidget_1.RatingWidget,
    poll: PollWidget_1.PollWidget,
    checklist: ChecklistWidget_1.ChecklistWidget,
    chips: ChipsWidget_1.ChipsWidget,
    menu: MenuWidget_1.MenuWidget,
    voice: VoiceWidget_1.VoiceWidget,
    tabs: TabsWidget_1.TabsWidget,
    accordion: AccordionWidget_1.AccordionWidget,
    steps: StepsWidget_1.StepsWidget,
    tree: TreeWidget_1.TreeWidget,
    carousel: CarouselWidget_1.CarouselWidget,
    cards: CardsWidget_1.CardsWidget,
    followups: FollowupsWidget_1.FollowupsWidget,
    actions: ActionsWidget_1.ActionsWidget,
    panel: PanelWidget_1.PanelWidget,
    flashcards: FlashcardsWidget_1.FlashcardsWidget,
    diff: DiffWidget_1.DiffWidget,
    media: MediaWidget_1.MediaWidget,
    app: AppWidget_1.AppWidget,
    map: MapBlock_1.MapBlock,
    sim: SimWidget_1.SimWidget,
    stage3d: Stage3DViews_1.Stage3DWidget,
    canvas: CanvasWidget_1.CanvasWidget,
    editor: EditorWidget_1.EditorWidget,
    terminal: TerminalWidget_1.TerminalWidget,
};
function rendererFor(kind) {
    return exports.REGISTRY[kind] ?? null;
}
