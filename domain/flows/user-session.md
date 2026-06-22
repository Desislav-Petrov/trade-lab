# User Session

## Overview

Covers the authenticated experience after a successful login. Includes the
persistent session UI (topbar greeting, logout button), the profile page, and
the logout flow. The session state is held exclusively in the frontend Zustand
store — no backend session endpoint is involved.

---

## Flow A — Session UI Display

Renders the authenticated state in the shell layout on every page the logged-in
user visits.

### Actors

- **Authenticated User**: A guest who has completed the login flow and has an active session in the Zustand store.
- **Guest Browser**: The React frontend rendering the shell layout.

### Preconditions

- A `Session` exists in the Zustand store (user is logged in).

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Guest Browser | Read session store | Reads `firstName`, `lastName`, `email`, and `loggedInAt` from the Zustand session store. |
| 2 | Guest Browser | Render topbar user area | Displays "Logged in as [firstName] [lastName]" and a Logout button in the top-right of the Topbar. |
| 3 | Guest Browser | Render sidebar nav links | Displays Trade, Ledger, Market, **Accounts**, and Profile nav links in the Sidebar. |

### Postconditions

- The topbar shows the authenticated user's name and a Logout button.
- The sidebar shows Trade, Ledger, Market, Accounts, and Profile nav links.

---

## Flow B — View Profile Page

The authenticated user navigates to their profile page to view their full
account information.

### Actors

- **Authenticated User**: A logged-in user navigating to `/profile`.
- **Guest Browser**: The React frontend rendering the profile page.

### Preconditions

- A `Session` exists in the Zustand store.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Navigate to `/profile` | Clicks the Profile link in the sidebar or navigates directly to `/profile`. |
| 2 | Guest Browser | Read session store | Reads all fields from the Zustand session store. |
| 3 | Guest Browser | Render profile page | Displays `firstName`, `lastName`, `email`, `address`, `status`, and `createdAt` (formatted as local date). Also displays the current date. |

### Postconditions

- The profile page is visible with all user fields rendered.

### Error Cases

| Scenario | Condition | Outcome |
|----------|-----------|----------|
| No active session | User navigates to `/profile` without a session | Redirect to `/login`. |

---

## Flow C — Logout

The authenticated user ends their session and returns to the login/register
entry point.

### Actors

- **Authenticated User**: A logged-in user who clicks the Logout button.
- **Guest Browser**: The React frontend clearing the session store.

### Preconditions

- A `Session` exists in the Zustand store.

### Steps

| # | Actor | Action | Description |
|---|-------|--------|-------------|
| 1 | Authenticated User | Click Logout | Clicks the Logout button in the topbar. |
| 2 | Guest Browser | Clear session store | Removes all data from the Zustand session store. |
| 3 | Guest Browser | Redirect to login | Navigates to `/login`. |
| 4 | Guest Browser | Render unauthenticated topbar | Topbar user area reverts to showing "Login or Register". Sidebar nav links are no longer shown. |

### Postconditions

- The Zustand session store is empty.
- The user is on the `/login` page.
- The topbar shows "Login or Register".
- The sidebar nav links (Trade, Ledger, Market, Accounts, Profile) are no longer visible.

---

## Domain Models Involved

- **Session**: Read in flows A and B; cleared in flow C.
- **User**: The source of all data stored in the Session (populated during the login flow).
