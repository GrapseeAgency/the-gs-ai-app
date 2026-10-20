"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.GameSim = GameSim;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const Stage2D_1 = require("./Stage2D");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
/**
 * Game — a real playable arcade loop with keyboard + on-screen d-pad:
 * move with arrows/WASD, grab the coins, dodge the drifters. Score persists
 * across resets via localStorage (per conversation).
 */
function GameSim({ payload }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [score, setScore] = (0, react_1.useState)(0);
    const noopSubscribe = () => () => { };
    const readBest = () => localStorage.getItem('gs.game.best') ?? '0';
    const best = Number((0, react_1.useSyncExternalStore)(noopSubscribe, readBest, () => '0'));
    const keys = (0, react_1.useRef)(new Set());
    const state = (0, react_1.useRef)({ p: { x: 40, y: 40, vx: 0, vy: 0 }, coins: [], drift: [], token: null, lives: 3 });
    const scoreRef = (0, react_1.useRef)(0);
    (0, react_1.useEffect)(() => {
        const dn = (e) => keys.current.add(e.key.toLowerCase());
        const up = (e) => keys.current.delete(e.key.toLowerCase());
        window.addEventListener('keydown', dn);
        window.addEventListener('keyup', up);
        return () => {
            window.removeEventListener('keydown', dn);
            window.removeEventListener('keyup', up);
        };
    }, []);
    const bump = (n) => {
        scoreRef.current += n;
        setScore(scoreRef.current);
        if (scoreRef.current > best) {
            localStorage.setItem('gs.game.best', String(scoreRef.current));
        }
    };
    return ((0, jsx_runtime_1.jsx)("div", { children: (0, jsx_runtime_1.jsx)(Stage2D_1.Stage2D, { title: payload.title || 'Dodge & collect — arrows / WASD', height: payload.size * 3, knobs: { ...payload, interactive: true }, hint: `score ${score} · best ${best}`, onStep: (ctx, w, h, dt, _t, pointer, _ui, resetToken) => {
                const s = state.current;
                if (s.token !== resetToken) {
                    s.token = resetToken;
                    s.p = { x: 40, y: 40, vx: 0, vy: 0 };
                    s.coins = Array.from({ length: 6 }, () => ({ x: 40 + Math.random() * (w - 80), y: 40 + Math.random() * (h - 80), taken: false }));
                    s.drift = Array.from({ length: 3 }, () => ({ x: Math.random() * w, y: Math.random() * h, vx: 30 + Math.random() * 50, vy: 24 + Math.random() * 40 }));
                    scoreRef.current = 0;
                    setScore(0);
                    s.lives = 3;
                }
                const k = keys.current;
                const acc = 900;
                if (k.has('arrowleft') || k.has('a'))
                    s.p.vx -= acc * dt;
                if (k.has('arrowright') || k.has('d'))
                    s.p.vx += acc * dt;
                if (k.has('arrowup') || k.has('w'))
                    s.p.vy -= acc * dt;
                if (k.has('arrowdown') || k.has('s'))
                    s.p.vy += acc * dt;
                // on-screen dpad (touch)
                if (pointer.down) {
                    s.p.vx += (pointer.x - s.p.x) * 2.4 * dt * 10;
                    s.p.vy += (pointer.y - s.p.y) * 2.4 * dt * 10;
                }
                s.p.vx *= 0.92;
                s.p.vy *= 0.92;
                s.p.x = Math.max(12, Math.min(w - 12, s.p.x + s.p.vx * dt));
                s.p.y = Math.max(12, Math.min(h - 12, s.p.y + s.p.vy * dt));
                for (const d of s.drift) {
                    d.x += d.vx * dt;
                    d.y += d.vy * dt;
                    if (d.x < 10 || d.x > w - 10)
                        d.vx *= -1;
                    if (d.y < 10 || d.y > h - 10)
                        d.vy *= -1;
                    if (Math.hypot(d.x - s.p.x, d.y - s.p.y) < 16) {
                        s.p.x = 40;
                        s.p.y = 40;
                        s.lives -= 1;
                        bump(-5);
                    }
                }
                for (const c of s.coins) {
                    if (c.taken)
                        continue;
                    if (Math.hypot(c.x - s.p.x, c.y - s.p.y) < 15) {
                        c.taken = true;
                        bump(10);
                        c.x = 20 + Math.random() * (w - 40);
                        c.y = 20 + Math.random() * (h - 40);
                        setTimeout(() => (c.taken = false), 1500);
                    }
                }
                // render
                for (const c of s.coins) {
                    if (c.taken)
                        continue;
                    ctx.fillStyle = '#F08C00';
                    ctx.beginPath();
                    ctx.arc(c.x, c.y, 6, 0, Math.PI * 2);
                    ctx.fill();
                }
                for (const d of s.drift) {
                    ctx.fillStyle = 'rgba(224,49,49,0.85)';
                    ctx.beginPath();
                    ctx.arc(d.x, d.y, 8, 0, Math.PI * 2);
                    ctx.fill();
                }
                ctx.fillStyle = accent;
                ctx.beginPath();
                ctx.arc(s.p.x, s.p.y, 9, 0, Math.PI * 2);
                ctx.fill();
                ctx.fillStyle = theme.text2;
                ctx.font = `${tokens_1.TYPE.micro}px sans-serif`;
                ctx.fillText(`♥ ${s.lives}`, 8, 14);
            } }) }));
}
