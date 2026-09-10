# TicketBooking Backend

Spring Boot 3 / Java 17 backend for a ticket booking app. Companion frontend: https://github.com/PrajwalAL22/TicketBookingApplication (not deployed live currently).

Concurrency-safe, idempotent seat booking (optimistic locking + retry with backoff), JWT authentication, Flyway-managed PostgreSQL schema, and the usual production-readiness scaffolding (Actuator health checks, OpenAPI docs, structured logging, Docker/CI). See [CLAUDE.md](CLAUDE.md) for the full build log and design rationale.

## Running locally

Requires a PostgreSQL instance and these environment variables (no defaults committed — see `src/main/resources/application.properties`):

| Variable | Required | Default | Notes |
|---|---|---|---|
| `DB_PASSWORD` | yes | — | Postgres password |
| `JWT_SECRET` | yes | — | HS256 signing key, >= 32 bytes |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` | no | `localhost` / `5432` / `Tickets-DB` / `postgres` | |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | no | `300000` (5 min) | short-lived, sent as `Authorization: Bearer` |
| `JWT_REFRESH_TOKEN_EXPIRATION_MS` | no | `86400000` (24h) | exchange at `/refresh` for a new access token |
| `ALLOWED_ORIGINS` | no | `http://localhost:3000,http://localhost:5173` | comma-separated CORS allow-list |
| `SPRING_PROFILES_ACTIVE` | no | `dev` | `dev` or `prod` — see `application-{profile}.properties` |

```bash
# PowerShell
$env:DB_PASSWORD="..."; $env:JWT_SECRET="..."; ./mvnw.cmd spring-boot:run
```

Schema is managed by Flyway (`src/main/resources/db/migration`) — it runs automatically on startup, no manual step needed.

## Running with Docker Compose

```bash
DB_PASSWORD=... JWT_SECRET=... docker compose up --build
```

Runs the app (port `5458`) alongside a Postgres container, with a healthcheck gate so the app waits for the database to actually be ready.

## Tests

```bash
./mvnw test
```

Two tiers: fast unit/service tests against H2 (Postgres-compatibility mode), plus one Testcontainers-backed integration test that spins up a real Postgres container to verify the Flyway migrations and optimistic-locking behavior against the actual target database. The Testcontainers test needs Docker running locally; CI runs both automatically.

## API docs

Once running: Swagger UI at `/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`, health check at `/actuator/health` — all public, everything else requires a JWT (`POST /login` to get one).

## Auth flow

`POST /login` returns an `accessToken` (5 min) and a `refreshToken` (24h). Send the access token as `Authorization: Bearer <accessToken>` on every request. When it expires, call `POST /refresh` with `{"refreshToken": "..."}` to get a new access token — the refresh token itself isn't reissued, so once *it* expires (24h), the client must log in again. The refresh token only works at `/refresh`; it's rejected everywhere else, and an access token is rejected at `/refresh` — each token only works where it's supposed to.
