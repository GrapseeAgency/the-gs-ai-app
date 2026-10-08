# App vs App comparison suite — GS-AI results (competitor columns intentionally empty)

Suite: tests/app-comparison/tasks.json (20 tasks, categories mandated by the operator).
GS-AI leg run: 2026-10-08, against the local production app (http://localhost:3000, next-server v16.1.3), via the public API only (POST /api/v1/conversations, POST /api/v1/uploads, POST /api/v1/conversations/{id}/messages with stream=false). Raw transcripts preserved in tests/app-comparison/raw-answers.json (conversation ids included). Grader: the repository agent, grading against the pre-written rubrics in tasks.json; reasons below are one sentence each and every graded claim is checkable against the raw transcript.

Score: PASS 16 / PARTIAL 3 / FAIL 1.

## Scorecard

| # | Task | Category | GS-AI | ChatGPT app | Claude app | DeepSeek app | Dola app |
|---|------|----------|-------|-------------|------------|--------------|----------|
| t01 | Chemical symbol for gold | short_factual | PASS — answered exactly "Au". | not run | not run | not run | not run |
| t02 | Two trains catch-up time | multi_step_reasoning | PASS — answered 2:00 PM, which matches 80 km gap closed at 20 km/h from 10:00 AM. | not run | not run | not run | not run |
| t03 | Python is_palindrome | code_generation | PASS — lowercases, filters non-alphanumerics, compares to reverse; correct on the Panama example and empty string. | not run | not run | not run | not run |
| t04 | Sky blue in exactly five words | instruction_hard_constraint | PARTIAL — correct gist but the answer has six words ("Sunlight scatters; short blue wavelengths dominate."), one condition met. | not run | not run | not run | not run |
| t05 | Invoice total and due date (attached file) | document | PASS — read the attachment and returned both $1,847.50 and March 31, 2026. | not run | not run | not run | not run |
| t06 | Latest stable Next.js major version today | live_web_search | FAIL — live search ran (7 sources returned) but it asserted version 15 as latest in October 2026, while Next.js 16 is the current stable major (the app server itself runs 16.1.3), so the sourced answer was stale. | not run | not run | not run | not run |
| t07 | Recall codename after an intervening turn | memory_of_earlier_turns | PASS — answered 51 on the math turn and recalled VELVET-9 unprompted two turns later. | not run | not run | not run | not run |
| t08 | "How much does it cost?" (no referent) | ambiguous_prompt | PASS — asked what "it" refers to and named the missing context instead of inventing a price. | not run | not run | not run | not run |
| t09 | Japan's currency | short_factual | PASS — "Japanese yen (JPY)". | not run | not run | not run | not run |
| t10 | Boxes, bags, marbles | multi_step_reasoning | PASS — 36 with correct shown work (60 minus 24). | not run | not run | not run | not run |
| t11 | SQL totals over 1000 ordered desc | code_generation | PASS — GROUP BY customer_id, SUM(amount), HAVING > 1000, ORDER BY total_amount DESC. | not run | not run | not run | not run |
| t12 | Exactly three primary colors, nothing else | instruction_hard_constraint | PASS — exactly three numbered single-word lines, zero extra text. | not run | not run | not run | not run |
| t13 | Sunlight to Earth | short_factual | PASS — "about 8 minutes and 20 seconds". | not run | not run | not run | not run |
| t14 | Sequential discount 25% then 10% | multi_step_reasoning | PASS — $27, the sequential-discount result (not the 35% trap). | not run | not run | not run | not run |
| t15 | Fix avg() empty-list crash | code_generation | PASS — `sum(nums) / len(nums) if nums else 0`, guard present, math unchanged. | not run | not run | not run | not run |
| t16 | Sentence of 12+ words with no letter e | instruction_hard_constraint | PARTIAL — 14 words (meets length) but contains 'e' ("over", "sentence", "e entirely"), one condition met. | not run | not run | not run | not run |
| t17 | CSV: top region by total sales | document | PARTIAL — region correct (West) but total stated 7,350 while the West rows sum to 6,150 (1,250 + 2,300 + 2,600); the figure includes one extra row. | not run | not run | not run | not run |
| t18 | Current PlayStation-maker CEO, sourced | live_web_search | PASS — live search ran (8 sources), named Hiroki Totoki as Sony Group CEO effective April 1, 2025, which is sourced and current; note the PlayStation unit (Sony Interactive Entertainment) CEO is Hideaki Nishino, so the parent-vs-unit reading is the only imprecision. | not run | not run | not run | not run |
| t19 | Peanut allergy across turns | memory_of_earlier_turns | PASS — proactively built a peanut-free list in turn 2 and in turn 3 correctly stated none of its own items contain peanuts, keeping the stated allergy connected. | not run | not run | not run | not run |
| t20 | "When is it coming out?" (no referent) | ambiguous_prompt | PASS — asked what "it" refers to; invented no release date. | not run | not run | not run | not run |

## Tally

GS-AI: 16 PASS, 3 PARTIAL (t04, t16, t17), 1 FAIL (t06). 20 of 20 tasks executed; 22 turns captured; both document fixtures uploaded and read successfully.

## Competitor columns — how they get filled

The ChatGPT app, Claude app, DeepSeek app, and Dola app columns stay "not run" until the operator runs the same 20 prompts manually in each app (the document tasks need the two files in tests/app-comparison/fixtures/ attached the same way; the memory and ambiguity tasks need the same multi-turn order) and pastes the answers back; the repository then grades them against the identical rubrics. No competitor cell will ever be filled from marketing claims or invented numbers.

## Known measurement caveats (stated once, honestly)

1. Grading was performed by the repository agent, not a disinterested third party; the raw transcripts are committed alongside so any grader can re-mark them.
2. The web-search tasks grade process plus plausibility, not a pinned fact, because "latest version today" and "current CEO today" change; the rubrics in tasks.json say exactly what counts.
3. One run per task per app; this suite measures breadth of capability, not variance. Re-running is cheap (bun scripts/run-app-comparison.ts) if the operator wants repeat trials.
