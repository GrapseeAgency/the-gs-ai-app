"use strict";
'use client';
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.tiltLabel = tiltLabel;
exports.Stage3D = Stage3D;
exports.axisGround = axisGround;
exports.ambientLights = ambientLights;
exports.accentMaterial = accentMaterial;
const jsx_runtime_1 = require("react/jsx-runtime");
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
const react_1 = require("react");
function tiltLabel(mode) {
    switch (mode) {
        case 'xr':
            return 'device tilt live — WebXR inline (viewer space)';
        case 'sensor':
            return 'device tilt live — motion sensor';
        case 'needs-permission':
            return 'tap “enable tilt” to allow the motion sensor';
        case 'unsupported':
            return 'no device sensors — drag to orbit';
        default:
            return 'detecting device sensors…';
    }
}
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
function Stage3D({ title, spec }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const holder = (0, react_1.useRef)(null);
    const [ready, setReady] = (0, react_1.useState)(false);
    const [failed, setFailed] = (0, react_1.useState)(false);
    const [tilt, setTilt] = (0, react_1.useState)('off');
    const [tiltTick, setTiltTick] = (0, react_1.useState)(0);
    const permissionRef = (0, react_1.useRef)(null);
    (0, react_1.useEffect)(() => {
        let disposed = false;
        let cleanup;
        const boot = async () => {
            try {
                const THREE = await Promise.resolve().then(() => __importStar(require('three')));
                const { OrbitControls } = await Promise.resolve().then(() => __importStar(require('three/examples/jsm/controls/OrbitControls.js')));
                if (disposed || !holder.current)
                    return;
                const el = holder.current;
                const width = el.clientWidth;
                const height = spec.height;
                const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
                renderer.setSize(width, height);
                renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1));
                el.appendChild(renderer.domElement);
                const scene = new THREE.Scene();
                const camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 300);
                camera.position.set(4.2, 3.2, 5.6);
                const controls = new OrbitControls(camera, renderer.domElement);
                controls.enableDamping = true;
                controls.target.set(0, 0.4, 0);
                // --- device parallax: rotates the SCENE, never the camera, so
                // OrbitControls and the device tilt compose instead of fighting.
                const parallax = { yaw: 0, pitch: 0, tYaw: 0, tPitch: 0 };
                let cleanupParallax = null;
                let pendingPermission = null;
                const yawOfQ = (q) => Math.atan2(2 * (q.w * q.y + q.x * q.z), 1 - 2 * (q.y * q.y + q.z * q.z));
                const pitchOfQ = (q) => Math.asin(Math.max(-1, Math.min(1, 2 * (q.w * q.x - q.z * q.y))));
                const bootParallax = async () => {
                    const xr = navigator.xr;
                    let xrLive = false;
                    if (xr) {
                        try {
                            // inline: guaranteed creatable, no permissions policy, no user
                            // activation; viewer reference space always succeeds (W3C §14.1)
                            const session = await xr.requestSession('inline');
                            const ref = await session.requestReferenceSpace('viewer');
                            xrLive = true;
                            if (disposed) {
                                session.end().catch(() => { });
                                return;
                            }
                            setTilt('xr');
                            // relative to the session-start orientation: a subtle, clamped
                            // yaw/pitch delta — parallax, not a teleporting camera
                            let first = null;
                            const poll = () => {
                                session.requestAnimationFrame((_t, frame) => {
                                    const pose = frame.getViewerPose(ref);
                                    if (pose) {
                                        const q = pose.transform.orientation;
                                        const y = yawOfQ(q);
                                        const pt = pitchOfQ(q);
                                        if (!first)
                                            first = { yaw: y, pitch: pt };
                                        parallax.tYaw = Math.max(-0.5, Math.min(0.5, y - first.yaw));
                                        parallax.tPitch = Math.max(-0.35, Math.min(0.35, pt - first.pitch));
                                    }
                                    if (!disposed)
                                        poll();
                                });
                            };
                            poll();
                            cleanupParallax = () => {
                                disposed = true;
                                session.end().catch(() => { });
                            };
                        }
                        catch {
                            /* inline refused → fall through to the sensor path */
                        }
                    }
                    if (!xrLive && typeof window !== 'undefined' && 'DeviceOrientationEvent' in window) {
                        const D = window.DeviceOrientationEvent;
                        const subscribe = () => {
                            const handler = (e) => {
                                if (e.alpha == null && e.beta == null && e.gamma == null)
                                    return;
                                const a = ((e.alpha ?? 0) * Math.PI) / 180;
                                const b = (((e.beta ?? 0) - 45) * Math.PI) / 180;
                                const g = ((e.gamma ?? 0) * Math.PI) / 180;
                                parallax.tYaw = Math.max(-0.5, Math.min(0.5, Math.sin(a) * 0.35 + g * 0.4));
                                parallax.tPitch = Math.max(-0.35, Math.min(0.35, b * 0.35));
                            };
                            window.addEventListener('deviceorientation', handler);
                            setTilt('sensor');
                            cleanupParallax = () => window.removeEventListener('deviceorientation', handler);
                        };
                        try {
                            if (typeof D?.requestPermission === 'function') {
                                // iOS 13+ gate — MUST run from the tap gesture; the hint row
                                // shows the button and re-enters here on click
                                setTilt('needs-permission');
                                pendingPermission = async () => {
                                    try {
                                        if ((await D.requestPermission?.()) === 'granted')
                                            subscribe();
                                        else
                                            setTilt('unsupported');
                                    }
                                    catch {
                                        setTilt('unsupported');
                                    }
                                    pendingPermission = null;
                                    setTiltTick((t) => t + 1);
                                };
                            }
                            else {
                                subscribe();
                            }
                        }
                        catch {
                            setTilt('unsupported');
                        }
                    }
                    if (!xrLive && !cleanupParallax && !pendingPermission) {
                        // desktops and anything without sensors: honest label, orbit-only
                        setTilt('unsupported');
                    }
                };
                permissionRef.current = () => pendingPermission?.();
                void bootParallax();
                const themed = { accent, border: theme.border, dark: theme.dark };
                const view = spec.build(THREE, scene, themed);
                let raf = 0;
                const clock = new THREE.Clock();
                const loop = () => {
                    controls.update();
                    parallax.yaw += (parallax.tYaw - parallax.yaw) * 0.08;
                    parallax.pitch += (parallax.tPitch - parallax.pitch) * 0.08;
                    scene.rotation.y = parallax.yaw;
                    scene.rotation.x = parallax.pitch;
                    view.update?.(clock.getElapsedTime());
                    renderer.render(scene, camera);
                    raf = requestAnimationFrame(loop);
                };
                loop();
                setReady(true);
                cleanup = () => {
                    cancelAnimationFrame(raf);
                    cleanupParallax?.();
                    permissionRef.current = null;
                    view.dispose?.();
                    controls.dispose();
                    renderer.dispose();
                    renderer.domElement.remove();
                };
            }
            catch {
                if (!disposed)
                    setFailed(true);
            }
        };
        boot();
        return () => {
            disposed = true;
            cleanup?.();
        };
    }, []);
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: title }), (0, jsx_runtime_1.jsxs)("div", { ref: holder, style: {
                    width: '100%',
                    height: spec.height,
                    borderRadius: tokens_1.RADIUS.md,
                    border: `1px solid ${theme.border}`,
                    background: theme.dark ? 'radial-gradient(120% 120% at 30% 20%, rgba(255,255,255,0.05), rgba(0,0,0,0.35))' : 'rgba(0,0,0,0.05)',
                    overflow: 'hidden',
                    position: 'relative',
                    touchAction: 'none',
                }, children: [!ready && !failed && ((0, jsx_runtime_1.jsx)("div", { style: { position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: tokens_1.TYPE.label, color: theme.text3 }, children: "loading WebGL\u2026" })), failed && ((0, jsx_runtime_1.jsx)("div", { style: { position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: tokens_1.TYPE.label, color: theme.text3 }, children: "WebGL unavailable on this device" }))] }), (0, jsx_runtime_1.jsxs)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 6, display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }, children: [(0, jsx_runtime_1.jsxs)("span", { children: [spec.hint ? `${spec.hint} · ` : '', tiltLabel(tilt)] }), tilt === 'needs-permission' && ((0, jsx_runtime_1.jsx)("button", { type: "button", onClick: () => {
                            permissionRef.current?.();
                            setTiltTick((t) => t + 1);
                        }, style: {
                            font: 'inherit',
                            fontSize: tokens_1.TYPE.micro,
                            color: accent,
                            background: 'transparent',
                            border: `1px solid ${accent}`,
                            borderRadius: 999,
                            padding: '1px 10px',
                            cursor: 'pointer',
                        }, children: "enable tilt" })), (0, jsx_runtime_1.jsx)("span", { "data-tilt-tick": tiltTick })] })] }));
}
/** Shared scene helpers used by every view. */
function axisGround(THREE, scene, themed) {
    const grid = new THREE.GridHelper(10, 20, new THREE.Color(themed.border), new THREE.Color(themed.border));
    const mat = grid.material;
    mat.transparent = true;
    mat.opacity = 0.25;
    scene.add(grid);
}
function ambientLights(THREE, scene, accent) {
    scene.add(new THREE.AmbientLight(0xffffff, 0.65));
    const key = new THREE.DirectionalLight(0xffffff, 1.1);
    key.position.set(4, 6, 3);
    scene.add(key);
    const rim = new THREE.PointLight(new THREE.Color(accent), 1.4, 30);
    rim.position.set(-5, 3, -4);
    scene.add(rim);
}
function accentMaterial(THREE, accent, opacity = 0.92) {
    return new THREE.MeshStandardMaterial({ color: new THREE.Color(accent), roughness: 0.35, metalness: 0.15, transparent: opacity < 1, opacity });
}
