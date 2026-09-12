#!/bin/sh
set -e

# ==============================================================================
# Docker Entrypoint Script for CelebStash Backend
# Automatically converts Cloud DATABASE_URL (Render, Supabase, Neon, Railway)
# into Spring Boot compatible JDBC properties (SPRING_DATASOURCE_*)
# ==============================================================================

# An explicitly configured SPRING_DATASOURCE_URL always wins. This matters when the platform
# injects its own DATABASE_URL (e.g. a linked Render Postgres) but the service is meant to talk
# to a different database such as Neon: converting DATABASE_URL here would otherwise replace the
# intended URL while leaving the operator-supplied username/password in place, pointing the app
# at the wrong database with mismatched credentials.
if [ -n "$SPRING_DATASOURCE_URL" ]; then
    echo "[docker-entrypoint] SPRING_DATASOURCE_URL is set explicitly; ignoring DATABASE_URL."
    if [ -n "$DATABASE_URL" ]; then
        echo "[docker-entrypoint] NOTE: DATABASE_URL is also set and will NOT be used."
    fi
elif [ -n "$DATABASE_URL" ]; then
    case "$DATABASE_URL" in
        jdbc:*)
            export SPRING_DATASOURCE_URL="$DATABASE_URL"
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
            DB_NAME="${DB_NAME_PARAMS%%\?*}"
            case "$DB_NAME_PARAMS" in
                *\?*) DB_PARAMS="${DB_NAME_PARAMS#*\?}" ;;
                *)    DB_PARAMS="" ;;
            esac

            # channel_binding is a libpq-only parameter (Neon includes it). The PostgreSQL JDBC
            # driver rejects unknown properties, so strip it before building the JDBC URL.
            DB_PARAMS=$(printf '%s' "$DB_PARAMS" \
                | sed -E 's/(^|&)channel_binding=[^&]*//g' \
                | sed -E 's/^&+//; s/&+$//; s/&&+/\&/g')

            # Managed providers (Neon, Supabase, RDS, ...) require TLS. Only a local host may skip it.
            case "$DB_PARAMS" in
                *sslmode=*) : ;;
                *)
                    case "$DB_HOST_PORT" in
                        localhost*|127.0.0.1*) : ;;
                        *)
                            if [ -n "$DB_PARAMS" ]; then
                                DB_PARAMS="${DB_PARAMS}&sslmode=require"
                            else
                                DB_PARAMS="sslmode=require"
                            fi
                            ;;
                    esac
                    ;;
            esac

            if [ -n "$DB_PARAMS" ]; then
                export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST_PORT}/${DB_NAME}?${DB_PARAMS}"
            else
                export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST_PORT}/${DB_NAME}"
            fi

            if [ -n "$DB_USER" ] && [ -z "$SPRING_DATASOURCE_USERNAME" ]; then
                export SPRING_DATASOURCE_USERNAME="$DB_USER"
            fi
            if [ -n "$DB_PASS" ] && [ -z "$SPRING_DATASOURCE_PASSWORD" ]; then
                export SPRING_DATASOURCE_PASSWORD="$DB_PASS"
            fi
            echo "[docker-entrypoint] Configured JDBC URL: ${SPRING_DATASOURCE_URL}"
            ;;
    esac
fi

# Surface which database this container will actually use (host only, never credentials).
echo "[docker-entrypoint] Effective datasource host: $(printf '%s' "${SPRING_DATASOURCE_URL:-<default from application.properties>}" | sed -E 's#^jdbc:postgresql://([^/]+)/.*#\1#')"

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
