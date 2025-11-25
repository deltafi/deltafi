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

set -e

# Check if graphite directory argument is provided
if [ -z "$1" ]; then
    echo "ERROR: Graphite directory is required"
    echo ""
    echo "Usage: $0 GRAPHITE_DIR"
    echo ""
    echo "Migrates historical Graphite Whisper data to VictoriaMetrics."
    echo ""
    echo "Arguments:"
    echo "  GRAPHITE_DIR    Path to the Graphite data directory (contains 'whisper' subdirectory and 'graphite.db')"
    echo ""
    echo "Example:"
    echo "  $0 ~/code/deltafi/data/graphite"
    echo ""
    echo "Environment Detection:"
    echo "  The script automatically detects whether VictoriaMetrics is running in Docker Compose or Kubernetes"
    exit 1
fi

GRAPHITE_DIR="$1/whisper"
GRAPHITE_DB="$1/graphite.db"
VM_PORT=2003
BATCH_SIZE=1000

if [ ! -d "$GRAPHITE_DIR" ]; then
    echo "ERROR: Graphite whisper directory not found: $GRAPHITE_DIR"
    echo "Please ensure GRAPHITE_DIR contains a 'whisper' subdirectory"
    exit 1
fi

if [ ! -f "$GRAPHITE_DB" ]; then
    echo "ERROR: Graphite database not found: $GRAPHITE_DB"
    echo "Please ensure GRAPHITE_DIR contains a 'graphite.db' file"
    exit 1
fi

# Detect environment and set up connection method
if docker ps --format '{{.Names}}' 2>/dev/null | grep -q deltafi-nodemonitor; then
    ENV_TYPE="compose"
    SENDER_CONTAINER="deltafi-nodemonitor"
    VM_HOST="deltafi-victoriametrics"
    echo "=== Graphite to VictoriaMetrics Migration (Docker Compose) ==="
