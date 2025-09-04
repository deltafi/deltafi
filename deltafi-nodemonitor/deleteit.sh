#!/bin/bash
#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#

set -uo pipefail

# Env vars expected: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB
PG_HOST="${DELTAFI_POSTGRES_SERVICE_HOST:-deltafi-postgres}"
PG_PORT="${DELTAFI_POSTGRES_SERVICE_PORT:-5432}"
PG_URL="postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@${PG_HOST}:${PG_PORT}/${POSTGRES_DB}"
DATA_DIR="/data/deltafi/minio"
WORKER_COUNT="${FASTDELETE_WORKERS:-1}"
NODE_NAME="${NODE_NAME:-localhost}"

_log() {
    local worker_id="$1"
    local log_level="$2"
    local message="$3"
    timestamp=$(date "+%Y-%m-%dT%H:%M:%SZ")
    echo "{\"timestamp\":\"$timestamp\",\"level\":\"$log_level\",\"worker\":\"$worker_id\",\"message\":\"$message\"}"
}

_info() { _log "$1" "INFO" "$2"; }
_warn() { _log "$1" "WARN" "$2"; }
_error() { _log "$1" "ERROR" "$2"; }
_debug() { [[ -z ${DEBUG:-} ]] || _log "$1" "DEBUG" "$2"; }

worker_loop() {
    local worker_id=$1
    _info "$worker_id" "Fast delete worker starting on $NODE_NAME"

    # Test database connection first
    _info "$worker_id" "Testing database connection..."
    test_conn=$(psql "$PG_URL" -Atc "SELECT 1;" 2>&1) || {
        _error "$worker_id" "Database connection test failed: $test_conn"
        exit 1
    }
    _info "$worker_id" "Database connection successful"

    while true; do
        # UPDATE to claim rows with timeout recovery
        # Rows with NULL started_at or started_at older than 1 minute are eligible
        # Note: psql returns 0 even when UPDATE affects 0 rows, so we don't need || error handling here
        results=$(psql "$PG_URL" -Atc "
            UPDATE pending_deletes
            SET started_at = now()
            WHERE (node, did) IN (
                SELECT node, did
                FROM pending_deletes
                WHERE node = '${NODE_NAME}'
                  AND (
                    started_at IS NULL
                    OR started_at < now() - interval '1 minute'
                  )
                LIMIT 100
                FOR UPDATE SKIP LOCKED
            )
            RETURNING bucket, did;
        " 2>&1)

        # Check for errors or empty results
        if [[ "$results" == *"ERROR"* ]]; then
            _error "$worker_id" "Query error: $results"
            sleep 5
            continue
        fi

        if [[ -z "$results" ]] || [[ "$results" == "UPDATE 0" ]]; then
            # No work to do
            sleep 1
            continue
        fi

        # Track successfully deleted items
        successful_dids=()
        delete_count=0

        while IFS='|' read -r bucket did; do
            # Skip empty lines
            [[ -z "$bucket" || -z "$did" ]] && continue

            prefix="${did:0:3}"
            path="${DATA_DIR}/${bucket}/${prefix}/${did}"

            # Try to delete the file and track success
            if rm -rf "$path" 2>/dev/null; then
                successful_dids+=("'$did'")
                ((delete_count++))
            else
                _warn "$worker_id" "Failed to delete $path"
            fi
        done <<< "$results"

        # Remove successfully deleted items from the database (only if we have some)
        if (( ${#successful_dids[@]} > 0 )); then
            # PostgreSQL returns "DELETE n" where n is the count - extract that number
            delete_result=$(psql "$PG_URL" -tc "
                DELETE FROM pending_deletes 
                WHERE did IN ($(IFS=,; echo "${successful_dids[*]}"));
            ")
            # Extract the number from "DELETE n" output
            deleted=$(echo "$delete_result" | grep -oE '[0-9]+' || echo "0")
            _info "$worker_id" "Deleted content for $delete_count deltaFiles"
        fi

        # If we got a full batch, keep processing immediately
        if (( delete_count < 100 )); then
            sleep 1
        fi
    done
}

# Main script
_info "main" "Starting $WORKER_COUNT fast delete workers on $NODE_NAME"
_info "main" "Postgres URL: $PG_URL"
_info "main" "Monitoring: $DATA_DIR"

# Fork worker processes
pids=()
for ((i=0; i<$WORKER_COUNT; i++)); do
    worker_loop "worker-$i" &
    pid=$!
    pids+=($pid)
    _info "main" "Started worker-$i with PID $pid"
done

# Trap signals to cleanup child processes
cleanup() {
    _info "main" "Shutting down workers..."
    for pid in "${pids[@]}"; do
        kill -TERM "$pid" 2>/dev/null || true
    done
    wait
    exit 0
}

trap cleanup SIGTERM SIGINT

# Wait for all workers (they should run forever)
# Using wait with explicit PID list to ensure proper waiting
for pid in "${pids[@]}"; do
    wait "$pid"
done
