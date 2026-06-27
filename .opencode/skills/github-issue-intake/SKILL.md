---
name: github-issue-intake
description: GitHub issue #, comments, labels. Use when product-developer-agent is explicitly asked to inspect a GitHub issue number and summarize the request before Phase 1.
---

# GitHub Issue Intake

Use this skill only when the user explicitly provides a GitHub issue number and asks you to work from that issue.

## What to read

Use the GitHub MCP to read:

1. The issue title and body.
2. The issue comments.
3. The issue labels.

## What to produce

Summarize the issue into the normal product-developer Phase 1 intake:

1. What feature or change is being requested.
2. Who the actor or user is.
3. What success looks like.
4. What is unclear, conflicting, or missing.

## Rules

- Stay read-only.
- Do not infer requirements that are not present in the issue or comments.
- If comments conflict with the issue body, call that out plainly.
- If labels suggest scope, priority, or status, include that in the summary.
