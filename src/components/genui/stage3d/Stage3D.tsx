'use client'

/**
 * Stage3D — the shared WebGL host. three.js + OrbitControls load as a lazy
 * chunk on first render; every view builds into a supplied Scene and gets
 * drag-rotate / wheel-zoom / touch for free. A plain-canvas poster renders
 * until the chunk arrives so streaming never blanks.
 *
 * DEVICE PARALLAX (Tier 2, labelled in the hint):
 *   - WebXR inline sessions (W3C WebXR §14.1: guaranteed creatable, no
 *     permissions-policy needed — only immersive modes require
 *     `xr-spatial-tracking`; §2.1: phones report the phone's own gyroscopic
 *     pose, desktops simply report no pose) drive a `viewer`-space look
 *     direction that nudges the SCENE rotation — real "peek around" parallax
 *     on Android Chrome, silent no-op where no pose arrives.
 *   - No navigator.xr (iOS Safari): DeviceOrientation fallback. iOS 13+
 *     gates the sensor behind a user-gesture permission — the hint row grows
 *     a "enable tilt" button exactly for that case; elsewhere it just
 *     subscribes.
 *   - OrbitControls keep working the whole time: parallax applies to the
 *     scene group, never fights the camera.
 */

import { useEffect, useRef, useState } from 'react'

type XRLike = { requestSession(mode: string, init?: Record<string, unknown>): Promise<XRSessionLike> }
type XRSessionLike = {
  requestReferenceSpace(type: string): Promise<unknown>
  requestAnimationFrame(cb: (time: number, frame: XRFrameLike) => void): number
  end(): Promise<void>
}
type XRFrameLike = {
  getViewerPose(space: unknown): { transform: { orientation: { x: number; y: number; z: number; w: number } } } | null
}
type DeviceOrientationEventStatic = {
  requestPermission?: () => Promise<'granted' | 'denied'>
}

export type TiltMode = 'off' | 'xr' | 'sensor' | 'needs-permission' | 'unsupported'

