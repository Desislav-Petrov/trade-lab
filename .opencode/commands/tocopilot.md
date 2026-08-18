---
description: Outsource the latest decomposed task file to GitHub Copilot — run with no arguments after the decomposer-agent finishes. Finds the most recently written file in tasks/, creates 1 BE PR and 1 FE PR.
---

You are outsourcing a fully decomposed implementation task list to GitHub Copilot.

## Step 1 — Find the task file

List all files in the `tasks/` directory of the current project. Pick the most recently modified one. Read its full contents.

Also read `AGENTS.md` for standing project rules.

Confirm to the user which file you are using before proceeding.

## Step 2 — Create the BE pull request

Call `github_create_pull_request_with_copilot` with:
- Title: `[BE] <usecase-slug from filename>`
- Problem statement must include:
  - The full contents of the task file, verbatim.
  - "Use the backend-engineer-agent for all backend tasks."
  - "Implement one task at a time, one layer at a time, in dependency order as shown in the task file."
  - "Always read the relevant standards doc (standards/backend.md, standards/architecture.md) before writing code."
  - "Read domain/ docs before implementing. Never invent behaviour not described there."
  - "Always create a branch, never commit to main."
  - "Run the full gradlew build and make sure it passes before marking the PR ready for review. NEVER submit the PR before doing that."
  - "Follow all rules in AGENTS.md."

## Step 3 — Create the FE pull request

Call `github_create_pull_request_with_copilot` with:
- Title: `[FE] <usecase-slug from filename>`
- Problem statement must include:
  - The full contents of the task file, verbatim.
  - "Use the frontend-engineer-agent for all frontend tasks."
  - "Implement one task at a time, one layer at a time, in dependency order as shown in the task file."
  - "Always read the relevant standards doc (standards/frontend.md, standards/architecture.md) before writing code."
  - "Read the backend API contract at services/contract/{domain}-openapi.yaml before writing any client code."
  - "Read domain/ docs before implementing. Never invent behaviour not described there."
  - "Always create a branch, never commit to main."
  - "Run all frontend tests and make sure they pass before marking the PR ready for review. NEVER submit the PR before doing that."
  - "Follow all rules in AGENTS.md."

## Step 4 — Report back

Return both PR URLs to the user. Confirm that Copilot has been instructed to use the correct agents, follow all project rules, run tests/build, and only request review once everything passes.
