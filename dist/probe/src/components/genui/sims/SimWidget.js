"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.SimWidget = SimWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const ClothSim_1 = require("./ClothSim");
const SoftBodySim_1 = require("./SoftBodySim");
const FluidSim_1 = require("./FluidSim");
const SmokeSim_1 = require("./SmokeSim");
const ParticleSim_1 = require("./ParticleSim");
const RigidSim_1 = require("./RigidSim");
const TerrainSim_1 = require("./TerrainSim");
const GameSim_1 = require("./GameSim");
function SimWidget({ payload }) {
    const full = {
        ...payload,
        gravity: payload.gravity ?? 9.8,
        speed: payload.speed ?? 1,
        damping: payload.damping ?? 0.99,
        count: payload.count ?? 60,
        size: payload.size ?? 70,
        seed: payload.seed ?? 42,
        interactive: payload.interactive !== false,
    };
    switch (payload.sim) {
        case 'cloth': return (0, jsx_runtime_1.jsx)(ClothSim_1.ClothSim, { payload: full });
        case 'softbody': return (0, jsx_runtime_1.jsx)(SoftBodySim_1.SoftBodySim, { payload: full });
        case 'fluid': return (0, jsx_runtime_1.jsx)(FluidSim_1.FluidSim, { payload: full });
        case 'smoke': return (0, jsx_runtime_1.jsx)(SmokeSim_1.SmokeSim, { payload: full });
        case 'rigid': return (0, jsx_runtime_1.jsx)(RigidSim_1.RigidSim, { payload: full });
        case 'terrain': return (0, jsx_runtime_1.jsx)(TerrainSim_1.TerrainSim, { payload: full });
        case 'game': return (0, jsx_runtime_1.jsx)(GameSim_1.GameSim, { payload: full });
        case 'particles':
        default: return (0, jsx_runtime_1.jsx)(ParticleSim_1.ParticleSim, { payload: full });
    }
}
