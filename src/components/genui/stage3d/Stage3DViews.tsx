'use client'

/**
 * The eight 3D views. Each returns a StageSpec — a builder that fills the
 * scene from the validated payload. All of them orbit/zoom via Stage3D.
 */

type Rec<K extends string = string, V = unknown> = Record<K, V>
import { evaluateExpr } from '../calc/evaluate'
import { Stage3D, axisGround, ambientLights, accentMaterial, type StageSpec } from './Stage3D'

interface Node3 { id: string; label: string; x: number; y: number; z: number; r?: number; group?: string }

type Payload3D = Rec<string, unknown> & {
  view?: string
  title?: string
  nodes?: Node3[]
  bonds?: Array<[number, number]>
  z?: string
  span?: number
  spin?: boolean
  note?: string
}

export function Stage3DWidget({ payload }: { payload: Payload3D }) {
  const view = payload.view ?? 'scene'
  const spec = buildSpec(view, payload)
  return <Stage3D title={payload.title} spec={spec} />
}

function buildSpec(view: string, p: Payload3D): StageSpec {
  const height = 280
  const nodes = (p.nodes ?? []).slice(0, 60)
  const bonds = (p.bonds ?? []).slice(0, 200)

  if (view === 'scatter3d') {
    return {
      height,
      hint: 'drag to orbit · wheel to zoom',
      build: (THREE, scene, themed) => {
        ambientLights(THREE, scene, themed.accent)
        axisGround(THREE, scene, themed)
        const geo = new THREE.SphereGeometry(1, 20, 20)
        nodes.forEach((n) => {
          const mesh = new THREE.Mesh(geo, accentMaterial(THREE, themed.accent, 0.95))
          mesh.position.set(n.x, n.y, n.z)
          mesh.scale.setScalar(n.r && n.r > 0 ? Math.min(0.6, n.r * 0.2) : 0.14)
          scene.add(mesh)
        })
        return {}
      },
    }
  }

  if (view === 'surface3d') {
    return {
      height,
      hint: `z = ${String(p.z ?? 'sin(x)*cos(y)')} · orbit to explore`,
      build: (THREE, scene, themed) => {
        ambientLights(THREE, scene, themed.accent)
        const span = Math.max(0.5, Math.min(10, p.span ?? 2))
        const seg = 60
        const geo = new THREE.PlaneGeometry(span * 2, span * 2, seg, seg)
        const pos = geo.attributes.position
        const sample = (x: number, y: number): number => {
          try {
            return evaluateExpr(String(p.z ?? 'sin(x)*cos(y)'), { x, y })
          } catch {
            return 0
          }
        }
        let zMax = 1
        const raw: number[] = []
        for (let i = 0; i < pos.count; i++) {
          const x = pos.getX(i)
          const y = pos.getY(i)
          const z = sample(x / span, y / span)
          raw.push(z)
          if (Number.isFinite(z)) zMax = Math.max(zMax, Math.abs(z))
        }
        for (let i = 0; i < pos.count; i++) pos.setZ(i, raw[i] / zMax)
        geo.computeVertexNormals()
        const mesh = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ color: new THREE.Color(themed.accent), roughness: 0.4, metalness: 0.1, side: THREE.DoubleSide, transparent: true, opacity: 0.94 }))
        mesh.rotation.x = -Math.PI / 2
        mesh.position.y = 0.5
        scene.add(mesh)
        const wire = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ color: new THREE.Color(themed.accent), wireframe: true, transparent: true, opacity: 0.14 }))
        wire.rotation.x = -Math.PI / 2
        wire.position.y = 0.502
        scene.add(wire)
        return {}
      },
    }
  }

  if (view === 'molecule') {
    return {
      height,
      hint: 'ball-and-stick · drag to rotate',
      build: (THREE, scene, themed) => {
        ambientLights(THREE, scene, themed.accent)
        const palette = ['#E03131', '#4C6FFF', '#9C36B5', '#F08C00', '#0CA678', '#1098AD']
        const sphereGeo = new THREE.SphereGeometry(1, 24, 24)
        nodes.forEach((n, i) => {
          const mesh = new THREE.Mesh(sphereGeo, new THREE.MeshStandardMaterial({ color: new THREE.Color(n.group || palette[i % palette.length]), roughness: 0.3, metalness: 0.25 }))
          mesh.position.set(n.x, n.y, n.z)
          mesh.scale.setScalar(n.r && n.r > 0 ? n.r : 0.42)
          scene.add(mesh)
        })
        const stickMat = new THREE.MeshStandardMaterial({ color: 0xb0b8c4, roughness: 0.5 })
        for (const [a, b] of bonds) {
          const na = nodes[a]
          const nb = nodes[b]
          if (!na || !nb) continue
          const va = new THREE.Vector3(na.x, na.y, na.z)
          const vb = new THREE.Vector3(nb.x, nb.y, nb.z)
          const dir = vb.clone().sub(va)
          const len = dir.length()
          const cyl = new THREE.Mesh(new THREE.CylinderGeometry(0.09, 0.09, len, 10), stickMat)
          cyl.position.copy(va.clone().add(vb).multiplyScalar(0.5))
          cyl.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir.normalize())
          scene.add(cyl)
        }
        return {
          update: (t) => {
            if (p.spin === false) return
            scene.rotation.y = t * 0.25
          },
        }
      },
    }
  }

  if (view === 'cad') {
    return {
      height,
      hint: 'parametric part — orbit; the joint articulates live',
      build: (THREE, scene, themed) => {
        ambientLights(THREE, scene, themed.accent)
        axisGround(THREE, scene, themed)
        const mat = accentMaterial(THREE, themed.accent, 0.95)
        const group = new THREE.Group()
        const base = new THREE.Mesh(new THREE.BoxGeometry(2.4, 0.5, 1.6), mat)
        base.position.y = 0.25
        group.add(base)
        const tower = new THREE.Mesh(new THREE.CylinderGeometry(0.34, 0.42, 1.5, 28), mat)
        tower.position.set(0.7, 1.1, 0)
        group.add(tower)
        const arm = new THREE.Mesh(new THREE.BoxGeometry(1.7, 0.28, 0.42), mat)
        arm.position.set(-0.25, 1.9, 0)
        group.add(arm)
        const joint = new THREE.Mesh(new THREE.SphereGeometry(0.26, 20, 20), accentMaterial(THREE, themed.accent))
        joint.position.set(-1.05, 1.9, 0)
        group.add(joint)
        scene.add(group)
        return {
          update: (t) => {
            joint.position.y = 1.9 + Math.sin(t * 1.4) * 0.18
            arm.rotation.z = Math.sin(t * 1.4) * 0.14
          },
        }
      },
    }
  }

  if (view === 'anatomy') {
    return {
      height,
      hint: 'layered translucent shells — orbit to peel the system',
      build: (THREE, scene, themed) => {
        ambientLights(THREE, scene, themed.accent)
        const layers: Array<[number, string, number]> = [
          [1.5, '#B08968', 0.32],
          [1.1, '#D6336C', 0.4],
          [0.72, '#E8590C', 0.72],
          [0.34, '#4C6FFF', 0.95],
        ]
        layers.forEach(([r, color, opacity], i) => {
          const mesh = new THREE.Mesh(
            new THREE.SphereGeometry(r, 40, 28),
            new THREE.MeshStandardMaterial({ color: new THREE.Color(color), transparent: true, opacity, roughness: 0.55, depthWrite: opacity > 0.7 })
          )
          mesh.position.y = 1.6
          mesh.userData.spin = 0.12 + i * 0.06
          scene.add(mesh)
        })
        return {
          update: (t) => {
            scene.traverse((o) => {
              if (o.userData.spin) o.rotation.y = t * (o.userData.spin as number)
            })
          },
        }
      },
    }
  }

  // vr / ar / scene — the magic-window stage (headset sessions are not
  // grantable from inside chat; this is the honest version)
  const magic = view === 'vr' || view === 'ar'
  return {
    height,
    hint: magic ? `${view.toUpperCase()} magic-window (drag to look) — headset mode needs a native client` : 'drag to orbit · wheel to zoom',
    build: (THREE, scene, themed) => {
      ambientLights(THREE, scene, themed.accent)
      axisGround(THREE, scene, themed)
      const knot = new THREE.Mesh(new THREE.TorusKnotGeometry(0.9, 0.28, 160, 24), accentMaterial(THREE, themed.accent, 0.9))
      knot.position.y = 1.6
      scene.add(knot)
      const pedestal = new THREE.Mesh(new THREE.CylinderGeometry(1.1, 1.3, 0.35, 40), accentMaterial(THREE, themed.accent, 0.5))
      pedestal.position.y = 0.18
      scene.add(pedestal)
      const n = 260
      const arr = new Float32Array(n * 3)
      for (let i = 0; i < n; i++) {
        arr[i * 3] = (Math.random() - 0.5) * 8
        arr[i * 3 + 1] = Math.random() * 5
        arr[i * 3 + 2] = (Math.random() - 0.5) * 8
      }
      const sparkGeo = new THREE.BufferGeometry()
      sparkGeo.setAttribute('position', new THREE.BufferAttribute(arr, 3))
      const sparks = new THREE.Points(sparkGeo, new THREE.PointsMaterial({ color: new THREE.Color(themed.accent), size: 0.045, transparent: true, opacity: 0.7 }))
      scene.add(sparks)
      return {
        update: (t) => {
          knot.rotation.x = t * 0.4
          knot.rotation.y = t * 0.6
          knot.position.y = 1.6 + Math.sin(t * 1.2) * 0.12
          sparks.rotation.y = t * 0.05
        },
      }
    },
  }
}
