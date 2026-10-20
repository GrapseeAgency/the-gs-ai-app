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
 */
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
function Stage3D({ title, spec }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const holder = (0, react_1.useRef)(null);
    const [ready, setReady] = (0, react_1.useState)(false);
    const [failed, setFailed] = (0, react_1.useState)(false);
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
                const themed = { accent, border: theme.border, dark: theme.dark };
                const view = spec.build(THREE, scene, themed);
                let raf = 0;
                const clock = new THREE.Clock();
                const loop = () => {
                    controls.update();
                    view.update?.(clock.getElapsedTime());
                    renderer.render(scene, camera);
                    raf = requestAnimationFrame(loop);
                };
                loop();
                setReady(true);
                cleanup = () => {
                    cancelAnimationFrame(raf);
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
                }, children: [!ready && !failed && ((0, jsx_runtime_1.jsx)("div", { style: { position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: tokens_1.TYPE.label, color: theme.text3 }, children: "loading WebGL\u2026" })), failed && ((0, jsx_runtime_1.jsx)("div", { style: { position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: tokens_1.TYPE.label, color: theme.text3 }, children: "WebGL unavailable on this device" }))] }), spec.hint ? (0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text3, marginTop: 6 }, children: spec.hint }) : null] }));
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
