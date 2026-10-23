'use client'

import { NexlyPlanCard } from '@/components/nexly/NexlyPlanCard'
import type { NexlyPlan } from '@/lib/nexly/plan'

/**
 * Catalogue renderer for the `plan` fence. The native apps own the exact
 * glass composition; this wrapper keeps the web pipeline total so a plan
 * emitted by the model is never downgraded to a code block.
 */
export function PlanWidget({ payload }: { payload: NexlyPlan }) {
  return <NexlyPlanCard plan={payload} />
}
