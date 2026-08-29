# TicketBooking_Backend — Improvement Roadmap

## Project context

Spring Boot 3.2.5 / Java 17 backend for a ticket booking app (originally a beginner project). Entities: `User`, `Journey`, `Booking`. MySQL via Spring Data JPA. No security, no transactions, no validation currently.

The companion frontend lives in a separate repo: https://github.com/PrajwalAL22/TicketBookingApplication (no longer deployed live). It is **not** part of this repo — API contract changes made here need a corresponding PRD for that frontend, tracked in [docs/PRD-UI.md](docs/PRD-UI.md).

Goal: harden this into a portfolio piece that demonstrates ~2 years of professional backend experience — correctness under concurrency, security, clean API design, and production-readiness — without over-engineering a toy project.

## How to use this file

This is living context for Claude Code across sessions. **Update the "Status" section below at the end of every phase** (mark it done, note any deviations from the plan, note follow-ups). Do not rewrite the phase plan itself unless the user explicitly changes scope.

## Conventions

- **Service layer is interface + `*Impl`**: `BookingService`/`UserService`/`JourneyService` are interfaces in `com.Coritech.service`; implementations (`BookingServiceImpl`/`UserServiceImpl`/`JourneyServiceImpl`) live in the `com.Coritech.service.impl` subpackage. Controllers depend on the interface type. New services should follow this pattern. (Retrofitted 2026-08-29, before Phase 2, at user request — not one of the original 5 phases below.)
- `BookingServiceImpl`'s internal retry-on-conflict helpers (`attemptBooking`/`attemptCancel`) are intentionally **not** on the `BookingService` interface — they're an implementation-only mechanism (self-injected `@Lazy` proxy so each retry gets a fresh `@Transactional`), not part of the public service contract. If this pattern is reused elsewhere, keep the same split.
- **Dependency injection is constructor-based via Lombok**: `@RequiredArgsConstructor` + `private final` fields, not `@Autowired` fields, everywhere a plain constructor works (controllers, `UserServiceImpl`, `JourneyServiceImpl`, and the test class via `@RequiredArgsConstructor(onConstructor_ = @Autowired)` — Spring's default test-constructor autowire mode is `ANNOTATED`, so the constructor still needs `@Autowired` explicitly). One documented exception: `BookingServiceImpl.self` (the self-injected proxy used for the retry mechanism above) stays as field injection (`@Autowired @Lazy`, non-final) because it can't be part of a Lombok-generated constructor without pulling in a circular dependency at bean-creation time — `@Lazy` has to apply to only that one parameter. (Retrofitted 2026-08-29, same session as the interface/impl split.)

## Phase plan

Phases are ordered by priority. "Resume gold" is deliberately last — it's polish, not foundation.

### Phase 1 — Idempotent, concurrency-safe booking
- `@Transactional` on `BookingService.bookTickets` / `cancelTickets`.
- Fix check-then-act race on `Journey.ticketsRemaining` (optimistic locking via `@Version`, with retry on conflict, or pessimistic `SELECT ... FOR UPDATE`).
- Idempotency key: client-supplied key stored as a unique column on `Booking`; duplicate submissions return the original booking instead of creating a new one.
- Tests proving the race is actually fixed (concurrent booking test).

### Phase 2 — Security hardening
- Hash passwords with BCrypt (`UserService`), migrate any existing plaintext data.
- Stop returning `User` (with password) from `/login`; return a safe DTO/token instead.
- Add real authentication (Spring Security + JWT) and protect booking/user endpoints — stop trusting `userId` as a request param.
- Lock down CORS (no `*` origin + `allowCredentials(true)` combo).

### Phase 3 — API & architecture cleanup
- Introduce request/response DTOs; stop binding `@RequestParam`/entities directly on controllers.
- Bean Validation (`@Valid`, `@NotBlank`, `@Min(1)`, etc.) on inputs.
- Global exception handling (`@ControllerAdvice`) with consistent error response shape.
- Remove stray `@Component` on `@Entity` classes; `int` → `Long` IDs.
- Switch POST bodies from `@RequestParam` to `@RequestBody` where appropriate.

### Phase 4 — Production readiness
- Externalize config via Spring profiles (dev/prod); remove committed plaintext DB password.
- DB migrations via Flyway (replace `ddl-auto=update`); unique constraint on `username` at the DB level.
- Unit tests for service layer, integration tests (Testcontainers).
- Structured logging (SLF4J), Actuator health checks, OpenAPI/Swagger docs.
- Dockerfile + docker-compose (app + MySQL); CI (GitHub Actions: build + test).

### Phase 5 — Resume gold (last, optional polish)
- Redis-backed distributed lock or rate limiting on booking endpoint.
- Outbox/event pattern (e.g. `BookingConfirmed` event) — talking point for eventual consistency / messaging.
- Pagination on `/getTickets`.
- Audit columns (`createdAt`/`updatedAt`) via `@CreatedDate`/`@LastModifiedDate`.

## Status

- [x] Phase 1 — done (2026-08-29)
- [ ] Phase 2 — not started
- [ ] Phase 3 — not started
- [ ] Phase 4 — not started
- [ ] Phase 5 — not started

_(Update this checklist and add a dated note under each completed phase summarizing what shipped and any deviations.)_

### Phase 1 notes (2026-08-29)

Shipped:
- `Journey.version` (`@Version`) for optimistic locking on `ticketsRemaining`.
- `Booking.idempotencyKey` (unique, nullable column) — repeat submissions with the same key return the original booking instead of creating a duplicate.
- `BookingService.bookTickets` / `cancelTickets` are now `@Transactional`, with a retry-on-conflict wrapper (`bookTickets`/`cancelTickets`) around a transactional attempt method (`attemptBooking`/`attemptCancel`), using a `@Lazy` self-injected proxy so each retry gets its own fresh transaction (a direct `this.attemptX(...)` call would bypass Spring's transaction proxy).
- Fixed a real bug found along the way: cancelling an already-cancelled booking used to double-refund seats back onto the journey. `attemptCancel` is now a no-op if `isCancelled` is already true.
- New exceptions (`com.Coritech.exception`): `ResourceNotFoundException` (404), `SeatsUnavailableException` (400), `BookingConflictException` (409, retries exhausted under contention) — currently handled with try/catch directly in `BookingController` (Phase 3 will generalize this into a `@ControllerAdvice`).
- `BookingService.bookTickets`/`cancelTickets` signatures changed to take `userId`/`journeyId`/`bookingId` (ints) instead of entity objects, so each retry attempt re-reads fresh entities from the DB rather than risking stale/detached objects — the public `/book` and `/cancell` HTTP contracts are unchanged (still take `userId`/`cityName`/`bookingID`), except `/book` now also accepts an optional `idempotencyKey` param.
- Test infra: added H2 (test scope) + `src/test/resources/application.properties` so tests run against an in-memory DB without needing a real MySQL instance. Note: H2 in MySQL-compat mode treats `USER` as a reserved word (the `user` table), worked around with `NON_KEYWORDS=USER` on the test JDBC URL rather than renaming the production table.
- Tests added: `BookingServiceConcurrencyTest` — 10 threads racing for 5 seats (asserts exactly 5 succeed, 0 oversold), duplicate-idempotency-key test, double-cancel test. All passing (`mvnw test`, 4/4 green).

**Addendum (2026-08-29, later same day):** the concurrency test flaked once (`4` successes instead of `5`) under real thread contention — with `MAX_ATTEMPTS = 3` and no backoff, a thread could lose the optimistic-lock race 3 times in a row purely from scheduling luck and give up while a seat was still available. Safety (never oversell) held, but a legitimate booking was wrongly rejected. Fixed by adding jittered backoff between retries and raising `MAX_ATTEMPTS` to 8 (`backoffBeforeRetry` in `BookingServiceImpl`). Verified stable over 5 consecutive full test-suite runs after the fix. This was surfaced incidentally while doing the Lombok/DI refactor below, not caused by it.

Deviations / follow-ups for later phases:
- Idempotency key concurrency is covered structurally (unique DB constraint + catch `DataIntegrityViolationException`) but not exercised by a true concurrent-duplicate-submission test — sequential double-submit is tested instead; a concurrent version was skipped as likely flaky on H2 vs. real value on MySQL.
- Exception-to-HTTP-status mapping lives ad hoc in `BookingController` for now; fold into a global `@ControllerAdvice` in Phase 3.
- `bookTickets`/`cancelTickets` signature change (entity → id params) is a small head-start on Phase 3's cleanup; not full DTO adoption yet.
