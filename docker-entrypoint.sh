#!/bin/sh
set -e

# ==============================================================================
# Docker Entrypoint Script for CelebStash Backend
# Automatically converts Cloud DATABASE_URL (Render, Supabase, Neon, Railway)
# into Spring Boot compatible JDBC properties (SPRING_DATASOURCE_*)
# ==============================================================================

if [ -n "$DATABASE_URL" ]; then
    case "$DATABASE_URL" in
        jdbc:*)
            if [ -z "$SPRING_DATASOURCE_URL" ]; then
                export SPRING_DATASOURCE_URL="$DATABASE_URL"
            fi
            ;;
        postgres://*|postgresql://*)
            echo "[docker-entrypoint] Detected PostgreSQL URL in DATABASE_URL. Converting to Spring Boot JDBC format..."
            REST="${DATABASE_URL#*://}"

            # Extract user:password if present
            if echo "$REST" | grep -q "@"; then
                CREDENTIALS="${REST%%@*}"
                HOST_AND_DB="${REST#*@}"

                DB_USER="${CREDENTIALS%%:*}"
                DB_PASS="${CREDENTIALS#*:}"
            else
                DB_USER=""
                DB_PASS=""
                HOST_AND_DB="$REST"
            fi

            DB_HOST_PORT="${HOST_AND_DB%%/*}"
            DB_NAME_PARAMS="${HOST_AND_DB#*/}"

            export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST_PORT}/${DB_NAME_PARAMS}"
            if [ -n "$DB_USER" ] && [ -z "$SPRING_DATASOURCE_USERNAME" ]; then
                export SPRING_DATASOURCE_USERNAME="$DB_USER"
            fi
            if [ -n "$DB_PASS" ] && [ -z "$SPRING_DATASOURCE_PASSWORD" ]; then
                export SPRING_DATASOURCE_PASSWORD="$DB_PASS"
            fi
            echo "[docker-entrypoint] Configured JDBC URL: jdbc:postgresql://${DB_HOST_PORT}/${DB_NAME_PARAMS}"
            ;;
    esac
fi

# Automatically parse REDIS_URL if provided
if [ -n "$REDIS_URL" ]; then
    case "$REDIS_URL" in
        redis://*|rediss://*)
            echo "[docker-entrypoint] Detected REDIS_URL. Configuring Redis host and port..."
            REST="${REDIS_URL#*://}"
            if echo "$REST" | grep -q "@"; then
                REST="${REST#*@}"
            fi
            R_HOST="${REST%%:*}"
            R_PORT_PATH="${REST#*:}"
            R_PORT="${R_PORT_PATH%%/*}"
            if [ -n "$R_HOST" ] && [ -z "$REDIS_HOST" ]; then
                export REDIS_HOST="$R_HOST"
                export SPRING_DATA_REDIS_HOST="$R_HOST"
            fi
            if [ -n "$R_PORT" ] && [ -z "$REDIS_PORT" ]; then
                export REDIS_PORT="$R_PORT"
                export SPRING_DATA_REDIS_PORT="$R_PORT"
            fi
            echo "[docker-entrypoint] Configured Redis host: ${R_HOST}, port: ${R_PORT}"
            ;;
    esac
fi

if [ -z "$SPRING_DATASOURCE_URL" ] && [ -z "$DATABASE_URL" ]; then
    echo "=========================================================================="
    echo "[docker-entrypoint] WARNING: No database environment variable detected!"
    echo "[docker-entrypoint] Falling back to default: jdbc:postgresql://localhost:5432/celebstash"
    echo "[docker-entrypoint] In Render Dashboard -> Environment, set DATABASE_URL or SPRING_DATASOURCE_URL."
    echo "=========================================================================="
fi

exec "$@"
