---
name: create-task
description: Use when the user asks to create a task, subtask, milestone or section in Linear — triggers include "Linear task", "create a task", "add a task", "Task anlegen", "Task erstellen", "leg einen Task an". Parses free-form German or English descriptions into Linear API parameters (priority, due date, assignee, labels, parent, estimate) and creates them in the mu project, always written in English with the description as a user story. Do NOT use for reading, searching, updating or completing existing Linear issues, and do NOT use for non-Linear task tracking.
---

# Create a Linear issue from natural language

Turns a free-form sentence into a Linear issue and creates it. Input may be German or
English; the trigger phrases below cover both. What lands in Linear is **always English**
and its description is **always a user story** — see [Writing the task](#writing-the-task).

## Target

Always create in team **`einself`** and project **`mu`** unless the user names a
different team or project explicitly — then resolve it and use that.

Context (snapshot from workspace — the API wins on conflict):

- Team: `einself` (key: `111`)
- Project: `mu` (https://linear.app/einself/project/mu-6b11e36819b3)
- User: `Pierre Klink` (display name: `pierre`)
- Statuses: `Todo` (unstarted), `In Progress` (started), `In Review` (started), `Done` (completed), `Canceled` (canceled), `Backlog` (backlog)
- Labels: `Bug`, `Feature`, `Improvement`
- No milestones defined yet

## Procedure

1. **Parse** — decompose the input using the reference table below.
2. **Resolve** — turn names into identifiers (assignee, labels, parent, milestone, project).
   Never guess, never invent.
3. **Write** — phrase `title` and `description` in English as a user story.
4. **Ask** — only when the title is missing, a reference cannot be resolved, or the user
   story has no honest benefit.
5. **Create** — a single `linear_save_issue` call carrying every parameter at once.
6. **Subtasks** — afterwards, one `linear_save_issue` per item with `parentId` set to the
   parent issue's ID.
7. **Milestones** — use `linear_save_milestone` instead of `linear_save_issue`. A milestone
   belongs to a project and has a `name` and optional `targetDate`.
8. **Confirm** — report title, fields that were set, and the URL.

No dry run and no pre-confirmation: the user already asked for the task to be created.
Above 5 subtasks, show the list for approval first.

## Writing the task

### Always English

`title`, `description` and every subtask title go into Linear **in English**, no matter which
language the request was in. A German request is translated, not transcribed: "Leg einen
Task an: SPEC.md Abschnitt 5 überarbeiten" becomes `Rework SPEC.md section 5`.

Translate the request, do not enlarge it. Keep identifiers verbatim — file paths, class and
method names, CLI flags, package names, quoted spec wording, error strings. `./gradlew test`
and `MetadataScanner` stay as they are.

Reply to the user in the language they used; only the Linear content is English.

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

### Exception: milestones

A milestone (`linear_save_milestone`) is a date marker for a project. It does not deliver
work, so it gets no user story. Give it a one-line description of what it marks, or no
description at all. The English rule still applies.

Linear has no "section" concept. If the user asks for one, explain that and offer a label
or a parent issue as an alternative.

## Parsing reference

| Phrasing (DE / EN)                                          | Field         | Value                                                                                                                     |
|-------------------------------------------------------------|---------------|---------------------------------------------------------------------------------------------------------------------------|
| the core statement of the sentence                          | `title`       | **Required.** Strip the imperative, then translate: "Erstelle einen Task für X" / "Create a task for X" → `X`, in English |
| "Beschreibung/Details:", "description:", trailing sentences | `description` | **Always set.** English user story, see [Writing the task](#writing-the-task)                                             |
| "dringend", "urgent", "ASAP", "critical"                    | `priority`    | `1` (Urgent)                                                                                                              |
| "hohe Prio", "wichtig", "high priority", "important"        | `priority`    | `2` (High)                                                                                                                |
| "niedrige Prio", "low priority", "irgendwann", "someday"    | `priority`    | `4` (Low)                                                                                                                 |
| "fällig", "bis", "deadline", "due"                          | `dueDate`     | `YYYY-MM-DD`                                                                                                              |
| "ab", "Start", "beginnt", "starting", "from"                | —             | **Not supported by Linear.** Note it in the description context instead.                                                  |
| "mir", "für mich", "me", "assign to me", "@pierre"          | `assignee`    | `"pierre"` (display name)                                                                                                 |
| *(default)*                                                 | `assignee`    | **Always set to `"pierre"`** unless the user explicitly assigns to someone else.                                          |
| "Label X", "tagged X", "#X", "Tag X"                        | `labels`      | label **names** as array, see below                                                                                       |
| "als Meilenstein", "as a milestone"                         | —             | use `linear_save_milestone` instead of `linear_save_issue`                                                                |
| "Aufwand", "Schätzung", "estimate", "takes N"               | `estimate`    | number (see below)                                                                                                        |
| "mit Subtasks: A, B, C", "with subtasks: …"                 | —             | separate `linear_save_issue` calls with `parentId`                                                                        |
| "wiederholt sich", "jeden", "alle N", "every", "repeats"    | —             | **Not supported by Linear.** Note the recurrence in the description.                                                      |
| "Zustand X", "Status X", "state X"                          | `state`       | state name (e.g. `"In Progress"`, `"Backlog"`)                                                                            |

### Unsupported features

- **Start date**: Linear issues have no start date field. When the user provides one, note it
  in the description context.
- **Recurrence**: Linear has no recurring issues. When the user asks for one, create a single
  issue and note the intended recurrence in the description.
- **Sections**: No equivalent. Offer a label or a parent issue with subtasks instead.
- **Multiple assignees**: Linear supports only a single assignee. If the user names several,
  assign the first and mention the others in the description.

Omit any field the user did not mention — **do not invent defaults**. In particular, with
no statement about priority, do not set `priority` at all (Linear then applies `0` = No priority).
**Exception: `assignee` always defaults to `"pierre"`** unless the user explicitly names
someone else. `description` is always written, because the user story is the
issue's format, not extra content.

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

Linear uses an abstract numeric estimate (not seconds). Convert:
- "1h" → `1`, "2h" → `2`, "30min" → `0.5`, "1.5h" → `1.5`
- "1d" → `1`, "3 days" → `3`
- "1w" → `5`

When converting from days or weeks, state the conversion in the confirmation.

### Assignee

`assignee` takes a user display name or ID. Resolve via `linear_list_users` or
`linear_get_user`. "mir"/"me"/"myself" → `linear_get_user(query: "me")` → use the
`displayName`. For an unknown name, list the existing members and ask — **never** guess or
fabricate a user.

Linear supports only a single assignee per issue.

### Labels

`linear_save_issue` accepts label **names** (not IDs) as a string array. Steps:

1. Call `linear_list_issue_labels` for the team
2. Match the given name case-insensitively
3. No match → ask whether to create it via `linear_create_issue_label`. Do not create it
   unprompted, and do not silently drop it.

### Milestones

A milestone in Linear is a separate entity tied to a project, not an issue flag. Steps:

1. If the user says "milestone", use `linear_save_milestone` with `project: "mu"` (or the
   resolved project).
2. `name` is required. Optional: `targetDate`, `description`.
3. If the user wants to assign an issue to an existing milestone, use `linear_save_issue`
   with `milestone` set to the milestone name.

## Error handling

- **No title derivable** → ask; do not invent one.
- **No benefit derivable for the user story** → ask one short question. Do not fill the
  `so that` clause with a restatement of the goal ("so that X is done") or with invented
  value — both are worse than asking.
- **Assignee or label unresolvable** → show the available options, then ask.
- **API error** → translate to plain language; never dump raw JSON at the user.
- **Unsupported features** → say so explicitly and offer the workaround (see
  [Unsupported features](#unsupported-features)).

## Output

On success report the issue title, the fields that were set, and the URL.

Example: `Created: "Test blob store sharding" (high, due 2026-08-07) — https://linear.app/einself/issue/111-42`

For milestones: `Created milestone: "Release 1.0" (target 2026-08-31)`

**Never** surface opaque Linear UUIDs to the user. Use the title, the short issue key
(e.g. `111-42`), and the URL.

## Examples

German in, English out. The title is translated; `SPEC.md` stays verbatim.

**Input:** "Leg einen Task an: SPEC.md Abschnitt 5 überarbeiten, dringend, fällig Freitag, für mich"

```
linear_save_issue(
  team: "einself",
  project: "mu",
  title: "Rework SPEC.md section 5",
  description: """
**As a** developer on mu-format,
**I want** section 5 of SPEC.md reworked,
**so that** the normative format description stays the thing the code is checked against.
""",
  priority: 1,
  dueDate: "<next Friday as YYYY-MM-DD>",
  assignee: "pierre"
)
```

The benefit is not invented: AGENTS.md states SPEC.md is normative and wins over the code.
Derive from context like this; if there is no such anchor, ask.

---

Subtasks carry the breakdown, so the description has no `## Scope`.

**Input:** "Linear task for the import refactoring with subtasks: lock handling, error paths, tests. Estimate 2h"

```
linear_save_issue(
  team: "einself",
  project: "mu",
  title: "Refactor the import workflow",
  description: """
**As a** developer on mu-format,
**I want** the import workflow restructured,
**so that** lock handling and error paths are covered by tests instead of held in one method.
""",
  estimate: 2,
  assignee: "pierre"
)
→ id from the result
linear_save_issue(team: "einself", title: "Lock handling", parentId: <id>)
linear_save_issue(team: "einself", title: "Error paths", parentId: <id>)
linear_save_issue(team: "einself", title: "Tests", parentId: <id>)
```

---

A milestone marks a date. No user story.

**Input:** "Milestone 'Release 1.0' by end of month"

```
linear_save_milestone(
  project: "mu",
  name: "Release 1.0",
  targetDate: "<last day of the current month>"
)
```

---

A one-line request still gets a user story, and nothing beyond it.

**Input:** "Jeden Montag: Abhängigkeiten prüfen, niedrige Prio"

```
linear_save_issue(
  team: "einself",
  project: "mu",
  title: "Check dependencies",
  description: """
**As a** developer on mu-format,
**I want** the project's dependencies reviewed every Monday,
**so that** outdated or vulnerable versions surface early instead of at the next upgrade.

Recurrence: every Monday (not natively supported by Linear).
""",
  priority: 4,
  assignee: "pierre"
)
```

---

Unsupported features are handled transparently.

**Input:** "Task: UI design, starting next Monday, assign to me and Alex"

```
linear_save_issue(
  team: "einself",
  project: "mu",
  title: "UI design",
  description: """
**As a** developer on mu-format,
**I want** the UI designed,
**so that** the application has a visual foundation to build on.

Start date: <next Monday>. Also assigned: Alex (Linear supports one assignee).
""",
  assignee: "pierre"
)
```
