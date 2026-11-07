"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.toRows = toRows;
exports.toPoints = toPoints;
function toRows(series, labels, labelEvery = 1) {
    const n = Math.max(0, ...series.map((s) => s.data.length));
    const rows = [];
    for (let i = 0; i < n; i++) {
        const row = {};
        const label = labels?.[i];
        if (label !== undefined) {
            row.label = i % labelEvery === 0 || i === n - 1 ? label : '';
            row.fullLabel = label;
        }
        else {
            row.label = String(i + 1);
            row.fullLabel = row.label;
        }
        for (const s of series) {
            const pt = s.data[i];
            if (pt)
                row[s.name] = pt[pt.length - 1];
        }
        rows.push(row);
    }
    return rows;
}
function toPoints(series) {
    return series.flatMap((s, si) => s.data.map((d) => ({ x: d[0], y: d[1], z: d[2] ?? 1, series: s.name, si })));
}
