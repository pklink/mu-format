---
name: create-task
description: Use when the user asks to create a task, subtask, milestone or section in Quire — triggers include "Quire task", "create a task", "add a task", "Quire-Task", "Task anlegen", "Task erstellen", "leg einen Task an". Parses free-form German or English descriptions into Quire API parameters (priority, due/start date, assignees, tags, subtasks, recurrence, estimate) and creates them in the mu project. Do NOT use for reading, searching, updating or completing existing Quire tasks, and do NOT use for non-Quire task tracking.
---

# Create a Quire task from natural language

Turns a free-form sentence into a Quire task and creates it. Input may be German or
English; this document is English but the trigger phrases below cover both.

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
3. **Ask** — only when the name is missing or a reference cannot be resolved.
4. **Create** — a single `quire_create_task` call carrying every parameter at once.
5. **Subtasks** — afterwards, one `quire_create_subtask` per item using the parent's `oid`.
6. **Confirm** — report name and URL.

No dry run and no pre-confirmation: the user already asked for the task to be created.
Above 5 subtasks, show the list for approval first.

## Parsing reference

| Phrasing (DE / EN) | Field | Value |
|---|---|---|
| the core statement of the sentence | `name` | **Required.** Strip the imperative: "Erstelle einen Task für X" / "Create a task for X" → `X` |
| "Beschreibung/Details:", "description:", trailing sentences | `description` | markdown allowed |
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

**Input:** "Leg einen Task an: SPEC.md Abschnitt 5 überarbeiten, dringend, fällig Freitag, für mich"

```
quire_create_task(
  projectId: "mu2261",
  name: "SPEC.md Abschnitt 5 überarbeiten",
  priority: "urgent",
  due: "<next Friday as YYYY-MM-DD>",
  assignees: ["pierre111"]
)
```

---

**Input:** "Quire task for the import refactoring with subtasks: lock handling, error paths, tests. Estimate 2h"

```
quire_create_task(projectId:"mu2261", name:"Import refactoring", estimate:7200)
→ oid from the result
quire_create_subtask(parentTaskId:<oid>, name:"Lock handling")
quire_create_subtask(parentTaskId:<oid>, name:"Error paths")
quire_create_subtask(parentTaskId:<oid>, name:"Tests")
```

---

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

**Input:** "Jeden Montag: Abhängigkeiten prüfen, niedrige Prio"

```
quire_create_task(
  projectId: "mu2261",
  name: "Abhängigkeiten prüfen",
  priority: "low",
  recurrence: { freq: "weekly", interval: 1, byweekday: [0] }
)
```
