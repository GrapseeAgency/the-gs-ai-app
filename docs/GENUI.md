# GENUI — Rendering Interactive Things Inside the Chat

Status: proposal (researched 2026-10-18). Nothing implemented yet.

## What the user asked for

"Render everything in the chat box — interactive things, rendered in the chat."
I.e. the assistant's answer is no longer only text: a chart when the answer is
a trend, a table when it is a comparison, a card when it is a price — directly
inside the conversation, on the web client AND the Android app.

## How the leaders do it (researched, not guessed)

| Product | Mechanism | Lesson for us |
|---|---|---|
| **Claude Artifacts** (2024) | Model emits full code (HTML/React/SVG/Mermaid) → rendered in a **sandboxed iframe** side panel, versioned | Maximum freedom, but needs a sandbox and a second surface. Heavy on mobile. |
| **ChatGPT Canvas** (Oct 2024) | Same side-panel pattern for writing/code | Same. |
| **GPT-6 "Intelligent UI" / Apps in ChatGPT** (announced Oct 2026) | Interactive widgets — clickable charts, forms, small apps — **inline in the message stream** | This is exactly what we want. Direction of the industry, not a niche. |
| **Vercel AI SDK Generative UI** (Mar 2024+) | "UI as a tool call": model emits **structured JSON**, the client renders a **registered React component** for it | The declarative paradigm. Safe, typed, testable. |
| **Perplexity** | Answer = text + structured cards inline | Same declarative idea, narrower widget set. |

Two paradigms, and the trade is explicit:

1. **Declarative blocks** — model describes *what* to show as typed JSON, the
   client renders it natively. Safe, fast, themeable, works offline. Limited to
   the widget types you ship.
2. **Sandboxed artifacts** — model writes *actual code*, rendered in an
   isolated frame. Unlimited, but needs iframe/WebView sandboxing, is heavier,
   and is where prompt-injection risk lives.

## Our constraints (checked in this repo)

- The provider chat endpoint has **no native tool-calling** — the backend owns
  routing (docs/WEBSEARCH.md §"Proven provider contract"). So the model cannot
  literally "call" a chart tool; the mechanism has to be **prompt-driven
  structured output inside the markdown stream**.
- The answer is already **streamed as markdown over SSE** to two clients:
  - Android: a real block parser exists — `ContentBlocks.kt` with
    Paragraph/Heading/List/Quote/CodeBlock/TableBlock/MathBlock/Mermaid.
  - Web: `page.tsx` streams SSE; shadcn components including `chart.tsx`
    (recharts) are **already installed but unused**.
- Nothing renders iframes anywhere today (verified: zero iframe usage in src).

## Recommendation: hybrid, phased. Declarative first, artifacts second.

Phase 1 gets us 80% of the "wow" with 10% of the risk; artifacts are the
escape hatch for "everything else", added once the plumbing exists.

### The wire format (Phase 0 — no SSE change needed)

The model emits a fenced block whose language is the widget type; the body is
JSON. It rides the existing text stream — no new event types, no new protocol.

````
Here's how laptop prices moved:

```chart
{"type":"line","title":"Avg price, 2023–2026","unit":"$",
 "series":[{"name":"budget","data":[[2023,499],[2024,449],[2025,429],[2026,399]]}]}
```
````

Rendering rules (both clients, identical):

- Unclosed fence while streaming → show a **skeleton** ("building chart…"),
  never raw JSON.
- Invalid JSON or failed schema validation → render as a plain code block.
  Degradation is free because the transport is markdown.
- Hard caps: ≤ 200 data points, ≤ 16 series, ≤ 64 KB per widget, ≤ 6 widgets
  per message. Enforced at parse time.

### Widget set v1

| Fence | Payload | Native renderer |
|---|---|---|
| `chart` | line/bar/area/pie, series + labels | Android: Compose Canvas (no new dep). Web: shadcn chart/recharts (already installed). |
| `table` | columns + rows (+ sortable) | both: existing TableBlock path, upgraded to interactive |
| `stat` | value, unit, delta, sparkline | simple Compose / div card |
| `compare` | two columns of labelled rows | split card |
| `progress` | label + percent | bar card |
| `timeline` | dated entries | vertical line card |

### Where the model learns to emit it

A short `WIDGET_PROMPT` appended to the grounding/system prompt, with one
worked example and the hard rule: *a widget supplements the answer, never
replaces the text around it.* Plus a parse-rate metric so we know how often the
model produces valid widgets before adding more types.

### Phase 2 — artifacts (the escape hatch)

For freeform interactive things (a game, a calculator, a map), the model emits
`artifact` with a full HTML document. Web: `<iframe sandbox="allow-scripts">`
(no `allow-same-origin`, no cookies, CSP'd). Android: WebView with JS enabled
but `domStorage` off and no bridge. Same skeleton/degrade rules. This is the
only phase with real security work, which is why it is second, not first.

### Why not "the model writes HTML for everything"

That is the Artifacts-only route. It gives maximum freedom but: doubles the
per-message token cost, renders differently on each client, needs the sandbox
on day one, and every answer becomes a mini website nobody proof-read. The
leaders all ship declarative widgets for the common cases and artifacts for
the long tail. So do we.

## Risks

- **Model compliance**: without tool-calling, JSON-in-fence compliance is the
  whole game. Mitigation: one worked example in the prompt, strict validation,
  code-block fallback, and the parse-rate metric before scaling the widget set.
- **Injection through widget JSON**: widgets render data, not code — the only
  executable surface in Phase 1 is none. Artifacts are where the sandbox
  discipline has to be airtight.
- **Streaming jank**: a widget fence mid-paragraph must not flicker; skeleton
  then swap, measured on the Android 60fps budget.

## Effort (honest)

| Phase | Scope | Estimate |
|---|---|---|
| 0 | widget schema + prompt contract + parser tests | 1 commit, hours |
| 1 | 3 widgets (chart, stat, table) end-to-end, both clients | the bulk of the work |
| 2 | artifacts sandbox + preview panel | comparable to Phase 1 |
| 3 | interactive callbacks (widget → new turn) | depends on Phase 1 |
