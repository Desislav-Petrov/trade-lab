---
name: tocopilot
description: Use when the user runs /tocopilot to outsource the latest decomposer-agent task file to GitHub Copilot. Automatically finds the most recently written file in tasks/, reads it, and creates 1 BE PR and 1 FE PR via github_create_pull_request_with_copilot. No arguments needed — run immediately after the decomposer-agent finishes. Each PR instructs Copilot to use the correct engineer agent, follow all AGENTS.md rules, and pass all tests/build before requesting review.
---

# Outsource to GitHub Copilot

Triggered by `/tocopilot` with no arguments — run immediately after the decomposer-agent has written its task file.

## How it finds the task file

It lists `tasks/`, picks the most recently modified `.md` file, and reads it in full. It confirms the filename with the user before creating any PRs.

## What this command does

1. **Finds and reads** the most recently modified file in `tasks/`.
2. **Reads** `AGENTS.md` for standing project rules.
3. **Confirms** the filename with the user.
4. **Creates a BE PR** via `github_create_pull_request_with_copilot` — title `[BE] <usecase-slug>`, full task file contents passed verbatim plus all standing instructions.
5. **Creates a FE PR** via `github_create_pull_request_with_copilot` — title `[FE] <usecase-slug>`, full task file contents passed verbatim plus all standing instructions.
6. **Reports** both PR URLs to the user.

## Standing instructions always included in every PR

- Use the correct engineer agent (backend-engineer-agent / frontend-engineer-agent)
- One task at a time, one layer at a time, in dependency order from the task file
- Read domain/ docs and standards/ docs before writing code
- FE must read the API contract (services/contract/{domain}-openapi.yaml) before writing any client code
- Always create a branch, never commit to main
- BE: run full `gradlew build` and pass before requesting review
- FE: run all frontend tests and pass before requesting review
- Follow all rules in AGENTS.md

## Rules

- Never split or re-interpret the task file — pass it verbatim to Copilot.
- Never implement anything directly — delegate 100% to Copilot.
