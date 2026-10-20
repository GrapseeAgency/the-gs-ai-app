/**
 * The GenUI web library — public surface.
 *
 * Consumers (the chat page, the gallery) import from here only:
 *   - MessageContent: a full assistant answer, prose + widgets inline
 *   - WidgetRenderer: one validated payload -> one themed component
 *   - parseStream: markdown -> segments (for custom hosts)
 *   - WidgetSurface / tokens: the theme system
 */

export { MessageContent, type Citation } from './MessageContent'
export { WidgetRenderer } from './WidgetRenderer'
export { parseStream, type Segment } from './stream'
export { WidgetSurface, useWidgetIdentity, type WidgetIdentity } from './theme/WidgetSurface'
export { surfaceStyle, type SurfaceStyle } from './theme/surfaces'
export {
  themeFor,
  accentOf,
  paletteById,
  rampOf,
  alpha,
  inkOn,
  hueAt,
  DATA_HUES,
  PALETTES,
  SURFACES,
  TYPE,
  SPACE,
  RADIUS,
  FONT,
  type GsTheme,
  type Palette,
  type Surface,
} from './theme/tokens'
export { REGISTRY, rendererFor } from './registry'
export { evaluateExpr, formatOutput } from './calc/evaluate'
export type { PromptHandler, WidgetComponent } from './registry'
export * from './types'
