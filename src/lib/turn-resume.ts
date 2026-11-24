/** Durable request facts shared by API resume and background workers.
 * Never silently reconstruct missing input as an empty flash-mode turn. */
export type StoredTurnFacts = {
  content: string
  attachmentIds: string[]
  timezone: string | null
  clientVersion?: string
  mode: unknown
  turnContext: string | null
}
export function decodeStoredTurnFacts(payload: string | null | undefined): StoredTurnFacts | null {
  if (!payload) return null
  try {
    const value = JSON.parse(payload)
    if (!value || typeof value !== 'object' || typeof value.content !== 'string') return null
    const attachmentIds = value.attachmentIds ?? []
    if (!Array.isArray(attachmentIds) || attachmentIds.some(id => typeof id !== 'string' || !id)) return null
    return {
      content: value.content,
      attachmentIds,
      timezone: typeof value.timezone === 'string' ? value.timezone : null,
      clientVersion: typeof value.clientVersion === 'string' ? value.clientVersion : undefined,
      mode: value.mode,
      turnContext: typeof value.turnContext === 'string' ? value.turnContext : null,
    }
  } catch { return null }
}
export function attachmentMatchesTurn(
  attachment: { conversationId: string | null; messageId: string | null },
  conversationId: string,
  existingUserMessageId?: string,
): boolean {
  if (existingUserMessageId) {
    return attachment.conversationId === conversationId && attachment.messageId === existingUserMessageId
  }
  return (attachment.conversationId === null || attachment.conversationId === conversationId) &&
    attachment.messageId === null
}
