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
 * one less React-version coupling). Renders pins, polylines and routes inside
 * the widget surface.
 */
const react_1 = require("react");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
function MapBlock({ payload, height = 260 }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const holder = (0, react_1.useRef)(null);
    const mapRef = (0, react_1.useRef)(null);
    const layerRef = (0, react_1.useRef)(null);
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
                const pins = (payload.pins ?? []).concat(payload.stops ?? []);
                for (const pin of pins) {
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
                const line = payload.route ?? (payload.stops ?? []).map((s) => [s.lat, s.lng]);
                if (line.length >= 2) {
                    L.polyline(line, { color: accent, weight: 3.5, opacity: 0.85, dashArray: payload.mode ? '7 6' : undefined }).addTo(layer);
                }
                const pts = pins.map((p) => [p.lat, p.lng]);
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
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title, right: payload.mode }), (0, jsx_runtime_1.jsx)("div", { ref: holder, style: { height, borderRadius: 14, overflow: 'hidden', border: `1px solid ${theme.border}`, background: theme.raised } }), (0, jsx_runtime_1.jsx)("style", { children: `.leaflet-container { font-family: inherit; background: ${theme.raised} }` })] }));
}
