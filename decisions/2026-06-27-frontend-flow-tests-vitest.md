# Decision: Add app-flow tests with the existing Vitest stack

**Date:** 2026-06-27  
**Status:** accepted

## Context

The frontend repo does not currently have a browser automation runner (Playwright/Cypress) or a backend+UI test harness. Adding a new runner would require extra dependency and environment setup before any flow coverage could run.

## Decision

Add a thin app-flow test layer in Vitest + React Testing Library. These tests render the real route tree, exercise the actual forms/navigation, and mock only the backend contract modules at the API boundary. This gives end-to-end coverage of the user journey inside the frontend app shell without introducing a second test runner.

## Consequences

- Core journeys are covered at the app level: registration/login, account management, and stock-trading subscriptions.
- The new suite complements the existing component/hook tests instead of replacing them.
- If true browser automation is added later, these tests can stay as fast smoke coverage.
