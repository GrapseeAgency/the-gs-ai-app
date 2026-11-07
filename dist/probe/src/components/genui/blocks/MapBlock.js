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
exports.MapBlock = MapBlock;
const jsx_runtime_1 = require("react/jsx-runtime");
/**
 * MapBlock — Leaflet with OSM tiles, driven imperatively (no react-leaflet —
 * one less React-version coupling). Renders pins, polylines, routes, place
 * cards, and turn-by-turn itineraries inside the widget surface.
 */
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
function normPin(raw) {
    if (!raw || typeof raw !== 'object')
        return null;
    const o = raw;
    const coords = Array.isArray(o.coordinates ?? o.position) ? (o.coordinates ?? o.position) : null;
    const lat = Number(o.lat ?? o.latitude ?? (coords ? coords[0] : NaN));
    const lng = Number(o.lng ?? o.lon ?? o.longitude ?? (coords ? coords[1] : NaN));
    if (!Number.isFinite(lat) || !Number.isFinite(lng) || lat < -90 || lat > 90 || lng < -180 || lng > 180)
        return null;
    const label = String(o.label ?? o.title ?? o.name ?? o.place ?? '').trim();
    const detail = String(o.detail ?? o.description ?? o.address ?? o.subtitle ?? o.category ?? '').trim();
    return { lat, lng, label: label || undefined, detail: detail || undefined };
}
function normStep(raw) {
    if (typeof raw === 'string' && raw.trim())
        return { instruction: raw.trim() };
    if (!raw || typeof raw !== 'object')
        return null;
    const o = raw;
    const instruction = String(o.instruction ?? o.text ?? o.step ?? o.title ?? o.description ?? '').trim();
    if (!instruction)
        return null;
    const distance = String(o.distance ?? o.dist ?? o.duration ?? o.time ?? '').trim();
    return { instruction, distance: distance || undefined };
}
function MapBlock({ payload, height = 260 }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const holder = (0, react_1.useRef)(null);
    const mapRef = (0, react_1.useRef)(null);
    const layerRef = (0, react_1.useRef)(null);
    const normalized = (0, react_1.useMemo)(() => {
        const rawPins = payload.pins ?? payload.markers ?? payload.locations ?? payload.places ?? [];
        const pins = rawPins.map(normPin).filter((p) => p !== null);
        if (pins.length === 0) {
            const single = normPin(payload);
            if (single)
                pins.push(single);
        }
        const rawStops = payload.stops ?? payload.waypoints ?? payload.itinerary ?? [];
        const stops = rawStops.map(normPin).filter((p) => p !== null);
        const rawSteps = payload.steps ?? payload.directions ?? [];
        const steps = rawSteps.map(normStep).filter((s) => s !== null);
        return { pins, stops, steps };
    }, [payload]);
    (0, react_1.useEffect)(() => {
        let cancelled = false;
        const setup = async () => {
            const L = (await Promise.resolve().then(() => __importStar(require('leaflet')))).default;
            if (cancelled || !holder.current || mapRef.current)
                return;
            const map = L.map(holder.current, { scrollWheelZoom: false, attributionControl: false });
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map);
            layerRef.current = L.layerGroup().addTo(map);
            mapRef.current = map;
            const draw = () => {
                const layer = layerRef.current;
                if (!layer)
                    return;
                layer.clearLayers();
                const allPins = normalized.pins.concat(normalized.stops);
                for (const pin of allPins) {
                    const marker = L.circleMarker([pin.lat, pin.lng], {
                        radius: 7,
                        color: '#fff',
                        weight: 2,
                        fillColor: accent,
                        fillOpacity: 1,
                    });
                    marker.bindTooltip(pin.label ?? '', { direction: 'top', offset: [0, -8] });
                    layer.addLayer(marker);
                }
                const line = payload.route ?? normalized.stops.map((s) => [s.lat, s.lng]);
                if (line.length >= 2) {
                    L.polyline(line, { color: accent, weight: 3.5, opacity: 0.85, dashArray: payload.mode ? '7 6' : undefined }).addTo(layer);
                }
                const pts = allPins.map((p) => [p.lat, p.lng]);
                if (pts.length > 1)
                    map.fitBounds(L.latLngBounds(pts).pad(0.25));
                else if (pts.length === 1)
                    map.setView(pts[0], payload.zoom ?? 13);
                else
                    map.setView(payload.center ?? [23.8, 90.4], payload.zoom ?? 5);
            };
            draw();
            mapRef.current.__gsPayload = payload;
            mapRef.current.invalidateSize();
        };
        setup();
        return () => {
            cancelled = true;
            mapRef.current?.remove();
            mapRef.current = null;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);
    const placeCards = normalized.pins.length > 0 ? normalized.pins : normalized.stops;
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title, right: payload.mode }), (0, jsx_runtime_1.jsx)("div", { ref: holder, style: { height, borderRadius: 14, overflow: 'hidden', border: `1px solid ${theme.border}`, background: theme.raised } }), placeCards.some((p) => p.label || p.detail) ? ((0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', gap: 8, overflowX: 'auto', paddingTop: 10 }, children: placeCards.map((p, idx) => ((0, jsx_runtime_1.jsxs)("button", { type: "button", onClick: () => mapRef.current?.setView([p.lat, p.lng], Math.max(payload.zoom ?? 14, 14)), style: {
                        flex: '0 0 auto',
                        maxWidth: 220,
                        textAlign: 'left',
                        padding: '8px 10px',
                        borderRadius: tokens_1.RADIUS.md,
                        border: `1px solid ${theme.border}`,
                        background: theme.raised,
                        cursor: 'pointer',
                    }, children: [(0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, fontWeight: 700, color: theme.text }, children: p.label ?? `Stop ${idx + 1}` }), p.detail ? ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: 11, color: theme.text3, marginTop: 2, lineHeight: 1.35 }, children: p.detail })) : null] }, idx))) })) : null, normalized.steps.length > 0 ? ((0, jsx_runtime_1.jsx)("div", { style: { marginTop: 10, display: 'flex', flexDirection: 'column', gap: 4 }, children: normalized.steps.map((s, idx) => ((0, jsx_runtime_1.jsxs)("div", { style: {
                        display: 'flex',
                        alignItems: 'baseline',
                        gap: 8,
                        padding: '5px 0',
                        fontSize: tokens_1.TYPE.micro,
                        borderBottom: `1px solid ${(0, tokens_1.alpha)(theme.dark ? '#FFFFFF' : '#14171C', 0.06)}`,
                    }, children: [(0, jsx_runtime_1.jsxs)("span", { style: { color: accent, fontWeight: 700, minWidth: 16 }, children: [idx + 1, "."] }), (0, jsx_runtime_1.jsx)("span", { style: { color: theme.text, flex: 1 }, children: s.instruction }), s.distance ? (0, jsx_runtime_1.jsx)("span", { style: { color: theme.text3, fontFamily: 'ui-monospace, monospace' }, children: s.distance }) : null] }, idx))) })) : null, (0, jsx_runtime_1.jsx)("style", { children: `.leaflet-container { font-family: inherit; background: ${theme.raised} }` })] }));
}
