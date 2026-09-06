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

import type { Assistant, Conversation, Message } from '@prisma/client'

export type ConversationJson = {
  id: string
  title: string
  modelId?: string
  assistantId?: string
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

export type AssistantJson = {
  id: string
  slug: string
  name: string
  description: string
  instructions: string
  category: string
  starters: string[]
  published: boolean
  favourite: boolean
  uses: number
  rating: number
  createdAt: string
}

function parseStarters(raw: string): string[] {
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((s): s is string => typeof s === 'string') : []
  } catch {
    return []
  }
}

export function assistantToJson(a: Assistant): AssistantJson {
  return {
    id: a.id,
    slug: a.slug,
    name: a.name,
    description: a.description,
    instructions: a.instructions,
    category: a.category,
    starters: parseStarters(a.starters),
    published: a.published,
    favourite: a.favourite,
    uses: a.uses,
    rating: a.rating,
    createdAt: a.createdAt.toISOString(),
  }
}

export function conversationToJson(c: Conversation & { assistant?: Assistant | null }): ConversationJson {
  return {
    id: c.id,
    title: c.title,
    ...(c.modelId ? { modelId: c.modelId } : {}),
    ...(c.assistantId ? { assistantId: c.assistantId } : {}),
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
