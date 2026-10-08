# RUNBOOK — filling the competitor columns of the app comparison suite

This suite compares AI APPS (not models). Only the GS-AI column is machine-run
(`bun scripts/run-app-comparison.ts`, results in `results.md`). The ChatGPT,
Claude, DeepSeek, and Dola columns require a human operator because their
harnesses (memory, browsing, prompts, routing) are closed and they publish no
benchmark numbers. Nothing in this runbook may be automated away or invented:
if a step cannot be executed for an app, leave that app's cell "not run" and
write why.

The 20 prompts live in `tests/app-comparison/tasks.json` with their pre-written
rubrics. The two document tasks need the files in `tests/app-comparison/fixtures/`
(`invoice.txt`, `sales.csv`). The memory and ambiguity tasks are multi-turn:
send the turns in the listed order in the SAME conversation.

## Rules for every app

1. Fresh conversation per task (except multi-turn tasks, which are one
   conversation with the listed turns in order).
2. Paste the prompt EXACTLY as written in tasks.json. No rephrasing.
3. Copy the app's reply verbatim into your results column before grading.
4. Grade against the rubric in tasks.json: pass / partial / fail, with a
   one-sentence reason. The rubric is pre-committed; do not adjust it to fit
   an answer.
5. Web-search tasks (t06, t18): note whether the app visibly searched and
   whether it cited sources; the rubric requires it.
6. Document tasks (t05, t17): attach the fixture file the app's own way
   (paperclip/upload button). If an app cannot accept attachments, record
   the task as fail with reason "no attachment support" rather than pasting
   the file contents into the chat.
7. If an app refuses or errors, record the raw behavior. Do not retry until
   it passes.

## ChatGPT app (ChatGPT, OpenAI)

Open the ChatGPT app (mobile or chatgpt.com). Use the default model selection
the app gives a new user; do not force a specific model or paid mode.
Work through tasks t01 to t20 in order. For t05 and t17 use the attach
(paperclip) button with the fixture files. For t07 and t19 keep each task's
turns in one conversation. For t06 and t18 the reply must show web results or
citations to count as sourced. Paste each verbatim reply into the results
column, then grade against the rubric with a one-sentence reason.

## Claude app (Claude, Anthropic)

Open the Claude app (mobile or claude.ai). Use the default model the app
gives a new conversation. Work through t01 to t20 in order. Same attachment
rule for t05/t17 (attach the fixture file), same single-conversation rule for
t07/t19, same sourcing rule for t06/t18. Claude's replies can be long: paste
them verbatim anyway; grade on the rubric, not on style.

## DeepSeek app (DeepSeek)

Open the DeepSeek app (mobile or chat.deepseek.com). Use the default mode; do
not force DeepSeek Reasoning for every task if the default does not use it
(record which mode the app actually used next to your first row). Work
through t01 to t20 in order with the same attachment, multi-turn, and
sourcing rules. Note: if web search is unavailable in your region or version
of the app, record t06 and t18 as fail with reason "no search available"
rather than skipping them.

## Dola app (Dola, ByteDance Larus)

Dola is a calendar-and-life assistant, not a general chat app. It may refuse
or be unable to run several task categories (code generation, SQL, letter
constraints). Run every task anyway and record what it actually does:
refusals and out-of-scope answers are honest results, graded fail or partial
by the rubric. For t05/t17, if Dola cannot accept a file upload, record fail
with reason "no attachment support". Do not skip tasks or substitute another
ByteDance app.

## After running

Fill the four competitor columns in `tests/app-comparison/results.md` with
pass/partial/fail plus the one-sentence reason per cell, and add the verbatim
replies in a new "verbatim transcripts" section below the scorecard (or a
sibling file `results-competitors.md`). Commit with the operator's name and
the date. The rubrics in tasks.json are the only grading standard; if a cell
cannot be graded, write "not run" and the reason. Never estimate a competitor
score: an empty cell is honest, an invented number is not.
