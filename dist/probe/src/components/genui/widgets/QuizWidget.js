"use strict";
'use client';
Object.defineProperty(exports, "__esModule", { value: true });
exports.QuizWidget = QuizWidget;
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const tokens_1 = require("../theme/tokens");
const WidgetSurface_1 = require("../theme/WidgetSurface");
const TitleRow_1 = require("../primitives/TitleRow");
const EmptyState_1 = require("../primitives/EmptyState");
/** Quiz — tap-to-answer, instant verdict, score kept across questions. */
function QuizWidget({ payload, onPrompt }) {
    const { accent, theme } = (0, WidgetSurface_1.useWidgetIdentity)();
    const [answers, setAnswers] = (0, react_1.useState)({});
    const questions = (payload.questions ?? []).slice(0, 10);
    if (!questions.length)
        return (0, jsx_runtime_1.jsx)(EmptyState_1.EmptyState, { label: "No questions" });
    const answered = Object.keys(answers).length;
    const score = questions.reduce((acc, q, qi) => acc + (answers[qi] === q.answer ? 1 : 0), 0);
    const done = answered === questions.length;
    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsx)(TitleRow_1.TitleRow, { title: payload.title, right: answered > 0 ? `${score}/${questions.length}` : `${questions.length} questions` }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 16 }, children: questions.map((q, qi) => {
                    const picked = answers[qi];
                    const isAnswered = picked !== undefined;
                    return ((0, jsx_runtime_1.jsxs)("div", { children: [(0, jsx_runtime_1.jsxs)("div", { style: { fontSize: tokens_1.TYPE.body, fontWeight: 600, color: theme.text, marginBottom: 8 }, children: [qi + 1, ". ", q.q] }), (0, jsx_runtime_1.jsx)("div", { style: { display: 'flex', flexDirection: 'column', gap: 6 }, children: q.options.map((opt, oi) => {
                                    const correct = oi === q.answer;
                                    const pickedThis = picked === oi;
                                    let bg = 'transparent';
                                    let border = theme.border;
                                    let fg = theme.text;
                                    if (isAnswered && correct) {
                                        bg = (0, tokens_1.alpha)('#0CA678', theme.dark ? 0.16 : 0.1);
                                        border = '#0CA678';
                                    }
                                    else if (pickedThis && !correct) {
                                        bg = (0, tokens_1.alpha)('#E03131', theme.dark ? 0.14 : 0.08);
                                        border = '#E03131';
                                    }
                                    else if (!isAnswered) {
                                        bg = 'transparent';
                                    }
                                    else {
                                        fg = theme.text3;
                                    }
                                    return ((0, jsx_runtime_1.jsxs)("button", { type: "button", disabled: isAnswered, onClick: () => setAnswers((a) => ({ ...a, [qi]: oi })), style: {
                                            textAlign: 'left',
                                            padding: '9px 12px',
                                            borderRadius: tokens_1.RADIUS.md,
                                            border: `1.5px solid ${border}`,
                                            background: bg,
                                            color: fg,
                                            fontSize: tokens_1.TYPE.label,
                                            fontWeight: pickedThis || (isAnswered && correct) ? 700 : 400,
                                            cursor: isAnswered ? 'default' : 'pointer',
                                            opacity: isAnswered && !correct && !pickedThis ? 0.6 : 1,
                                        }, children: [opt, isAnswered && correct ? ' ✓' : '', pickedThis && !correct ? ' ✗' : ''] }, oi));
                                }) }), isAnswered && q.explain ? ((0, jsx_runtime_1.jsx)("div", { style: { fontSize: tokens_1.TYPE.micro, color: theme.text2, marginTop: 6, lineHeight: 1.5 }, children: q.explain })) : null] }, qi));
                }) }), done && ((0, jsx_runtime_1.jsxs)("div", { style: { marginTop: 14, padding: 12, borderRadius: tokens_1.RADIUS.lg, background: (0, tokens_1.alpha)(accent, 0.12), border: `1px solid ${(0, tokens_1.alpha)(accent, 0.3)}` }, children: [(0, jsx_runtime_1.jsxs)("span", { style: { fontSize: tokens_1.TYPE.body, fontWeight: 700, color: accent }, children: [score, "/", questions.length] }), (0, jsx_runtime_1.jsx)("span", { style: { fontSize: tokens_1.TYPE.label, color: theme.text2, marginLeft: 8 }, children: score === questions.length ? 'Perfect.' : score >= questions.length / 2 ? 'Solid.' : 'Worth another pass.' })] }))] }));
}
