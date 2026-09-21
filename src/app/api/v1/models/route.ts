import { NextResponse } from 'next/server'

export const dynamic = 'force-dynamic'

/**
 * Static model catalogue. Field names follow shared-contracts/openapi.yaml
 * (`ModelDto` / components.schemas.Model) EXACTLY:
 *   { id, displayName, capabilities[], contextWindow, speedTier }
 * capabilities enum: text | vision | tools | reasoning | voice
 * speedTier enum:    fast | balanced | deep
 *
 * `isDefault` is an additive extension (not in the contract) marking the
 * model clients should use when the user has not picked one (gs-balanced).
 */
const MODELS: {
  id: string
  displayName: string
  capabilities: string[]
  contextWindow: number
  speedTier: 'fast' | 'balanced' | 'deep'
  isDefault?: boolean
}[] = [
  {
    id: 'gs-swift',
    displayName: 'GS Swift',
    capabilities: ['text'],
    contextWindow: 32000,
    speedTier: 'fast',
  },
  {
    id: 'gs-free',
    displayName: 'GS Free',
    capabilities: ['text'],
    contextWindow: 200000,
    speedTier: 'fast',
  },
  {
    id: 'gs-free-big',
    displayName: 'GS Free Deep',
    capabilities: ['text', 'reasoning'],
    contextWindow: 1000000,
    speedTier: 'deep',
  },
  {
    id: 'gs-balanced',
    displayName: 'GS Balanced',
    capabilities: ['text', 'tools'],
    contextWindow: 128000,
    speedTier: 'balanced',
    isDefault: true,
  },
  {
    id: 'gs-deep',
    displayName: 'GS Deep',
    capabilities: ['text', 'reasoning'],
    contextWindow: 128000,
    speedTier: 'deep',
  },
  {
    id: 'gs-research',
    displayName: 'GS Research',
    capabilities: ['text', 'reasoning', 'tools'],
    contextWindow: 200000,
    speedTier: 'deep',
  },
  {
    id: 'gs-coder',
    displayName: 'GS Coder',
    capabilities: ['text', 'reasoning', 'tools'],
    contextWindow: 128000,
    speedTier: 'balanced',
  },
  {
    id: 'gs-creative',
    displayName: 'GS Creative',
    capabilities: ['text'],
    contextWindow: 128000,
    speedTier: 'balanced',
  },
  {
    id: 'gs-vision',
    displayName: 'GS Vision',
    capabilities: ['text', 'vision'],
    contextWindow: 64000,
    speedTier: 'balanced',
  },
  {
    id: 'gs-voice',
    displayName: 'GS Voice',
    capabilities: ['text', 'voice'],
    contextWindow: 32000,
    speedTier: 'fast',
  },
]

export async function GET() {
  // PHASE 8.2 §25 — ONE capability contract for EVERY tier. Search is a backend
  // capability: the selected model may differ, the SEARCH SERVICE must not.
  const searchCapabilities = { webSearch: true, research: true, sourceOpen: true, sourceRead: true }
  return NextResponse.json({
    models: MODELS.map((m) => ({ ...m, searchCapabilities })),
    searchCapabilities,
  })
}
