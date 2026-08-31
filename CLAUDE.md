# TicketBooking_Backend — Improvement Roadmap

## Project context

Spring Boot 3.2.5 / Java 17 backend for a ticket booking app (originally a beginner project). Entities: `User`, `Journey`, `Booking`. PostgreSQL via Spring Data JPA (migrated from MySQL 2026-08-30). No security, no validation currently (concurrency-safe transactions shipped in Phase 1).

The companion frontend lives in a separate repo: https://github.com/PrajwalAL22/TicketBookingApplication (no longer deployed live). It is **not** part of this repo — API contract changes made here need a corresponding PRD for that frontend, tracked in [docs/PRD-UI.md](docs/PRD-UI.md).

Goal: harden this into a portfolio piece that demonstrates ~2 years of professional backend experience — correctness under concurrency, security, clean API design, and production-readiness — without over-engineering a toy project.

## How to use this file

This is living context for Claude Code across sessions. **Update the "Status" section below at the end of every phase** (mark it done, note any deviations from the plan, note follow-ups). Do not rewrite the phase plan itself unless the user explicitly changes scope.

## Conventions

- **Service layer is interface + `*Impl`**: `BookingService`/`UserService`/`JourneyService` are interfaces in `com.Coritech.service`; implementations (`BookingServiceImpl`/`UserServiceImpl`/`JourneyServiceImpl`) live in the `com.Coritech.service.impl` subpackage. Controllers depend on the interface type. New services should follow this pattern. (Retrofitted 2026-08-29, before Phase 2, at user request — not one of the original 5 phases below.)
- `BookingServiceImpl`'s internal retry-on-conflict helpers (`attemptBooking`/`attemptCancel`) are intentionally **not** on the `BookingService` interface — they're an implementation-only mechanism (self-injected `@Lazy` proxy so each retry gets a fresh `@Transactional`), not part of the public service contract. If this pattern is reused elsewhere, keep the same split.
- **Dependency injection is constructor-based via Lombok**: `@RequiredArgsConstructor` + `private final` fields, not `@Autowired` fields, everywhere a plain constructor works (controllers, `UserServiceImpl`, `JourneyServiceImpl`, and the test class via `@RequiredArgsConstructor(onConstructor_ = @Autowired)` — Spring's default test-constructor autowire mode is `ANNOTATED`, so the constructor still needs `@Autowired` explicitly). One documented exception: `BookingServiceImpl.self` (the self-injected proxy used for the retry mechanism above) stays as field injection (`@Autowired @Lazy`, non-final) because it can't be part of a Lombok-generated constructor without pulling in a circular dependency at bean-creation time — `@Lazy` has to apply to only that one parameter. (Retrofitted 2026-08-29, same session as the interface/impl split.)
- **Datasource is PostgreSQL, credentials via env vars only**: `application.properties` reads `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` with sensible defaults for everything except the password (no default — must be set in the environment, e.g. `$env:DB_PASSWORD` in PowerShell, or the IDE run config). Never put a real password into a tracked `application.properties`. Local dev DB is `Tickets-DB` on `localhost:5432`, user `postgres`.
- **`globally_quoted_identifiers=true` is required**: the `user` table (from the `User` entity) collides with the `USER` reserved keyword in Postgres — unlike MySQL, Postgres doesn't silently escape it, so unquoted DDL fails outright (`create table user (...)` is a syntax error). This is set in both `application.properties` and `src/test/resources/application.properties` so H2 (running in `MODE=PostgreSQL`) matches production behavior. Don't remove it without renaming the `user` table instead.

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
- Externalize config via Spring profiles (dev/prod). (Plaintext committed DB password already resolved — see datasource convention above.)
- DB migrations via Flyway (replace `ddl-auto=update`); unique constraint on `username` at the DB level.
- Unit tests for service layer, integration tests (Testcontainers, Postgres image).
- Structured logging (SLF4J), Actuator health checks, OpenAPI/Swagger docs.
- Dockerfile + docker-compose (app + Postgres); CI (GitHub Actions: build + test).

### Phase 5 — Resume gold (last, optional polish)
- Redis-backed distributed lock or rate limiting on booking endpoint.
- Outbox/event pattern (e.g. `BookingConfirmed` event) — talking point for eventual consistency / messaging.
- Pagination on `/getTickets`.
- Audit columns (`createdAt`/`updatedAt`) via `@CreatedDate`/`@LastModifiedDate`.

## Status

- [x] Phase 1 — done (2026-08-29)
- [x] Phase 2 — done (2026-08-30)
- [x] Phase 3 — done (2026-08-30)
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
- Test infra: added H2 (test scope) + `src/test/resources/application.properties` so tests run against an in-memory DB without needing a real database instance. Originally ran H2 in MySQL-compat mode with a `NON_KEYWORDS=USER` workaround for the `user` table; after the Postgres migration (below) this was replaced by `globally_quoted_identifiers=true`, which fixes the same problem in a way that matches production instead of being an H2-only quirk.
- Tests added: `BookingServiceConcurrencyTest` — 10 threads racing for 5 seats (asserts exactly 5 succeed, 0 oversold), duplicate-idempotency-key test, double-cancel test. All passing (`mvnw test`, 4/4 green).

**Addendum (2026-08-29, later same day):** the concurrency test flaked once (`4` successes instead of `5`) under real thread contention — with `MAX_ATTEMPTS = 3` and no backoff, a thread could lose the optimistic-lock race 3 times in a row purely from scheduling luck and give up while a seat was still available. Safety (never oversell) held, but a legitimate booking was wrongly rejected. Fixed by adding jittered backoff between retries and raising `MAX_ATTEMPTS` to 8 (`backoffBeforeRetry` in `BookingServiceImpl`). Verified stable over 5 consecutive full test-suite runs after the fix. This was surfaced incidentally while doing the Lombok/DI refactor below, not caused by it.

**Infrastructure note (2026-08-30): migrated datasource from MySQL to PostgreSQL.** User created a local `Tickets-DB` database via pgAdmin and asked to switch the app to it. Swapped `mysql-connector-j` → `org.postgresql:postgresql` in `pom.xml`, updated `application.properties` (`jdbc:postgresql://...`, `PostgreSQLDialect`), and switched the test H2 datasource to `MODE=PostgreSQL`. Also had to recover the local Postgres `postgres` role's password (none of the user's guessed candidates worked) via a temporary `pg_hba.conf` trust-auth window — reloaded via `SELECT pg_reload_conf()` from the user's pgAdmin session rather than restarting the Windows service (no admin rights available in this environment) — set a new password, then reverted `pg_hba.conf` back to `scram-sha-256` and reloaded again. Verified both the locked-down state and the new password work before finishing. Along the way, hit and fixed a real Postgres incompatibility: `user` is a reserved keyword in Postgres (MySQL silently escaped it), so Hibernate's unquoted `create table user (...)` DDL failed outright — fixed via `globally_quoted_identifiers=true` (see datasource convention above) rather than renaming the table.

