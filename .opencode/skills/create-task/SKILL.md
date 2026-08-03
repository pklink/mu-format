---
name: create-task
description: Use when the user asks to create a task, subtask, milestone or section in Quire — triggers include "Quire task", "create a task", "add a task", "Quire-Task", "Task anlegen", "Task erstellen", "leg einen Task an". Parses free-form German or English descriptions into Quire API parameters (priority, due/start date, assignees, tags, subtasks, recurrence, estimate) and creates them in the mu project, always written in English with the description as a user story. Do NOT use for reading, searching, updating or completing existing Quire tasks, and do NOT use for non-Quire task tracking.
---

# Create a Quire task from natural language

Turns a free-form sentence into a Quire task and creates it. Input may be German or
English; the trigger phrases below cover both. What lands in Quire is **always English**
and its description is **always a user story** — see [Writing the task](#writing-the-task).

## Target project

Always **`mu2261`** (project "mu", <https://quire.io/w/mu2261>) unless the user names a
different project explicitly — then resolve it with `quire_search_projects` and use that
slug.

Project context (snapshot taken when this skill was written — the API wins on conflict):

- Only member: `pierre111` (timezone Europe/Berlin)
- Statuses: `To-do` = 0, `In progress` = 10, `Completed` = 100
- No tags defined yet
- **Free plan**: search `limit` caps at 30, `get_task_tree` with `depth > 1` returns 402

## Procedure

1. **Parse** — decompose the input using the reference table below.
2. **Resolve** — turn names into IDs (assignees, tags). Never guess, never invent.
3. **Write** — phrase `name` and `description` in English as a user story.
4. **Ask** — only when the name is missing, a reference cannot be resolved, or the user
   story has no honest benefit.
5. **Create** — a single `quire_create_task` call carrying every parameter at once.
6. **Subtasks** — afterwards, one `quire_create_subtask` per item using the parent's `oid`.
7. **Confirm** — report name and URL.

No dry run and no pre-confirmation: the user already asked for the task to be created.
Above 5 subtasks, show the list for approval first.

## Writing the task

### Always English

`name`, `description` and every subtask name go into Quire **in English**, no matter which
language the request was in. A German request is translated, not transcribed: "Leg einen
Task an: SPEC.md Abschnitt 5 überarbeiten" becomes `Rework SPEC.md section 5`.

Translate the request, do not enlarge it. Keep identifiers verbatim — file paths, class and
method names, CLI flags, package names, quoted spec wording, error strings. `./gradlew test`
and `MetadataScanner` stay as they are.

Reply to the user in the language they used; only the Quire content is English.

### Description as a user story

`description` always opens with a user story:

```markdown
**As a** <role>,
**I want** <goal>,
**so that** <benefit>.
```

- **role** — who benefits. Default to `developer on mu-format`; mu is a single-member,
  single-tool project, so that fits almost everything. Use a different role only when the
  request names one (e.g. `user of the mu CLI` for user-visible behaviour).
- **goal** — the outcome, not the implementation. "the architecture tests read our Java 25
  class files", not "bump archunit to 1.4.1".
- **benefit** — why it matters. Derive it from what the user said or from evident context,
  and keep it factual. Never invent business value, revenue or user demand that was not
  stated. If no honest benefit can be derived, ask one short question instead of padding.

Then add only the sections the input actually supports:

- `## Context` — background, root cause, findings, file references as `path:line`.
- `## Scope` — a numbered list of the work, when it decomposes into several steps.
- `## Acceptance criteria` — a checklist of observable outcomes. Prefer verifiable ones;
  for this repo `./gradlew test` passes is the usual anchor (see AGENTS.md — it is the only
  verification step that exists).

Omit any section you would have to make up. A one-line request yields a user story and
nothing else — that is a complete task, not a deficient one.

Keep the story to three lines. Detail belongs under `## Context`, never inside the
`As a … I want … so that …` sentence.

When the breakdown already lives in subtasks, drop `## Scope` — do not list the same steps
twice.

### Exception: sections and milestones

`section: true` is an organizational header and `milestone: true` is a date marker. Neither
delivers anything, so neither gets a user story. Give them a one-line description of what
they group or mark, or no description at all. The English rule still applies.

## Parsing reference

| Phrasing (DE / EN) | Field | Value |
|---|---|---|
| the core statement of the sentence | `name` | **Required.** Strip the imperative, then translate: "Erstelle einen Task für X" / "Create a task for X" → `X`, in English |
| "Beschreibung/Details:", "description:", trailing sentences | `description` | **Always set.** English user story, see [Writing the task](#writing-the-task) |
| "dringend", "urgent", "ASAP", "critical" | `priority` | `urgent` |
| "hohe Prio", "wichtig", "high priority", "important" | `priority` | `high` |
| "niedrige Prio", "low priority", "irgendwann", "someday" | `priority` | `low` |
| "fällig", "bis", "deadline", "due" | `due` | `YYYY-MM-DD` |
| "ab", "Start", "beginnt", "starting", "from" | `start` | `YYYY-MM-DD` |
| "mir", "für mich", "me", "assign to me", "@pierre111" | `assignees` | `["pierre111"]` |
| "Tag X", "tagged X", "#X" | `tags` | tag **IDs**, see below |
| "als Meilenstein", "as a milestone" | `milestone` | `true` |
| "als Section", "as a section" | `section` | `true` |
| "Aufwand", "Schätzung", "estimate", "takes N" | `estimate` | seconds (integer) |
| "mit Subtasks: A, B, C", "with subtasks: …" | — | separate `quire_create_subtask` calls |
| "wiederholt sich", "jeden", "alle N", "every", "repeats" | `recurrence` | see below |

Omit any field the user did not mention — **do not invent defaults**. In particular, with
no statement about priority, do not set `priority` at all (Quire then applies `medium`).
`description` is the one exception: it is always written, because the user story is the
task's format, not extra content.

### Date resolution

Resolve relative expressions against **today's date from the environment context**,
timezone Europe/Berlin. Emit `YYYY-MM-DD`.

- "heute"/"today", "morgen"/"tomorrow", "übermorgen" → +0 / +1 / +2 days
- "nächste Woche"/"next week" → +7 days; "nächsten Monat"/"next month" → +1 month
- "Freitag"/"Friday" → the next upcoming Friday (today does not count)
- "Ende der Woche"/"end of week" → coming Sunday; "Monatsende"/"end of month" → last day
- explicit `DD.MM.` / `DD.MM.YYYY` / `YYYY-MM-DD` → normalize to ISO; without a year pick
  the next future occurrence

If a date expression is ambiguous, **ask** rather than guessing.

### Estimate

Convert to seconds: `1m` = 60, `1h` = 3600, `1d` = 28800 (8-hour day).
Examples: "2h" → `7200`, "30min" → `1800`, "1.5h" → `5400`, "3 days" → `86400`.
The 8-hour day is an assumption — when the user speaks in days, state the conversion in
the confirmation.

### Assignees

`assignees` takes user IDs. Resolve via `quire_list_project_members`.
"mir"/"me"/"myself" → `quire_get_current_user`. For an unknown name, list the existing
members and ask — **never** guess or fabricate a user.

### Tags

`quire_create_task` expects **IDs in `tags`, not names**. Steps:

1. Call `quire_list_tags` for the project
2. Match the given name case-insensitively → use its `oid`
3. No match → ask whether to create it via `quire_create_tag`. Do not create it
   unprompted, and do not silently drop it.

### Recurrence

An object with `freq` (`daily`/`weekly`/`monthly`/`yearly`) and `interval` (≥1).
**Weekdays are integers with Mon=0, Tue=1, Wed=2, Thu=3, Fri=4, Sat=5, Sun=6** — this is
not the JavaScript convention.

- "daily" → `{freq:"daily", interval:1}`
- "every 3 days after completion" → `{freq:"daily", interval:3, sincelatest:true}`
- "every Monday and Thursday" → `{freq:"weekly", interval:1, byweekday:[0,3]}`
- "every 2 weeks" → `{freq:"weekly", interval:2}`
- "on the 15th of each month" → `{freq:"monthly", interval:1, bydayno:15}`
- "last Friday of the month" → `{freq:"monthly", interval:1, byweekno:"last", byweekday:4}`
- "quarterly" → `{freq:"monthly", interval:3}`
- "yearly on May 12" → `{freq:"yearly", interval:1, bymonth:5, bydayno:12}`

End date only via `until` (ISO). Quire has **no** count-based end — for "repeat 5 times",
compute the equivalent `until` date yourself and mention the conversion in the result.

## Error handling

- **No task name derivable** → ask; do not invent one.
- **No benefit derivable for the user story** → ask one short question. Do not fill the
  `so that` clause with a restatement of the goal ("so that X is done") or with invented
  value — both are worse than asking.
- **Assignee or tag unresolvable** → show the available options, then ask.
- **API error** → translate to plain language (e.g. 402 = free-plan limit); never dump
  raw JSON at the user.
- **Unsupported options or fields** → say so explicitly instead of ignoring them silently.

## Output

On success report the task name, the fields that were set, and the URL.
Example: `Created: "Test blob store sharding" (high, due 2026-08-07) — https://quire.io/w/mu2261/42`

**Never** surface opaque Quire IDs (the 24-character `oid` handles) to the user. Use the
name, the short `#id` from the URL, and the URL itself.

## Examples

German in, English out. The name is translated; `SPEC.md` stays verbatim.

**Input:** "Leg einen Task an: SPEC.md Abschnitt 5 überarbeiten, dringend, fällig Freitag, für mich"

```
quire_create_task(
  projectId: "mu2261",
  name: "Rework SPEC.md section 5",
  description: """
**As a** developer on mu-format,
**I want** section 5 of SPEC.md reworked,
**so that** the normative format description stays the thing the code is checked against.
""",
  priority: "urgent",
  due: "<next Friday as YYYY-MM-DD>",
  assignees: ["pierre111"]
)
```

The benefit is not invented: AGENTS.md states SPEC.md is normative and wins over the code.
Derive from context like this; if there is no such anchor, ask.

---

Subtasks carry the breakdown, so the description has no `## Scope`.

**Input:** "Quire task for the import refactoring with subtasks: lock handling, error paths, tests. Estimate 2h"

```
quire_create_task(
  projectId: "mu2261",
  name: "Refactor the import workflow",
  description: """
**As a** developer on mu-format,
**I want** the import workflow restructured,
**so that** lock handling and error paths are covered by tests instead of held in one method.
""",
  estimate: 7200
)
→ oid from the result
quire_create_subtask(parentTaskId:<oid>, name:"Lock handling")
quire_create_subtask(parentTaskId:<oid>, name:"Error paths")
quire_create_subtask(parentTaskId:<oid>, name:"Tests")
```

---

A milestone marks a date. No user story.

**Input:** "Milestone 'Release 1.0' by end of month"

```
quire_create_task(
  projectId: "mu2261",
  name: "Release 1.0",
  milestone: true,
  due: "<last day of the current month>"
)
```

---

A one-line request still gets a user story, and nothing beyond it.

**Input:** "Jeden Montag: Abhängigkeiten prüfen, niedrige Prio"

```
quire_create_task(
  projectId: "mu2261",
  name: "Check dependencies",
  description: """
**As a** developer on mu-format,
**I want** the project's dependencies reviewed every Monday,
**so that** outdated or vulnerable versions surface early instead of at the next upgrade.
""",
  priority: "low",
  recurrence: { freq: "weekly", interval: 1, byweekday: [0] }
)
```
