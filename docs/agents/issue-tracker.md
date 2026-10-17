# Issue tracker: GitHub

Issues and PRDs for this repo live as GitHub issues. Use the `gh` CLI for all operations.

## Fork: `origin` is the only tracker

This clone has two remotes:

- `origin` — `shahidansari311/saathi-ai` — **the issue tracker.** Everything the skills read or write lives here.
- `upstream` — `shahidansari311/saathi-ai` — the project this is forked from. **Off limits.** Never open, comment on, label, or close anything there, and don't read its issues for context. If a skill's output would reference an upstream issue, drop the reference.

`gh` infers the repo from the remotes and may prompt or guess when both are present. Pass `--repo shahidansari311/saathi-ai` on **every** `gh issue` and `gh pr` command, not only the ambiguous-looking ones — the examples below all carry it for that reason.

`gh api` is the exception: it has no `--repo` flag and rejects one as an unknown flag. Encode the repository in the endpoint path instead — `repos/shahidansari311/saathi-ai/...`.

## Conventions

- **Create an issue**: `gh issue create --repo shahidansari311/saathi-ai --title "..." --body "..."`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> --repo shahidansari311/saathi-ai --comments`, filtering comments by `jq` and also fetching labels.
- **List issues**: `gh issue list --repo shahidansari311/saathi-ai --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Comment on an issue**: `gh issue comment <number> --repo shahidansari311/saathi-ai --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --repo shahidansari311/saathi-ai --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --repo shahidansari311/saathi-ai --comment "..."`

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

Left off deliberately: PRs on this fork are the maintainer's own branches, not incoming requests. `/triage` sees issues only. The `gh pr` triage flow below is **inactive** — it applies only if the flag above is flipped to `yes`.

When set to `yes`, PRs run through the same labels and states as issues, using the `gh pr` equivalents:

- **Read a PR**: `gh pr view <number> --repo shahidansari311/saathi-ai --comments` and `gh pr diff <number> --repo shahidansari311/saathi-ai` for the diff.
- **List external PRs for triage**: `gh pr list --repo shahidansari311/saathi-ai --state open --json number,title,body,labels,author,authorAssociation,comments` then keep only `authorAssociation` of `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR`, or `NONE` (drop `OWNER`/`MEMBER`/`COLLABORATOR`).
- **Comment / label / close**: `gh pr comment <number> --repo shahidansari311/saathi-ai`, `gh pr edit <number> --repo shahidansari311/saathi-ai --add-label`/`--remove-label`, `gh pr close <number> --repo shahidansari311/saathi-ai`.

GitHub shares one number space across issues and PRs, so a bare `#42` may be either — resolve with `gh pr view 42 --repo shahidansari311/saathi-ai` and fall back to `gh issue view 42 --repo shahidansari311/saathi-ai`.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --repo shahidansari311/saathi-ai --comments`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. `gh issue create --repo shahidansari311/saathi-ai --label wayfinder:map`.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue (`gh api` on the sub-issues endpoint). Where sub-issues aren't enabled, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: GitHub's **native issue dependencies** — the canonical, UI-visible representation. Add an edge with `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, where `<blocker-db-id>` is the blocker's numeric **database id** (`gh api repos/<owner>/<repo>/issues/<n> --jq .id`, _not_ the `#number` or `node_id`). GitHub reports `issue_dependencies_summary.blocked_by` (open blockers only — the live gate). Where dependencies aren't available, fall back to a `Blocked by: #<n>, #<n>` line at the top of the child body. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children (`gh issue list --repo shahidansari311/saathi-ai --state open`, scoped to the map's sub-issues / task list), drop any with an open blocker (`issue_dependencies_summary.blocked_by > 0`, or an open issue in the `Blocked by` line) or an assignee; first in map order wins.
- **Claim**: `gh issue edit <n> --repo shahidansari311/saathi-ai --add-assignee @me` — the session's first write.
- **Resolve**: `gh issue comment <n> --repo shahidansari311/saathi-ai --body "<answer>"`, then `gh issue close <n> --repo shahidansari311/saathi-ai`, then append a context pointer (gist + link) to the map's Decisions-so-far.

  **Exception — during a multi-agent sweep.** When several ticket agents run at once, they must not each edit the map body: concurrent edits to one issue body overwrite each other. In that mode the agent leaves its context pointer as a comment on its own closed ticket, and the driving session appends to the map centrally after each batch. See `docs/agents/ticket-sweep-handoff.md`, "How to resume", steps 4 and 5.