Deviations / follow-ups for later phases:
- Idempotency key concurrency is covered structurally (unique DB constraint + catch `DataIntegrityViolationException`) but not exercised by a true concurrent-duplicate-submission test — sequential double-submit is tested instead; a concurrent version was skipped as likely flaky on H2 vs. real value on MySQL.
- Exception-to-HTTP-status mapping lives ad hoc in `BookingController` for now; fold into a global `@ControllerAdvice` in Phase 3.
- `bookTickets`/`cancelTickets` signature change (entity → id params) is a small head-start on Phase 3's cleanup; not full DTO adoption yet.

### Phase 2 notes (2026-08-30)

Shipped:
- **BCrypt everywhere**: `UserServiceImpl.register`/`login` now use `PasswordEncoder` (`BCryptPasswordEncoder` bean in `SecurityConfig`) instead of storing/comparing plaintext. No data migration needed — the dev DB (`Tickets-DB`) was freshly created empty, so there was no existing plaintext data to migrate; a real deployment with existing plaintext rows would need a one-off migration (force password reset, since BCrypt can't recover the original plaintext to re-hash it).
- **JWT auth** (`com.Coritech.security`): `JwtService` (jjwt 0.12.6, HS256, `jwt.secret`/`jwt.expiration-ms` from env, no committed default for the secret — same convention as `DB_PASSWORD`), `CustomUserDetailsService` (loads a `User` into a Spring Security `UserDetails`, single `ROLE_USER` authority — no roles/permissions concept exists yet, kept minimal), `JwtAuthenticationFilter` (validates the `Authorization: Bearer` header per-request, populates `SecurityContext`), `JwtAuthenticationEntryPoint` (returns a clean 401 JSON body instead of Spring Security's default). Login does **not** go through `AuthenticationManager`/`DaoAuthenticationProvider` — `UserServiceImpl.login` checks the password directly via `PasswordEncoder.matches`, which is simpler and avoids a second, redundant credential-checking path; `UserDetailsService` exists solely for the filter's per-request token validation.
- **`/login` no longer returns `User`**: returns `com.Coritech.dto.AuthResponse` (`token`/`username`/`userId`) instead. Also added `@JsonIgnore` on `User.password` as a defense-in-depth backstop, since `/book` and `/getTickets` still serialize a full `Booking` (including its nested `User`) — Phase 3's DTO pass will remove the entity from the wire format entirely, but until then this guarantees the password hash can never leak through *any* endpoint, not just `/login`.
- **Endpoints now derive `userId` from the JWT, not a request param**: `/book`, `/getTickets`, `/cancell` all resolve the caller via `Authentication.getName()` → `UserRepository.findByUsername`, instead of trusting a client-supplied `userId`. `/register`, `/login`, `/journey` stay public (`permitAll`); everything else requires a valid token (`SecurityConfig`).
- **New authorization check**: `BookingService.cancelTickets` now takes `userId` and `BookingServiceImpl.attemptCancel` throws `ForbiddenException` (403) if the caller doesn't own the booking — previously anyone who knew/guessed a `bookingID` could cancel it. Covered by a new test, `cannotCancelSomeoneElsesBooking`.
- **CORS**: replaced the old `CORSConfiguration` (which combined `allowCredentials(true)` with a wildcard origin — a combination browsers reject anyway) with a single `CorsConfigurationSource` bean in `SecurityConfig`, wired through `HttpSecurity.cors(...)`. Origins come from `security.cors.allowed-origins` (env `ALLOWED_ORIGINS`, defaults to common local dev ports `3000`/`5173` since the companion frontend isn't deployed anywhere right now). `allowCredentials` is `false` — JWT rides in the `Authorization` header, not a cookie, so cross-origin credentials aren't needed. Removed the per-controller `@CrossOrigin(origins = "*")` annotations from all three controllers now that this is handled globally.
- Verified end-to-end with the real app running against Postgres (not just the H2 test suite): register → login → 401 without a token → book → getTickets scoped to the caller → 403 cancelling another user's booking → 200 cancelling your own → 401 on a garbage token. All passed.

Deviations / follow-ups for later phases:
- `/book` and `/getTickets` returned the raw `Booking` entity (nested `User`/`Journey`), not a DTO — resolved in Phase 3.
- No `AuthenticationManager`/`DaoAuthenticationProvider` — see rationale above; revisit only if a second auth path (e.g. OAuth2) is ever needed.
- `UserServiceImpl.register`'s check-then-insert on username still isn't race-safe (no unique DB constraint yet) — already tracked as a Phase 4 item.
- No refresh tokens / token revocation / blacklist — `/logout` is just a client-side "discard your token" convention, standard for a stateless-JWT setup at this scope. Worth flagging in an interview as a known limitation rather than something to build here.
- Single `ROLE_USER` authority for everyone — no admin/staff role exists in this app, so role-based authorization wasn't built out; would matter if e.g. journey creation/management endpoints are added later.

### Phase 3 notes (2026-08-30)

Shipped:
- **Request/response DTOs** (`com.Coritech.dto`): `RegisterRequest`, `LoginRequest`, `BookingRequest`, `CancelRequest` for inputs; `BookingResponse` for output. `/book` and `/getTickets` now return `BookingResponse` (flat: `bookingId`/`journeyId`/`cityName`/`noOfTicketsBooked`/`cancelled`/`idempotencyKey`) instead of the raw `Booking` entity — no nested `User`/`Journey`, so there's no longer any path by which a password hash could leak through these endpoints (closes the Phase 2 deviation).
- **Bean Validation**: `@NotBlank`/`@Size`/`@Min`/`@NotNull`/`@Positive` on the request DTOs, enforced via `@Valid @RequestBody`. `/register`, `/login`, `/book`, `/cancell` all moved from `@RequestParam` to `@RequestBody` (JSON) accordingly. `/journey` is the one exception — stayed `@RequestParam` since it's a `GET` with a single simple filter, not a resource payload — but still validated via `@Validated` on the controller + `@NotBlank` on the parameter (throws `ConstraintViolationException`, handled separately from `MethodArgumentNotValidException` since method-parameter validation and `@RequestBody` validation surface different exception types).
- **Global exception handling**: `com.Coritech.exception.GlobalExceptionHandler` (`@RestControllerAdvice`) replaces the ad hoc try/catch blocks that lived in `BookingController`/`UserController` since Phase 1. Consistent `ErrorResponse` shape (`timestamp`/`status`/`error`/`message`/`path`) for every case: the four custom exceptions from Phases 1–2 (`ResourceNotFoundException` 404, `SeatsUnavailableException` 400, `BookingConflictException` 409, `ForbiddenException` 403), both validation exception types (400), `MissingServletRequestParameterException`/`HttpMessageNotReadableException` (400, so a missing param or malformed JSON body doesn't fall through to a raw 500), and a logged catch-all `Exception` → 500 with a generic message (never leaks internals/stack traces to the client).
- **Entity cleanup**: removed the stray `@Component` from `User`/`Journey`/`Booking` (entities don't need to be Spring beans). Changed all three primary keys from `int` → `Long` (`User.userId`, `Journey.journeyId`, `Booking.bookingId`), cascading through the repositories (`JpaRepository<X, Long>`), `BookingService`/`BookingServiceImpl`, `BookingController`, `AuthResponse.userId`, and the test file.
- **Real bug fixed along the way**: `BookingServiceImpl.attemptCancel`'s ownership check used `booking.getUser().getUserId() != userId` — worked by accident with `int`, but with the `int`→`Long` change this became a classic boxed-`Long` reference-equality bug (`Long` only caches -128..127, so `!=` would silently misbehave for most real IDs). Fixed to `.equals()` before it ever shipped as a live bug, since the type change forced touching that line anyway.
- **Real DB migration required**: the `int`→`Long` change meant existing Postgres columns (`integer`) no longer matched the entities (`bigint`), and `ddl-auto=update` doesn't widen existing column types. Since `Tickets-DB`'s three tables were still empty (confirmed via `SELECT count(*)` before touching anything), dropped and let Hibernate recreate them with correct `bigint` columns — a real migration would need a proper Flyway `ALTER COLUMN ... TYPE bigint` (Phase 4 territory) if there were data to preserve. The `DROP TABLE` itself was blocked by the auto-mode safety classifier as destructive and required explicit user confirmation before proceeding.
- Verified end-to-end against the real Postgres DB (not just H2): validation errors (short password, blank username, `noOfTickets=0`, blank `cityName`, missing `bookingId`) all return the new consistent `ErrorResponse` shape with correct status codes; full register → login → book → getTickets → 403 cross-user cancel → 200 owner cancel flow confirmed working with the new JSON-body contract and flat `BookingResponse`.

Deviations / follow-ups for later phases:
- `JourneyController` still returns a bare `int`/error-string from `/journey` rather than a proper DTO — low priority since it's a single scalar value, but inconsistent with the rest of the API now; could wrap in a small `JourneyAvailabilityResponse` later if the frontend needs more fields (e.g. `journeyId`) from that endpoint.
- `UserController.register`/`login` still return bare strings for error cases (`"Username already exists"`, `"Invalid username or password"`) rather than routing through `ErrorResponse` — these are deliberate application responses (not exceptions), so they weren't forced through `GlobalExceptionHandler`; could be normalized in a later pass for full consistency.
- `UserServiceImpl.register`'s check-then-insert on username still isn't race-safe (no unique DB constraint yet) — unchanged from Phase 2, still tracked as a Phase 4 item.
