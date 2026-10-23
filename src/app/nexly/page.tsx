import fs from 'node:fs'
import path from 'node:path'
import { NexlyShowcase } from '@/components/nexly/NexlyShowcase'

/**
 * The sprite is read on the server and rendered once, so every <use> resolves
 * on first paint. No 5,000-component JS bundle, no icon flash.
 */
export default function NexlyPage() {
  const sprite = fs.readFileSync(
    path.join(process.cwd(), 'public/icons/gs-sprite.svg'),
    'utf8'
  )

  return (
    <>
      <div
        aria-hidden="true"
        style={{ position: 'absolute', width: 0, height: 0, overflow: 'hidden' }}
        dangerouslySetInnerHTML={{ __html: sprite }}
      />
      <NexlyShowcase />
    </>
  )
}