elif kubectl get pods -n deltafi 2>/dev/null | grep -q nodemonitor; then
    ENV_TYPE="k8s"
    SENDER_POD=$(kubectl get pods -n deltafi -l app=deltafi-nodemonitor -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    if [ -z "$SENDER_POD" ]; then
        echo "ERROR: Could not find nodemonitor pod in Kubernetes"
        exit 1
    fi
    VM_HOST="deltafi-victoriametrics"
    echo "=== Graphite to VictoriaMetrics Migration (Kubernetes) ==="
else
    echo "ERROR: Could not detect nodemonitor running in Docker Compose or Kubernetes"
    echo "Please ensure the DeltaFi stack is running before running this migration"
    exit 1
fi

echo "Graphite data: $GRAPHITE_DIR"
echo "Environment: $ENV_TYPE"
echo ""

# Use bundled whisper-dump.py
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WHISPER_DUMP="$SCRIPT_DIR/whisper-dump.py"
CONFIG_FILE="$SCRIPT_DIR/migrate-graphite-to-victoriametrics.conf"

if [ ! -f "$WHISPER_DUMP" ]; then
    echo "ERROR: whisper-dump.py not found at $WHISPER_DUMP"
    echo "The migration script requires whisper-dump.py to be bundled in the bin directory"
    exit 1
fi

# Set PYTHONPATH so whisper-dump.py can find whisper.py in the same directory
export PYTHONPATH="$SCRIPT_DIR:$PYTHONPATH"

# Load exclusion patterns from config file
EXCLUSION_WHERE=""
if [ -f "$CONFIG_FILE" ]; then
    echo "Loading exclusion patterns from $CONFIG_FILE"
    while IFS= read -r line || [ -n "$line" ]; do
        # Skip empty lines and comments
        [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
        # Add to WHERE clause
        if [ -z "$EXCLUSION_WHERE" ]; then
            EXCLUSION_WHERE="path NOT LIKE '$line'"
        else
            EXCLUSION_WHERE="$EXCLUSION_WHERE AND path NOT LIKE '$line'"
        fi
    done < "$CONFIG_FILE"

    if [ -n "$EXCLUSION_WHERE" ]; then
        echo "Exclusion patterns loaded"
    fi
else
    echo "WARNING: Config file not found at $CONFIG_FILE"
    echo "Using default exclusions only"
    EXCLUSION_WHERE="path NOT LIKE 'carbon.%' AND path NOT LIKE 'statsd.%' AND path NOT LIKE 'stats_counts.statsd.%'"
fi

# Count metrics from database (excluding patterns from config)
total_tagged=$(sqlite3 "$GRAPHITE_DB" "SELECT COUNT(*) FROM tags_series WHERE $EXCLUSION_WHERE")
total_simple=$(find "$GRAPHITE_DIR" -name "*.wsp" \
    ! -path "*/_tagged/*" \
    ! -path "*/carbon/*" \
    ! -path "$GRAPHITE_DIR/statsd/*" \
    ! -path "$GRAPHITE_DIR/stats_counts/statsd/*" | wc -l | tr -d ' ')
total_files=$((total_tagged + total_simple))

echo "Found $total_files Whisper files to migrate ($total_tagged tagged + $total_simple simple)"
echo ""

current=0
batch=""
batch_count=0

# Function to send batch to VictoriaMetrics
send_batch() {
    if [ -n "$batch" ]; then
        if [ "$ENV_TYPE" = "compose" ]; then
            # Use nodemonitor's nc with -q 0 to close after EOF
            echo "$batch" | docker exec -i "$SENDER_CONTAINER" nc -q 0 "$VM_HOST" "$VM_PORT" 2>/dev/null
        elif [ "$ENV_TYPE" = "k8s" ]; then
            # Use nodemonitor pod's nc with -q 0 to close after EOF
            echo "$batch" | kubectl exec -i -n deltafi "$SENDER_POD" -- nc -q 0 "$VM_HOST" "$VM_PORT" 2>/dev/null
        fi
        batch=""
        batch_count=0
    fi
}

# Process tagged metrics from database
sqlite3 "$GRAPHITE_DB" "SELECT hash, path FROM tags_series WHERE $EXCLUSION_WHERE" | while IFS='|' read -r hash metric_path; do
    current=$((current + 1))

    # Find the .wsp file: _tagged/ABC/DEF/ABCDEF....wsp
    prefix1="${hash:0:3}"
    prefix2="${hash:3:3}"
    wsp_file="$GRAPHITE_DIR/_tagged/$prefix1/$prefix2/$hash.wsp"

    if [ ! -f "$wsp_file" ]; then
        echo "[$current/$total_files] SKIP: File not found for $metric_path"
        continue
    fi

    echo "[$current/$total_files] Migrating $metric_path..."

    # Extract data from whisper file and add to batch
    while IFS=: read -r timestamp value; do
        if [[ "$timestamp" =~ ^[0-9]+$ ]] && [ "$value" != "None" ]; then
            # VictoriaMetrics accepts: metric.name;tag1=value1;tag2=value2 value timestamp
            line="$metric_path $value $timestamp"
            batch="$batch$line"$'\n'
            batch_count=$((batch_count + 1))

            if [ $batch_count -ge $BATCH_SIZE ]; then
                send_batch
            fi
        fi
    done < <("$WHISPER_DUMP" -r "$wsp_file")
done

# Send any remaining batch
send_batch

# Process simple (non-tagged) metrics
find "$GRAPHITE_DIR" -name "*.wsp" \
    ! -path "*/_tagged/*" \
    ! -path "*/carbon/*" \
    ! -path "$GRAPHITE_DIR/statsd/*" \
    ! -path "$GRAPHITE_DIR/stats_counts/statsd/*" | while read -r wsp_file; do
    current=$((current + total_tagged + 1))

    # Convert file path to metric name
    metric_path="${wsp_file#$GRAPHITE_DIR/}"
    metric_name="${metric_path%.wsp}"
    metric_name=$(echo "$metric_name" | tr '/' '.')

    echo "[$current/$total_files] Migrating $metric_name..."

    while IFS=: read -r timestamp value; do
        if [[ "$timestamp" =~ ^[0-9]+$ ]] && [ "$value" != "None" ]; then
            line="$metric_name $value $timestamp"
            batch="$batch$line"$'\n'
            batch_count=$((batch_count + 1))

            if [ $batch_count -ge $BATCH_SIZE ]; then
                send_batch
            fi
        fi
    done < <("$WHISPER_DUMP" -r "$wsp_file")
done

# Send final batch
send_batch

echo ""
echo "=== Migration Complete ==="
echo "All metrics have been migrated to VictoriaMetrics"
