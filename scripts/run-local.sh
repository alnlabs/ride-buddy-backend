#!/usr/bin/env bash
# Start Ride Buddy backend locally: Postgres (Docker) → SQL migrations → Spring Boot
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5433}"
DB_NAME="${DB_NAME:-ridebuddy}"
DB_USER="${DB_USER:-ridebuddy}"
DB_PASSWORD="${DB_PASSWORD:-ridebuddy}"
SERVER_PORT="${SERVER_PORT:-8080}"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

log() { echo -e "${CYAN}==>${NC} $*"; }
ok() { echo -e "${GREEN}✔${NC} $*"; }
err() { echo -e "${RED}✖${NC} $*" >&2; }

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    err "Missing required command: $1"
    exit 1
  fi
}

need docker
need mvn

if ! docker info >/dev/null 2>&1; then
  err "Docker is not running. Start Docker Desktop and try again."
  exit 1
fi

log "Starting Postgres (PostGIS) on host port ${DB_PORT}…"
docker compose up -d postgres
echo "  (maps container :5432 → host :${DB_PORT}; avoids conflict with local Postgres on 5432)"

log "Waiting for Postgres healthy…"
for i in $(seq 1 90); do
  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' ride-buddy-postgres 2>/dev/null || echo starting)"
  if [[ "$status" == "healthy" ]]; then
    ok "Postgres is healthy"
    break
  fi
  if docker compose exec -T postgres pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
    # Extra settle time after pg_isready (PostGIS image under qemu can flake)
    sleep 3
    if docker compose exec -T postgres pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; then
      ok "Postgres is ready (status=${status})"
      break
    fi
  fi
  if [[ "$i" -eq 90 ]]; then
    err "Postgres did not become ready in time (last status=${status})"
    docker compose logs --tail=40 postgres || true
    exit 1
  fi
  sleep 1
done

run_psql() {
  docker compose exec -T -e PGPASSWORD="$DB_PASSWORD" postgres \
    psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 "$@"
}

apply_sql_file() {
  local file="$1"
  local name
  name="$(basename "$file")"
  # Files are mounted at /sql inside the container
  run_psql -f "/sql/${name}"
}

log "Applying SQL migrations…"
apply_sql_file "${ROOT_DIR}/sql/000_schema_migrations.sql" >/dev/null

for file in "${ROOT_DIR}/sql"/*.sql; do
  name="$(basename "$file")"
  applied="$(run_psql -tAc "SELECT 1 FROM schema_migrations WHERE filename = '${name}'" 2>/dev/null | tr -d '[:space:]' || true)"
  if [[ "$applied" == "1" ]]; then
    echo "  skip  ${name}"
    continue
  fi
  echo "  apply ${name}"
  apply_sql_file "$file" >/dev/null
  run_psql -c "INSERT INTO schema_migrations (filename) VALUES ('${name}') ON CONFLICT DO NOTHING;" >/dev/null
done
ok "Migrations complete"

echo ""
ok "API starting on http://localhost:${SERVER_PORT}"
echo "  Health:  http://localhost:${SERVER_PORT}/actuator/health"
echo "  Swagger: http://localhost:${SERVER_PORT}/swagger-ui.html"
echo "  Mock OTP: 123456"
echo "  DB:      ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo ""

export DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD SERVER_PORT
export APP_AUTH_MOCK_OTP="${APP_AUTH_MOCK_OTP:-true}"
export JWT_SECRET="${JWT_SECRET:-ride-buddy-dev-secret-key-must-be-at-least-32-chars-long}"

exec mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=${SERVER_PORT}"
