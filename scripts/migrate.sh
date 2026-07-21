#!/usr/bin/env bash
# Apply sql/*.sql via Docker Postgres (same as run-local.sh). Falls back to host psql if needed.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SQL_DIR="${ROOT_DIR}/sql"
cd "$ROOT_DIR"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5433}"
DB_NAME="${DB_NAME:-ridebuddy}"
DB_USER="${DB_USER:-ridebuddy}"
DB_PASSWORD="${DB_PASSWORD:-ridebuddy}"

CONTAINER_NAME="${POSTGRES_CONTAINER:-ride-buddy-postgres}"

use_docker=false
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  if docker compose ps --status running --services 2>/dev/null | grep -qx 'postgres'; then
    use_docker=true
  elif docker inspect -f '{{.State.Running}}' "$CONTAINER_NAME" 2>/dev/null | grep -qx true; then
    use_docker=true
  fi
fi

if [[ "$use_docker" == true ]]; then
  echo "Applying migrations via Docker (${CONTAINER_NAME} → ${DB_NAME}) ..."

  run_psql() {
    docker compose exec -T -e PGPASSWORD="$DB_PASSWORD" postgres \
      psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 "$@"
  }

  apply_sql_file() {
    local name
    name="$(basename "$1")"
    # ./sql is mounted read-only at /sql in the container
    run_psql -f "/sql/${name}"
  }
else
  if ! command -v psql >/dev/null 2>&1; then
    echo "✖ Postgres is not reachable via Docker, and host psql was not found." >&2
    echo "  Start DB with:  docker compose up -d postgres" >&2
    echo "  Or install psql and set DB_HOST/DB_PORT." >&2
    exit 127
  fi
  echo "Applying migrations via host psql to ${DB_HOST}:${DB_PORT}/${DB_NAME} ..."
  export PGPASSWORD="$DB_PASSWORD"
  run_psql() {
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 "$@"
  }
  apply_sql_file() {
    run_psql -f "$1"
  }
fi

apply_sql_file "${SQL_DIR}/000_schema_migrations.sql" >/dev/null

for file in "${SQL_DIR}"/*.sql; do
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

echo "Migrations complete."
