# PRD: Frontend updates for TicketBooking backend hardening

**Applies to:** [TicketBookingApplication](https://github.com/PrajwalAL22/TicketBookingApplication) (no longer deployed live), the separate frontend repo consuming this backend's API.

**Status:** Draft — write/update as each backend phase in [CLAUDE.md](../CLAUDE.md) ships, since each phase changes the API contract the UI depends on.

## 1. Problem & goal

The current frontend talks to a backend with no auth, no validation, and no concurrency safety. As the backend is hardened in phases (see `CLAUDE.md`), the UI needs matching updates so users get real feedback instead of silent failures or confusing generic errors — e.g. today a sold-out journey or a duplicate double-click booking likely just returns a raw error or nothing.

Goal: keep the UI in lockstep with each backend phase so the app is demoable end-to-end at every stage, not just at the very end.

## 2. Non-goals

- No visual redesign — this PRD is about behavior/data-flow changes forced by the API, not a UI facelift.
- No new user-facing features beyond what the backend phases enable (e.g. no "favorite journeys" — out of scope).

## 3. Requirements by backend phase

### Phase 1 — Idempotent, concurrency-safe booking
- **Idempotency key generation**: on opening the booking form (or on submit), generate a client-side UUID and send it as the idempotency key with the `/book` request. Persist it in component state so a retry (network failure, double-click) reuses the same key rather than generating a new one.
- **Booking button**: disable immediately on click until the request resolves, to reduce (not replace — the key does that) accidental double submits.
- **Conflict/failure feedback**: distinguish "seats sold out" (still valid response, e.g. 400) from "your booking already went through" (idempotent replay returns the original booking — show it as success, not an error) from a transient lock-contention retry-able error (e.g. 409) — show "high demand, please try again" with a retry button.

### Phase 2 — Security hardening
- **Login flow rework**: backend stops returning the raw `User` object (with password) and instead returns a token (JWT). UI must store the token (memory or httpOnly-cookie pattern, not `localStorage` if avoidable) and attach it as `Authorization: Bearer <token>` on every subsequent request.
- **Route guarding**: booking, cancellation, and "my tickets" views require a valid token — redirect to login if absent/expired.
- **Logout**: must actually clear the stored token client-side (today it's a no-op on the backend, so the UI is likely faking this already — verify and align once backend implements it for real).
- **Session expiry handling**: on a 401 from any authenticated call, clear local auth state and redirect to login with a "session expired" message.
- **CORS tightening**: once the backend restricts allowed origins, confirm whatever origin the frontend is served from (local dev, or a future redeploy) is on the allow-list before this ships.

### Phase 3 — API & architecture cleanup
- **Request/response shape changes**: booking/user endpoints move from query params to JSON bodies and typed DTOs. Update all API client calls (headers, payload shape) accordingly.
- **Field-level validation errors**: backend will return structured validation errors (e.g. `{ field: "noOfTickets", message: "must be at least 1" }`). UI should surface these inline on the relevant form field instead of a generic toast.
- **Consistent error envelope**: adopt whatever the new `@ControllerAdvice` error shape is (likely `{ timestamp, status, message, path }` or similar) in a single API-error-handling utility, replacing any ad-hoc error parsing.
- **ID type change** (`int` → `Long`): verify no client-side assumptions about ID size/format break (unlikely in JS, but check serialization if IDs are used as object keys elsewhere).

### Phase 4 — Production readiness
- Mostly backend/infra — no functional UI changes expected. Verify:
  - The frontend's API base URL matches whatever environment the hardened backend is deployed to (relevant again once/if either app is redeployed).
  - If OpenAPI docs are added, optionally link to them from a "developer info" or footer section (nice-to-have, not required).

### Phase 5 — Resume gold (last)
- **Rate limiting feedback**: if a user is rate-limited on booking attempts, show a clear "too many attempts, wait Ns" message (backend should return a `Retry-After` hint).
- **Pagination on "my tickets"**: replace any "load all bookings" call with paginated fetch + a "load more"/pager UI once the backend endpoint supports it.
- **Audit info display (optional)**: show booking created/updated timestamps on the "my tickets" view if useful.

## 4. Error/edge-case matrix (all phases)

| Scenario | Backend response (post-hardening) | UI behavior |
|---|---|---|
| Sold out journey | 400 with message | Inline message on booking form, keep form open |
| Duplicate submit (same idempotency key) | 200 with original booking | Treat as success, no duplicate booking shown |
| Concurrent booking conflict (optimistic lock retry exhausted) | 409 | "High demand, please retry" + retry button |
| Expired/invalid token | 401 | Clear session, redirect to login |
| Validation error | 400 with field errors | Inline field-level errors |
| Rate limited (Phase 5) | 429 + Retry-After | Countdown/backoff message |

## 5. Open questions

- Token storage strategy (memory vs. cookie) — depends on whether the backend can set httpOnly cookies given the frontend is (or will be) separately deployed from the backend (cross-origin cookie implications).
- Whether the frontend repo already has any API-client abstraction layer to update centrally, or calls are scattered per-component (needs a look at that repo before Phase 2 lands).

## 6. Sequencing

This PRD's sections should be implemented in the same order as the backend phases — do not build Phase 3's DTO-shape changes into the frontend before Phase 3 actually ships on the backend, since the contract will keep changing under Phases 1-2.
