export type ModelPreferenceId = 'pro' | 'lite' | 'vision' | 'code'
export type ReasoningEffort = 'minimal' | 'low' | 'standard' | 'high'
export type RequestMode = 'flash' | 'thinking' | 'auto'

export interface ModelPreferences {
  model: ModelPreferenceId
  effort: ReasoningEffort
  auto: boolean
  remember: boolean
}

export const MODEL_PREFERENCES_KEY = 'nexly-model-preferences'

export const DEFAULT_MODEL_PREFERENCES: Readonly<ModelPreferences> = {
  model: 'pro',
  effort: 'standard',
  auto: true,
  remember: true,
}

const MODEL_IDS = new Set<ModelPreferenceId>(['pro', 'lite', 'vision', 'code'])
const EFFORT_IDS = new Set<ReasoningEffort>(['minimal', 'low', 'standard', 'high'])
const REQUEST_MODES = new Set<RequestMode>(['flash', 'thinking', 'auto'])

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function readModelPreferences(): ModelPreferences {
  if (typeof window === 'undefined') return { ...DEFAULT_MODEL_PREFERENCES }

  try {
    const raw = window.localStorage.getItem(MODEL_PREFERENCES_KEY)
    if (!raw) return { ...DEFAULT_MODEL_PREFERENCES }
    const value: unknown = JSON.parse(raw)
    if (!isRecord(value)) return { ...DEFAULT_MODEL_PREFERENCES }

    return {
      model: MODEL_IDS.has(value.model as ModelPreferenceId)
        ? (value.model as ModelPreferenceId)
        : DEFAULT_MODEL_PREFERENCES.model,
      effort: EFFORT_IDS.has(value.effort as ReasoningEffort)
        ? (value.effort as ReasoningEffort)
        : DEFAULT_MODEL_PREFERENCES.effort,
      auto: typeof value.auto === 'boolean' ? value.auto : DEFAULT_MODEL_PREFERENCES.auto,
      remember: value.remember !== false,
    }
  } catch {
    return { ...DEFAULT_MODEL_PREFERENCES }
  }
}

export function writeModelPreferences(preferences: ModelPreferences): void {
  if (typeof window === 'undefined' || !preferences.remember) return
  try {
    window.localStorage.setItem(MODEL_PREFERENCES_KEY, JSON.stringify(preferences))
  } catch {
    // Storage may be unavailable (for example, in a private browsing context).
  }
}

export function clearModelPreferences(): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(MODEL_PREFERENCES_KEY)
  } catch {
    // Leave the in-memory setting usable even when browser storage is blocked.
  }
}

/** Resolve the app setting to the server-supported thinking mode.
 * The GS server performs capability/model routing from the actual message;
 * the client only chooses automatic, fast, or explicit reasoning behavior. */
export function requestModeForPreferences(preferences: ModelPreferences): RequestMode {
  if (preferences.auto) return 'auto'
  switch (preferences.effort) {
    case 'minimal':
    case 'low':
      return 'flash'
    case 'high':
      return 'thinking'
    case 'standard':
      return 'auto'
  }
}

export function isRequestMode(value: string | null): value is RequestMode {
  return value !== null && REQUEST_MODES.has(value as RequestMode)
}
