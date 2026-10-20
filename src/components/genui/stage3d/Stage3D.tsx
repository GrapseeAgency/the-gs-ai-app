'use client'

/**
 * Stage3D — the shared WebGL host. three.js + OrbitControls load as a lazy
 * chunk on first render; every view builds into a supplied Scene and gets
 * drag-rotate / wheel-zoom / touch for free. A plain-canvas poster renders
 * until the chunk arrives so streaming never blanks.
 */

import { useEffect, useRef, useState } from 'react'
import { RADIUS, TYPE } from '../theme/tokens'
import { useWidgetIdentity } from '../theme/WidgetSurface'
import { TitleRow } from '../primitives/TitleRow'

export interface StageSpec {
  height: number
  build: (three: typeof import('three'), scene: import('three').Scene, theme: { accent: string; border: string; dark: boolean }) => { update?: (t: number) => void; dispose?: () => void }
  hint?: string
}

export function Stage3D({ title, spec }: { title?: string; spec: StageSpec }) {
  const { accent, theme } = useWidgetIdentity()
  const holder = useRef<HTMLDivElement>(null)
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let disposed = false
    let cleanup: (() => void) | undefined
    const boot = async () => {
      try {
        const THREE = await import('three')
        const { OrbitControls } = await import('three/examples/jsm/controls/OrbitControls.js')
        if (disposed || !holder.current) return
        const el = holder.current
        const width = el.clientWidth
        const height = spec.height
        const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
        renderer.setSize(width, height)
        renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1))
        el.appendChild(renderer.domElement)
        const scene = new THREE.Scene()
        const camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 300)
        camera.position.set(4.2, 3.2, 5.6)
        const controls = new OrbitControls(camera, renderer.domElement)
        controls.enableDamping = true
        controls.target.set(0, 0.4, 0)
        const themed = { accent, border: theme.border, dark: theme.dark }
        const view = spec.build(THREE, scene, themed)
        let raf = 0
        const clock = new THREE.Clock()
        const loop = () => {
          controls.update()
          view.update?.(clock.getElapsedTime())
          renderer.render(scene, camera)
          raf = requestAnimationFrame(loop)
        }
        loop()
        setReady(true)
        cleanup = () => {
          cancelAnimationFrame(raf)
          view.dispose?.()
          controls.dispose()
          renderer.dispose()
          renderer.domElement.remove()
        }
      } catch {
        if (!disposed) setFailed(true)
      }
    }
    boot()
    return () => {
      disposed = true
      cleanup?.()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div>
      <TitleRow title={title} />
      <div
        ref={holder}
        style={{
          width: '100%',
          height: spec.height,
          borderRadius: RADIUS.md,
          border: `1px solid ${theme.border}`,
          background: theme.dark ? 'radial-gradient(120% 120% at 30% 20%, rgba(255,255,255,0.05), rgba(0,0,0,0.35))' : 'rgba(0,0,0,0.05)',
          overflow: 'hidden',
          position: 'relative',
          touchAction: 'none',
        }}
      >
        {!ready && !failed && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: TYPE.label, color: theme.text3 }}>
            loading WebGL…
          </div>
        )}
        {failed && (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: TYPE.label, color: theme.text3 }}>
            WebGL unavailable on this device
          </div>
        )}
      </div>
      {spec.hint ? <div style={{ fontSize: TYPE.micro, color: theme.text3, marginTop: 6 }}>{spec.hint}</div> : null}
    </div>
  )
}

/** Shared scene helpers used by every view. */
export function axisGround(THREE: typeof import('three'), scene: import('three').Scene, themed: { accent: string; border: string }) {
  const grid = new THREE.GridHelper(10, 20, new THREE.Color(themed.border), new THREE.Color(themed.border))
  const mat = grid.material as import('three').Material
  mat.transparent = true
  mat.opacity = 0.25
  scene.add(grid)
}

export function ambientLights(THREE: typeof import('three'), scene: import('three').Scene, accent: string) {
  scene.add(new THREE.AmbientLight(0xffffff, 0.65))
  const key = new THREE.DirectionalLight(0xffffff, 1.1)
  key.position.set(4, 6, 3)
  scene.add(key)
  const rim = new THREE.PointLight(new THREE.Color(accent), 1.4, 30)
  rim.position.set(-5, 3, -4)
  scene.add(rim)
}

export function accentMaterial(THREE: typeof import('three'), accent: string, opacity = 0.92) {
  return new THREE.MeshStandardMaterial({ color: new THREE.Color(accent), roughness: 0.35, metalness: 0.15, transparent: opacity < 1, opacity })
}
