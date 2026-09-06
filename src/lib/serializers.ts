/**
 * DB-model → API-JSON mappers.
 *
 * Field names follow shared-contracts/openapi.yaml EXACTLY:
 *   Conversation: { id, title, modelId?, pinned, archived, createdAt, updatedAt }
 *   Message:      { id, conversationId, role, content, createdAt }
 *
 * Nullable optional fields (modelId) are omitted when unset so the payload
 * never violates the contract's `type: string` for present keys.
 * Dates are serialized as ISO-8601 date-time strings.
 */

import type { Conversation, Message } from '@prisma/client'

export type ConversationJson = {
  id: string
  title: string
  modelId?: string
  pinned: boolean
  archived: boolean
  createdAt: string
  updatedAt: string
}

export type MessageJson = {
  id: string
  conversationId: string
  role: string
  content: string
  createdAt: string
}

export function conversationToJson(c: Conversation): ConversationJson {
  return {
    id: c.id,
    title: c.title,
    ...(c.modelId ? { modelId: c.modelId } : {}),
    pinned: c.pinned,
    archived: c.archived,
    createdAt: c.createdAt.toISOString(),
    updatedAt: c.updatedAt.toISOString(),
  }
}

export function messageToJson(m: Message): MessageJson {
  return {
    id: m.id,
    conversationId: m.conversationId,
    role: m.role,
    content: m.content,
    createdAt: m.createdAt.toISOString(),
  }
}
