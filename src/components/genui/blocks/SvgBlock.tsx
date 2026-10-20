'use client'

/**
 * SvgBlock — one inline <svg>, themed: the source's currentColor and the
 * --gs-* variables resolve inside the bubble.
 */

import { useMemo } from 'react'
import { useWidgetIdentity } from '../theme/WidgetSurface'

export function SvgBlock({ source }: { source: string }) {
  const { accent, theme } = useWidgetIdentity()
  const themed = useMemo(() => {
    const vars = `:root{--gs-text:${theme.text};--gs-text-2:${theme.text2};--gs-text-3:${theme.text3};--gs-accent:${accent};--gs-surface:${theme.card};--gs-border:${theme.border};}`
    if (/<style[^>]*>/i.test(source)) return source.replace(/<style([^>]*)>/i, `<style$1>${vars}`)
    return source.replace(/<svg([^>]*)>/i, `<svg$1><style>${vars}</style>`)
  }, [source, accent, theme])
  return (
    <div
      style={{ display: 'flex', justifyContent: 'center', lineHeight: 0 }}
      dangerouslySetInnerHTML={{ __html: themed }}
    />
  )
}
