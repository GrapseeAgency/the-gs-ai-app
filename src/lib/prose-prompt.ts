/**
 * The rich-prose and composition contract, in its own module — same rule as
 * widgets/prompt.ts: ai.ts imports the provider SDK, so a prompt that must be
 * testable from a plain node script cannot live there.
 *
 * Why it exists (docs/RESEARCH_AGENT_RENDERING.md, pass 11): the clients
 * render 17 block types and inline spans in twin parity across Web, Android,
 * and iOS, and the widget compiler normalizes 170+ interactive surfaces. The
 * model has full freedom to combine flowing prose, structural markdown, math,
 * diagrams, collapsibles, inline icons, and interactive widgets whenever they
 * make the answer clearer — while avoiding mechanical AI-template habits
 * (arXiv 2603.27006, "The Last Fingerprint").
 *
 * Design constraints, in priority order:
 *  - freedom with judgment: the model may use any combination of prose,
 *    headings, lists, nested lists, blockquotes, markdown tables, collapsible
 *    <details>/<summary> blocks, LaTeX math, code blocks, mermaid/svg/html,
 *    inline :icon-name: glass glyphs, and interactive widgets;
 *  - flowing prose is the natural baseline for conversation and explanation,
 *    so structure steps in when it genuinely helps the reader scan, compare,
 *    or act;
 *  - it names the documented AI-voice tells (bold-label-colon lists,
 *    single-sentence headings, mechanical key-takeaway bolding) because
 *    suppression experiments show unnamed habits survive;
 *  - citation rules state what the clients render and the server pipeline
 *    repairs: [N] immediately after the supported claim, from provided sources;
 *  - SYSTEM_PROMPT stays the lean 5-clause operator contract (PHASE 8.3).
 */

export const PROSE_PROMPT = `
You have full freedom to compose with every block the renderer speaks: flowing prose, #–###### headings, bullet and numbered lists (including nested lists), > blockquotes, markdown tables, <details><summary>Title</summary>...</details> collapsible sections, inline \\(...\\) and display $$...$$ LaTeX math, inline \`code\` and fenced code blocks, [links](https://...), inline :icon-name: glass glyphs, and interactive widgets.

Rich formatting is a judgment call, and flowing prose is the default. Shape each answer so it reads like strong human writing, not a rigid template:

- Reach for a heading when a multi-part answer has true sections, or when the user asks for a structured breakdown. A heading is a section title, never emphasis for a single sentence.
- Use a list for genuinely discrete parallel items, and number it when sequence, rank, or steps matter. List items carry complete thoughts, never fragments, and never carry a bold label followed by a colon.
- Let bold and italics mark the rare word the reader must not miss. They are not decoration, not headings, and not a highlight for every key term.
- A quote block carries actual quoted material or a primary-source excerpt. A table carries a real multi-column comparison, with a value in every cell. A <details> block tucks secondary depth or long derivations out of the main flow.
- Place [N] immediately after the specific claim it supports, at most two markers in one sentence, numbered only from the sources provided to you. Never emit a marker for a source you were not given.
`
