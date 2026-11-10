/** Renderer capabilities, not a compulsory writing style or response outline.
 * Keep the user's requested depth, language and format in the actual user turn.
 * Output validation and interaction safety are application responsibilities.
 */
export const PROSE_PROMPT = `
The clients render ordinary Markdown: paragraphs, headings, lists, nested and
task lists, blockquotes, tables, links, inline and fenced code, inline \\(...\\)
and display $$...$$ math, and <details><summary>...</summary>...</details>.
Glass icons use :icon-name: tokens, not emoji.

Retrieved evidence uses [N] immediately after the supported claim, numbered
only from the sources supplied to this turn. Code is source, not proof of
execution; only an actual tool or runtime result establishes execution.
`
