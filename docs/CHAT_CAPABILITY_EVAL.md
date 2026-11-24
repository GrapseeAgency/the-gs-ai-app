# Chat capability evaluation

Run against a deployed test backend with its actual worker and trace database:
```sh
bun scripts/run-eval-suite.ts --suite tests/eval-suite-chat-capability-v1.json --durable --strict --baseline results/chat-capability-v1.json
```

The durable transport starts the real worker run and retrieves its exact canonical assistant message. A cut stream, failed setup turn, missing trace or uncompleted run cannot be counted as a pass. The original legacy transport remains available for comparison. Existing suites remain unchanged.

This first suite covers arithmetic, multi-turn corrections, multilingual instruction following, output formatting and execution honesty. It is a small deterministic regression suite, not proof of general intelligence. The email negative assertion is a limited regression, not an exhaustive tool-honesty grader. Calculator tool events are recorded on the durable run; inspect them for actual dispatch, not just answer correctness. Model availability and historical tier scores are not evidence of current concrete model quality.

Report pass/fail/error/skip separately, plus TTFT and total latency. Do not promote a model based on successful HTTP calls or a single suite. Real device responsiveness requires separate Android/iOS measurements.
