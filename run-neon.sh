#!/bin/sh
set -e

# ==============================================================================
# Start the CelebStash backend against the linked Neon Postgres branch.
#
# Reads the Neon connection string from .env.local (written by `neon link`, and
# git-ignored via the existing "*.local" rule) and converts it into the
# SPRING_DATASOURCE_* variables that application.properties already expects.
# No credential is ever written into a tracked file.
#
# Usage:
#   ./run-neon.sh              # direct (unpooled) endpoint  [recommended]
#   ./run-neon.sh --pooled     # pooled (PgBouncer) endpoint
# ==============================================================================

cd "$(dirname "$0")"

ENV_FILE=".env.local"
if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: $ENV_FILE not found. Run:  neon link --project-id <id> --branch <branch>" >&2
    exit 1
fi

USE_POOLED=0
[ "$1" = "--pooled" ] && USE_POOLED=1

# Pull the raw URL out of .env.local without echoing it.
if [ "$USE_POOLED" -eq 1 ]; then
    RAW_URL=$(grep -E '^DATABASE_URL=' "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '"' | tr -d '\r')
else
    RAW_URL=$(grep -E '^DATABASE_URL_UNPOOLED=' "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '"' | tr -d '\r')
    # Older `neon link` versions only write DATABASE_URL; fall back to it.
    if [ -z "$RAW_URL" ]; then
        RAW_URL=$(grep -E '^DATABASE_URL=' "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '"' | tr -d '\r')
    fi
fi

if [ -z "$RAW_URL" ]; then
    echo "ERROR: no DATABASE_URL found in $ENV_FILE" >&2
    exit 1
fi

# postgresql://user:password@host/db?params  ->  JDBC url + user + password
REST="${RAW_URL#*://}"
CREDENTIALS="${REST%%@*}"
HOST_AND_DB="${REST#*@}"
DB_USER="${CREDENTIALS%%:*}"
DB_PASS="${CREDENTIALS#*:}"
DB_HOST_PORT="${HOST_AND_DB%%/*}"
DB_NAME_PARAMS="${HOST_AND_DB#*/}"
DB_NAME="${DB_NAME_PARAMS%%\?*}"

# Neon requires TLS. channel_binding is a libpq-only parameter and is dropped here
# because the PostgreSQL JDBC driver does not accept it.
JDBC_PARAMS="sslmode=require"
if [ "$USE_POOLED" -eq 1 ]; then
    # PgBouncer runs in transaction pooling mode; disable server-side prepared
    # statements so pgjdbc does not reuse a statement across pooled backends.
    JDBC_PARAMS="${JDBC_PARAMS}&prepareThreshold=0"
fi

SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST_PORT}/${DB_NAME}?${JDBC_PARAMS}"
export SPRING_DATASOURCE_URL
export SPRING_DATASOURCE_USERNAME="$DB_USER"
export SPRING_DATASOURCE_PASSWORD="$DB_PASS"

# Export every other KEY=VALUE in .env.local (SMTP credentials, seed passwords, ...) so secrets
# stay in the git-ignored file rather than in application.properties.
while IFS= read -r line; do
    case "$line" in
        ''|\#*|DATABASE_URL*|NEON_BRANCH*) continue ;;
        *=*)
            key="${line%%=*}"
            value="${line#*=}"
            value=$(printf '%s' "$value" | tr -d '"' | tr -d '\r')
            export "$key=$value"
            ;;
    esac
done < "$ENV_FILE"

echo "[run-neon] endpoint : ${DB_HOST_PORT}"
echo "[run-neon] database : ${DB_NAME}"
echo "[run-neon] user     : ${DB_USER}"
echo "[run-neon] jdbc     : jdbc:postgresql://${DB_HOST_PORT}/${DB_NAME}?${JDBC_PARAMS}"
echo "[run-neon] password : (read from .env.local, not shown)"

exec ./mvnw spring-boot:run "$@"
