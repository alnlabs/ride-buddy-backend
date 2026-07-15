#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SQL_DIR="${ROOT_DIR}/sql"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5433}"
DB_NAME="${DB_NAME:-ridebuddy}"
DB_USER="${DB_USER:-ridebuddy}"
DB_PASSWORD="${DB_PASSWORD:-ridebuddy}"

export PGPASSWORD="$DB_PASSWORD"

PSQL=(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1)

echo "Applying migrations to ${DB_HOST}:${DB_PORT}/${DB_NAME} ..."

# Ensure tracking table exists first
"${PSQL[@]}" -f "${SQL_DIR}/000_schema_migrations.sql" >/dev/null

for file in "${SQL_DIR}"/*.sql; do
  name="$(basename "$file")"
  applied="$("${PSQL[@]}" -tAc "SELECT 1 FROM schema_migrations WHERE filename = '${name}'" || true)"
  if [[ "$applied" == "1" ]]; then
    echo "  skip  ${name}"
    continue
  fi
  echo "  apply ${name}"
  "${PSQL[@]}" -f "$file" >/dev/null
  "${PSQL[@]}" -c "INSERT INTO schema_migrations (filename) VALUES ('${name}') ON CONFLICT DO NOTHING;" >/dev/null
done

echo "Migrations complete."
