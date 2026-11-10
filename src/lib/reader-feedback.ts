/** A rating is a fact about one answer, not permission to override the next
 * user request or infer a permanent writing/formatting preference.
 */
export function readerFeedbackContext(previousAssistant: { feedback: string | null } | null): string {
  if (previousAssistant?.feedback === 'down') return '\n\nREADER FEEDBACK\nThe immediately preceding assistant answer was rated not helpful.'
  if (previousAssistant?.feedback === 'up') return '\n\nREADER FEEDBACK\nThe immediately preceding assistant answer was rated helpful.'
  return ''
}
