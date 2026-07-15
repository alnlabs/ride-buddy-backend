#!/usr/bin/env bash
# Convenience wrapper — run from ride-buddy-backend/
exec "$(cd "$(dirname "$0")" && pwd)/scripts/run-local.sh" "$@"
