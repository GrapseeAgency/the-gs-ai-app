"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.dynamic = void 0;
exports.GET = GET;
exports.POST = POST;
const server_1 = require("next/server");
const db_1 = require("@/lib/db");
const serializers_1 = require("@/lib/serializers");
const rate_limit_1 = require("@/lib/rate-limit");
exports.dynamic = 'force-dynamic';
const MAX_NAME = 80;
const MAX_TEXT = 8000;
const ALLOWED_CATEGORIES = [
    'general',
    'research',
    'writing',
    'coding',
    'productivity',
    'learning',
];
function slugify(input) {
    return input
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
        .slice(0, 60);
}
// GET /api/v1/assistants?category=writing&favourite=true — blueprint catalogue
async function GET(req) {
    const params = new URL(req.url).searchParams;
    const category = params.get('category');
    const favourite = params.get('favourite');
    const assistants = await db_1.db.assistant.findMany({
        where: {
            ...(category ? { category } : {}),
            ...(favourite === 'true' ? { favourite: true } : {}),
            ...(favourite === 'false' ? { favourite: false } : {}),
        },
        orderBy: [{ favourite: 'desc' }, { uses: 'desc' }],
    });
    return server_1.NextResponse.json({ items: assistants.map(serializers_1.assistantToJson) });
}
// POST /api/v1/assistants — { name, description?, instructions?, category?, starters? }
async function POST(req) {
    // Guardrail: 10 creations / minute / client.
    const limit = (0, rate_limit_1.rateLimit)((0, rate_limit_1.clientKey)(req, 'assistants:create'), 10, 60000);
    if (!limit.allowed) {
        return server_1.NextResponse.json({ code: 'rate_limited', message: 'Too many assistant creations — slow down.' }, { status: 429, headers: { 'Retry-After': String(limit.retryAfterSec) } });
    }
    let body;
    try {
        body = (await req.json());
    }
    catch {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'Request body must be valid JSON' }, { status: 400 });
    }
    const name = typeof body.name === 'string' ? body.name.trim() : '';
    if (!name) {
        return server_1.NextResponse.json({ code: 'bad_request', message: 'name must be a non-empty string' }, { status: 400 });
    }
    if (name.length > MAX_NAME) {
        return server_1.NextResponse.json({ code: 'bad_request', message: `name must not exceed ${MAX_NAME} characters` }, { status: 400 });
    }
    const description = typeof body.description === 'string' ? body.description.slice(0, MAX_TEXT) : '';
    const instructions = typeof body.instructions === 'string' ? body.instructions.slice(0, MAX_TEXT) : '';
    const category = ALLOWED_CATEGORIES.includes(body.category)
        ? body.category
        : 'general';
    const starters = Array.isArray(body.starters)
        ? body.starters.filter((s) => typeof s === 'string').slice(0, 6)
        : [];
    const base = slugify(name) || 'assistant';
    let slug = base;
    for (let i = 2;; i++) {
        const clash = await db_1.db.assistant.findUnique({ where: { slug } });
        if (!clash)
            break;
        slug = `${base}-${i}`;
    }
    const assistant = await db_1.db.assistant.create({
        data: {
            slug,
            name,
            description,
            instructions,
            category,
            starters: JSON.stringify(starters),
            published: body.published === true,
        },
    });
    return server_1.NextResponse.json((0, serializers_1.assistantToJson)(assistant), { status: 201 });
}
