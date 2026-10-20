"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.ContourChartView = ContourChartView;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const ChartFrame_1 = require("./ChartFrame");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/**
 * Contour — marching-squares iso-lines over a scattered scalar field.
 * Points are binned to a grid, gaps filled by nearest-bin, then the classic
 * 16-case segment table draws each level.
 */
function ContourChartView({ payload, height = 230 }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const points = (payload.points ?? []);
    const levels = Number(payload.levels ?? 6);
    const W = 320;
    const H = height;
    const grid = (0, react_1.useMemo)(() => {
        if (points.length < 9)
            return null;
        const xs = points.map((p) => p[0]);
        const ys = points.map((p) => p[1]);
        const zs = points.map((p) => p[2]);
        const x0 = Math.min(...xs), x1 = Math.max(...xs);
        const y0 = Math.min(...ys), y1 = Math.max(...ys);
        const N = 28;
        const bins = Array.from({ length: N }, () => Array(N).fill(undefined));
        for (const [x, yv, z] of points) {
            const gx = Math.min(N - 1, Math.max(0, Math.floor(((x - x0) / (x1 - x0 || 1)) * (N - 1))));
            const gy = Math.min(N - 1, Math.max(0, Math.floor(((yv - y0) / (y1 - y0 || 1)) * (N - 1))));
            bins[gy][gx] = bins[gy][gx] ?? [];
            bins[gy][gx].push(z);
        }
        const grid = bins.map((row) => row.map((cell) => (cell ? cell.reduce((a, b) => a + b, 0) / cell.length : NaN)));
        // nearest-fill NaNs (two passes are plenty for chat-scale data)
        for (let pass = 0; pass < 2; pass++) {
            for (let gy = 0; gy < N; gy++) {
                for (let gx = 0; gx < N; gx++) {
                    if (!Number.isNaN(grid[gy][gx]))
                        continue;
                    let best = NaN;
                    let bd = 1e9;
                    for (let dy = -2; dy <= 2; dy++)
                        for (let dx = -2; dx <= 2; dx++) {
                            const ny = gy + dy;
                            const nx = gx + dx;
                            if (ny < 0 || nx < 0 || ny >= N || nx >= N)
                                continue;
                            if (Number.isNaN(grid[ny][nx]))
                                continue;
                            const d = dx * dx + dy * dy;
                            if (d < bd) {
                                bd = d;
                                best = grid[ny][nx];
                            }
                        }
                    grid[gy][gx] = best;
                }
            }
        }
        return { grid, N, z0: Math.min(...zs), z1: Math.max(...zs), x0, x1, y0, y1 };
    }, [points]);
    if (!grid)
        return (0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, seriesNames: [], legend: false, children: () => null });
    const px = (gx) => (gx / (grid.N - 1)) * (W - 30) + 26;
    const py = (gy) => H - 26 - (gy / (grid.N - 1)) * (H - 44);
    const segments = [];
    const zlo = grid.z0;
    const zspan = grid.z1 - zlo || 1;
    for (let li = 1; li <= levels; li++) {
        const iso = zlo + (li / (levels + 1)) * zspan;
        for (let gy = 0; gy < grid.N - 1; gy++) {
            for (let gx = 0; gx < grid.N - 1; gx++) {
                const v = [grid.grid[gy][gx], grid.grid[gy][gx + 1], grid.grid[gy + 1][gx + 1], grid.grid[gy + 1][gx]];
                if (v.some((x) => Number.isNaN(x)))
                    continue;
                const corners = [
                    [px(gx), py(gy)],
                    [px(gx + 1), py(gy)],
                    [px(gx + 1), py(gy + 1)],
                    [px(gx), py(gy + 1)],
                ];
                const idx = v.reduce((acc, val, i) => acc + (val > iso ? 1 << i : 0), 0);
                const lerp = (a, b, va, vb) => {
                    const t = (iso - va) / (vb - va || 1e-9);
                    return `${a[0] + (b[0] - a[0]) * t},${a[1] + (b[1] - a[1]) * t}`;
                };
                const E = [
                    [corners[0], corners[1], v[0], v[1]],
                    [corners[1], corners[2], v[1], v[2]],
                    [corners[3], corners[2], v[3], v[2]],
                    [corners[0], corners[3], v[0], v[3]],
                ];
                const table = {
                    1: [[3, 2]], 2: [[1, 2]], 3: [[1, 3]], 4: [[0, 1]], 5: [[0, 3], [1, 2]],
                    6: [[0, 2]], 7: [[0, 3]], 8: [[0, 3]], 9: [[0, 2]], 10: [[0, 1], [2, 3]],
                    11: [[0, 1]], 12: [[1, 3]], 13: [[1, 2]], 14: [[2, 3]],
                };
                for (const [a, b] of table[idx] ?? []) {
                    segments.push(`M ${lerp(E[a][0], E[a][1], E[a][2], E[a][3])} L ${lerp(E[b][0], E[b][1], E[b][2], E[b][3])}`);
                }
            }
        }
    }
    return ((0, jsx_runtime_1.jsx)(ChartFrame_1.ChartFrame, { title: payload.title, unit: payload.unit, legend: false, height: height, children: () => ((0, jsx_runtime_1.jsxs)("svg", { width: "100%", height: height, viewBox: `0 0 ${W} ${H}`, preserveAspectRatio: "xMidYMid meet", role: "img", "aria-label": "contour plot", children: [Array.from({ length: grid.N }, (_, gy) => Array.from({ length: grid.N }, (_, gx) => {
                    const t = (grid.grid[gy][gx] - zlo) / zspan;
                    return ((0, jsx_runtime_1.jsx)("rect", { x: px(gx) - (W - 30) / (grid.N - 1) / 2, y: py(gy) - (H - 44) / (grid.N - 1) / 2, width: (W - 30) / (grid.N - 1) + 1, height: (H - 44) / (grid.N - 1) + 1, fill: (0, tokens_1.alpha)(accent, 0.05 + t * 0.30), stroke: "none" }, `${gx}-${gy}`));
                })), segments.map((d, i) => ((0, jsx_runtime_1.jsx)("path", { d: d, fill: "none", stroke: accent, strokeWidth: 1.1, strokeOpacity: 0.9 }, i))), (0, jsx_runtime_1.jsx)("text", { x: 26, y: H - 8, fontSize: tokens_1.TYPE.micro, fill: theme.text3, children: grid.x0.toFixed(1) }), (0, jsx_runtime_1.jsx)("text", { x: W - 6, y: H - 8, textAnchor: "end", fontSize: tokens_1.TYPE.micro, fill: theme.text3, children: grid.x1.toFixed(1) })] })) }));
}
