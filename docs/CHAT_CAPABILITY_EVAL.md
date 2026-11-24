# Chat capability evaluation

Run against a deployed test backend with its actual worker and trace database:
```sh
bun scripts/run-eval-suite.ts --suite tests/eval-suite-chat-capability-v1.json --durable --strict --baseline results/chat-capability-v1.json
```

The durable transport starts the real worker run and retrieves its exact canonical assistant message. A cut stream, failed setup turn, missing trace or uncompleted run cannot be counted as a pass. The original legacy transport remains available for comparison. Existing suites remain unchanged.

This first suite covers arithmetic, multi-turn corrections, multilingual instruction following, output formatting and execution honesty. It is a small deterministic regression suite, not proof of general intelligence. The email negative assertion is a limited regression, not an exhaustive tool-honesty grader. Calculator tool events are recorded on the durable run; inspect them for actual dispatch, not just answer correctness. Model availability and historical tier scores are not evidence of current concrete model quality.

Report pass/fail/error/skip separately, plus TTFT and total latency. Do not promote a model based on successful HTTP calls or a single suite. Real device responsiveness requires separate Android/iOS measurements.

## Remote tool protocol and rollout

The OpenRouter adapter can issue native function calls for calculator, text statistics and clock. Enable `GS_REMOTE_TOOLS=1` only after the concrete free provider passes compatibility and quality checks. This is restricted to eligible thinking turns; final synthesis remains genuine provider streaming. The existing search pipeline remains responsible for web retrieval. Arbitrary code execution, file writes, external account actions and unapproved side effects are not enabled.

The Chat capability validation workflow runs focused unit tests and a concrete-model benchmark (two trials per case, strict identity check, no paid fallback). A missing repository `OPENROUTER_API_KEY` secret produces an explicit blocked result, not a passing benchmark. The optional isolated-backend dispatch uses a temporary database and does not deploy production. Production deployment needs the authorized hosting environment and deployment credentials; there is no deployment workflow configured in this repository.

## Durable queue deployment prerequisite

The queue now persists the provider retry deadline in the additive nullable `TurnRun.retryNotBefore` column and indexes due work. Before deploying this revision to an existing SQLite database, back up the database and apply the reviewed Prisma schema with `npx prisma db push` (without accepting destructive changes). The source change and fresh-database regression do not migrate a production database. Existing rows with a null deadline retain their legacy local backoff. No paid fallback or rate-limit bypass is introduced.
