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

set -euo pipefail

# Env vars expected: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB
PG_HOST="${DELTAFI_POSTGRES_SERVICE_HOST:-deltafi-postgres}"
PG_PORT="${DELTAFI_POSTGRES_SERVICE_PORT:-5432}"
PG_URL="postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@${PG_HOST}:${PG_PORT}/${POSTGRES_DB}"
DATA_DIR="/data/deltafi/minio"

_log() {
    local log_level="$1"
    local message="$2"
    timestamp=$(date "+%Y-%m-%dT%H:%M:%SZ")
    echo "{\"timestamp\":\"$timestamp\",\"level\":\"$log_level\",\"message\":\"$message\"}"
}

_info() { _log "INFO" "$1"; }
_warn() { _log "WARN" "$1"; }
_error() { _log "ERROR" "$1"; }
_debug() { [[ -z ${DEBUG:-} ]] || _log "DEBUG" "$1"; }

_info "Fast delete worker starting on $NODE_NAME"
_info "Postgres URL: $PG_URL"
_info "Monitoring: $DATA_DIR"

delete_did() {
    local bucket="$1"
    local did="$2"
    local prefix="${did:0:3}"
    local path="${DATA_DIR}/${bucket}/${prefix}/${did}"

    if [[ -d "$path" ]]; then
        rm -rf "$path"
        _info "Deleted $path"
    else
        _warn "Path not found, skipping delete: $path"
    fi
}

while true; do
    # Query up to 100 pending deletes for this node
    results=$(psql "$PG_URL" -Atc "
        SELECT bucket, did
        FROM pending_deletes
        WHERE node = '${NODE_NAME}'
        LIMIT 100;
    ")

    if [[ -z "$results" ]]; then
        sleep 1
        continue
    fi

    to_delete=()
    while IFS='|' read -r bucket did; do
        to_delete+=("'$did'")
        delete_did "$bucket" "$did"
    done <<< "$results"

    if (( ${#to_delete[@]} > 0 )); then
        delete_query="DELETE FROM pending_deletes WHERE node = '${NODE_NAME}' AND did IN ($(IFS=,; echo "${to_delete[*]}"))"
        output=$(psql "$PG_URL" -c "$delete_query" 2>&1)
        exit_code=$?

        if [[ $exit_code -eq 0 ]]; then
            while IFS= read -r line; do
                _info "psql: $line"
            done <<< "$output"
        else
            while IFS= read -r line; do
                _error "psql: $line"
            done <<< "$output"
        fi
    fi

    # If we got a full batch, keep processing immediately
    if (( $(wc -l <<< "$results") < 100 )); then
        sleep 0.1
    fi
done
