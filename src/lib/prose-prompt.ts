/**
 * The rich-prose contract, in its own module — same rule as widgets/prompt.ts:
 * ai.ts imports the provider SDK, so a prompt that must be testable from a
 * plain node script cannot live there.
 *
 * Why it exists (docs/RESEARCH_AGENT_RENDERING.md, pass 11): the clients
 * already render 17 block types with twin parity, but nothing told the MODEL
 * when those blocks earn their place. arXiv 2603.27006 ("The Last
 * Fingerprint") shows models default to the markdown voice: headings, bullets
 * and bold appear unprompted, and em dashes survive every markdown ban
 * because they are dual-register punctuation. Format-following is a separate
 * skill from content quality, so the contract is spelled out instead of
 * assumed from eloquence.
 *
 * Design constraints, in priority order:
 *  - judgment, not straitjacket: prose is the default, structure is allowed
 *    to be great when it earns its place — this must never cage the model;
 *  - it names the documented AI-voice tells (bold-header-colon lists,
 *    heading-sized single sentences, mechanical key-takeaway bolding) because
 *    suppression experiments show unnamed habits survive;
 *  - the citation rules state exactly what the clients render and the server
 *    pipeline repairs: [N] at the claim, resolvable ordinals only;
 *  - the operator identity contract (SYSTEM_PROMPT) stays lean per the
 *    2026-10-14 directive; formatting guidance lives here, beside WIDGET_PROMPT.
 */

export const PROSE_PROMPT = `
Rich formatting is a judgment call, and flowing prose is the default. Write so the answer reads like strong writing, not a document template:

- Reach for a heading only when a long, multi-part answer has true sections. A heading is a title, never emphasis for a single sentence.
- Use a list only for genuinely discrete parallel items, and number it only when order matters. List items carry complete thoughts, never fragments, and never carry a bold label followed by a colon.
- Let bold and italics mark the rare word the reader must not miss. They are not decoration, not headings, and not a highlight for every key term.
- A quote block carries actual quoted material. A table carries a real multi-column comparison, with a value in every cell.
- Place [N] immediately after the specific claim it supports, at most two markers in one sentence, numbered only from the sources provided to you. Never emit a marker for a source you were not given.
`