export function tiltLabel(mode: TiltMode): string {
  switch (mode) {
    case 'xr':
      return 'device tilt live — WebXR inline (viewer space)'
    case 'sensor':
      return 'device tilt live — motion sensor'
    case 'needs-permission':
      return 'tap “enable tilt” to allow the motion sensor'
    case 'unsupported':
      return 'no device sensors — drag to orbit'
    default:
      return 'detecting device sensors…'
  }
}
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
  const [tilt, setTilt] = useState<TiltMode>('off')
  const [tiltTick, setTiltTick] = useState(0)
  const permissionRef = useRef<(() => void) | null>(null)

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

        // --- device parallax: rotates the SCENE, never the camera, so
        // OrbitControls and the device tilt compose instead of fighting.
        const parallax = { yaw: 0, pitch: 0, tYaw: 0, tPitch: 0 }
        let cleanupParallax: (() => void) | null = null
        let pendingPermission: (() => void) | null = null
        const yawOfQ = (q: { x: number; y: number; z: number; w: number }) =>
          Math.atan2(2 * (q.w * q.y + q.x * q.z), 1 - 2 * (q.y * q.y + q.z * q.z))
        const pitchOfQ = (q: { x: number; y: number; z: number; w: number }) =>
          Math.asin(Math.max(-1, Math.min(1, 2 * (q.w * q.x - q.z * q.y))))
        const bootParallax = async () => {
          const xr = (navigator as Navigator & { xr?: XRLike }).xr
          let xrLive = false
          if (xr) {
            try {
              // inline: guaranteed creatable, no permissions policy, no user
              // activation; viewer reference space always succeeds (W3C §14.1)
              const session = await xr.requestSession('inline')
              const ref = await session.requestReferenceSpace('viewer')
              xrLive = true
              if (disposed) {
                session.end().catch(() => {})
                return
              }
              setTilt('xr')
              // relative to the session-start orientation: a subtle, clamped
              // yaw/pitch delta — parallax, not a teleporting camera
              let first: { yaw: number; pitch: number } | null = null
              const poll = () => {
                session.requestAnimationFrame((_t, frame) => {
                  const pose = frame.getViewerPose(ref)
                  if (pose) {
                    const q = pose.transform.orientation
                    const y = yawOfQ(q)
                    const pt = pitchOfQ(q)
                    if (!first) first = { yaw: y, pitch: pt }
                    parallax.tYaw = Math.max(-0.5, Math.min(0.5, y - first.yaw))
                    parallax.tPitch = Math.max(-0.35, Math.min(0.35, pt - first.pitch))
                  }
                  if (!disposed) poll()
                })
              }
              poll()
              cleanupParallax = () => {
                disposed = true
                session.end().catch(() => {})
              }
            } catch {
              /* inline refused → fall through to the sensor path */
            }
          }
          if (!xrLive && typeof window !== 'undefined' && 'DeviceOrientationEvent' in window) {
            const D = (window as unknown as { DeviceOrientationEvent: DeviceOrientationEventStatic }).DeviceOrientationEvent
            const subscribe = () => {
              const handler = (e: DeviceOrientationEvent) => {
                if (e.alpha == null && e.beta == null && e.gamma == null) return
                const a = ((e.alpha ?? 0) * Math.PI) / 180
                const b = (((e.beta ?? 0) - 45) * Math.PI) / 180
                const g = ((e.gamma ?? 0) * Math.PI) / 180
                parallax.tYaw = Math.max(-0.5, Math.min(0.5, Math.sin(a) * 0.35 + g * 0.4))
                parallax.tPitch = Math.max(-0.35, Math.min(0.35, b * 0.35))
              }
              window.addEventListener('deviceorientation', handler)
              setTilt('sensor')
              cleanupParallax = () => window.removeEventListener('deviceorientation', handler)
            }
            try {
              if (typeof D?.requestPermission === 'function') {
                // iOS 13+ gate — MUST run from the tap gesture; the hint row
                // shows the button and re-enters here on click
                setTilt('needs-permission')
                pendingPermission = async () => {
                  try {
                    if ((await D.requestPermission?.()) === 'granted') subscribe()
                    else setTilt('unsupported')
                  } catch {
                    setTilt('unsupported')
                  }
                  pendingPermission = null
                  setTiltTick((t) => t + 1)
                }
              } else {
                subscribe()
              }
            } catch {
              setTilt('unsupported')
            }
          }
          if (!xrLive && !cleanupParallax && !pendingPermission) {
            // desktops and anything without sensors: honest label, orbit-only
            setTilt('unsupported')
          }
        }
        permissionRef.current = () => pendingPermission?.()
        void bootParallax()

        const themed = { accent, border: theme.border, dark: theme.dark }
        const view = spec.build(THREE, scene, themed)
        let raf = 0
        const clock = new THREE.Clock()
        const loop = () => {
          controls.update()
          parallax.yaw += (parallax.tYaw - parallax.yaw) * 0.08
          parallax.pitch += (parallax.tPitch - parallax.pitch) * 0.08
          scene.rotation.y = parallax.yaw
          scene.rotation.x = parallax.pitch
          view.update?.(clock.getElapsedTime())
          renderer.render(scene, camera)
          raf = requestAnimationFrame(loop)
        }
        loop()
        setReady(true)
        cleanup = () => {
          cancelAnimationFrame(raf)
          cleanupParallax?.()
          permissionRef.current = null
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
      <div style={{ fontSize: TYPE.micro, color: theme.text3, marginTop: 6, display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <span>
          {spec.hint ? `${spec.hint} · ` : ''}
          {tiltLabel(tilt)}
        </span>
        {tilt === 'needs-permission' && (
          <button
            type="button"
            onClick={() => {
              permissionRef.current?.()
              setTiltTick((t) => t + 1)
            }}
            style={{
              font: 'inherit',
              fontSize: TYPE.micro,
              color: accent,
              background: 'transparent',
              border: `1px solid ${accent}`,
              borderRadius: 999,
              padding: '1px 10px',
              cursor: 'pointer',
            }}
          >
            enable tilt
          </button>
        )}
        <span data-tilt-tick={tiltTick} />
      </div>
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
