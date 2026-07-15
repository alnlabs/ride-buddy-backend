# Ride Buddy Backend

Java 17 · Spring Boot 3 · PostgreSQL / PostGIS · plain SQL migrations (no Flyway)

## Quick start

```bash
# One command — Postgres + migrations + API (Docker Desktop must be running)
./run-local.sh
```

Or step by step:

```bash
docker compose up -d postgres
./scripts/migrate.sh   # needs local psql, or use run-local.sh which migrates via Docker
mvn spring-boot:run
```

- Health: http://localhost:8080/actuator/health  
- Swagger: http://localhost:8080/swagger-ui.html  
- Mock OTP: `123456` (`app.auth.mock-otp=true`)

## API (Phase 0–3)

| Area | Endpoints |
|------|-----------|
| Auth | `POST /api/v1/auth/otp/request`, `/otp/verify`, `/refresh` |
| Profile | `GET/PUT /api/v1/profile/me`, `/me/places`, `/me/interests` |
| Vehicles | `GET/POST /api/v1/vehicles`, `PUT/DELETE /{id}`, `POST /{id}/primary` |
| Rides | `POST /api/v1/rides`, `/mine`, `/open`, `/search`, `/{id}`, `/{id}/cancel`, `/{id}/share` |
| Bookings | `POST /api/v1/bookings`, `/mine`, `/ride/{id}`, `/{id}/decide`, `/{id}/cancel` |

Payments in this base version: **cash only**.

## SQL migrations

Files in [`sql/`](sql/) applied by [`scripts/migrate.sh`](scripts/migrate.sh) via `psql`. Tracked in `schema_migrations`.

## Env

See [`.env.example`](.env.example). Defaults work with `docker-compose.yml`.
