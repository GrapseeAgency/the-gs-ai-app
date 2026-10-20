'use client'

/** The sim kind dispatcher: sim -> the 8 live simulations. */
type Rec<K extends string = string, V = unknown> = Record<K, V>
import { ClothSim } from './ClothSim'
import { SoftBodySim } from './SoftBodySim'
import { FluidSim } from './FluidSim'
import { SmokeSim } from './SmokeSim'
import { ParticleSim } from './ParticleSim'
import { RigidSim } from './RigidSim'
import { TerrainSim } from './TerrainSim'
import { GameSim } from './GameSim'

export type SimPayload = Rec<string, unknown> & { sim?: string; title?: string; gravity?: number; speed?: number; damping?: number; count?: number; size?: number; seed?: number; interactive?: boolean; note?: string }
export type SimKnobs = { gravity: number; speed: number; damping: number; count: number; size: number; seed: number; interactive: boolean }

export function SimWidget({ payload }: { payload: SimPayload }) {
  const full: SimPayload & SimKnobs = {
    ...payload,
    gravity: payload.gravity ?? 9.8,
    speed: payload.speed ?? 1,
    damping: payload.damping ?? 0.99,
    count: payload.count ?? 60,
    size: payload.size ?? 70,
    seed: payload.seed ?? 42,
    interactive: payload.interactive !== false,
  }
  switch (payload.sim) {
    case 'cloth': return <ClothSim payload={full} />
    case 'softbody': return <SoftBodySim payload={full} />
    case 'fluid': return <FluidSim payload={full} />
    case 'smoke': return <SmokeSim payload={full} />
    case 'rigid': return <RigidSim payload={full} />
    case 'terrain': return <TerrainSim payload={full} />
    case 'game': return <GameSim payload={full} />
    case 'particles':
    default: return <ParticleSim payload={full} />
  }
}
