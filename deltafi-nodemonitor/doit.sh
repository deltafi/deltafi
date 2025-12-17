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

GRAPHITE_HOST=${GRAPHITE_HOST:-deltafi-victoriametrics}
GRAPHITE_PORT=${GRAPHITE_PORT:-2003}
VALKEY_HOST=${VALKEY_HOST:-deltafi-valkey-master}
VALKEY_PORT=${VALKEY_PORT:-6379}
PERIOD=${PERIOD:-5}

_log() {
    local log_level="$1"
    local message="$2"
    timestamp=$(date "+%Y-%m-%dT%H:%M:%SZ")
    echo "{\"timestamp\":\"$timestamp\",\"level\":\"$log_level\",\"$message\"}"
}

_debug() {
    [[ -z $DEBUG ]] || _log "DEBUG" "$1"
}

_warn() {
    _log "WARN" "$1"
}

_info() {
    _log "INFO" "$1"
}

_info "Starting up on ${NODE_NAME}"
_info "Graphite: ${GRAPHITE_HOST}:${GRAPHITE_PORT}"
_info "Valkey: ${VALKEY_HOST}:${VALKEY_PORT}"
_info "Reporting period: ${PERIOD} seconds"

if [ -S /var/run/docker.sock ]; then
    _info "Docker detected.  Container stats enabled."
fi

exterminate() {
    _warn "Terminating the nodemonitor."
    exit
}

trap exterminate SIGTERM

send_to_valkey() {
    local metric_name="$1"
    local hostname="$2"
    local metric_value="$3"
    local timestamp="$4"

    # Optional 5th param to store partition name
    if [[ -n "$5" ]]; then
        local partition="$5"
        echo -e "AUTH ${VALKEY_PASSWORD}\nHSET ${metric_name} ${hostname} \"[${metric_value}, ${timestamp}, \\\"${partition}\\\"]\"" | nc -N "${VALKEY_HOST}" "${VALKEY_PORT}" >/dev/null
    else
        echo -e "AUTH ${VALKEY_PASSWORD}\nHSET ${metric_name} ${hostname} \"[${metric_value}, ${timestamp}]\"" | nc -N "${VALKEY_HOST}" "${VALKEY_PORT}" >/dev/null
    fi
}

# CPU metrics

PREV_CPU_TOTAL=0
PREV_CPU_IDLE=0
PREV_CPU_IOWAIT=0

report_cpu_metrics() {
    TOTAL_CPU_UNITS=$(($(nproc)*1000))

    # Get the total CPU statistics, discarding the 'cpu ' prefix.
    CPU=($(sed -n 's/^cpu\s//p' /proc/stat))
    IDLE=${CPU[3]} # Just the idle CPU time.
    IOWAIT=${CPU[4]}

    # Calculate the total CPU time.
    TOTAL=0
    for VALUE in "${CPU[@]:0:8}"; do
      TOTAL=$((TOTAL+VALUE))
    done

    # Calculate the CPU usage since we last checked.
    DIFF_IDLE=$((IDLE-PREV_CPU_IDLE))
    DIFF_IOWAIT=$((IOWAIT-PREV_CPU_IOWAIT))
    DIFF_TOTAL=$((TOTAL-PREV_CPU_TOTAL))
    DIFF_USAGE_PERCENT=$((100000*(DIFF_TOTAL-DIFF_IDLE)/DIFF_TOTAL))
    DIFF_USAGE_UNITS=$((DIFF_USAGE_PERCENT*TOTAL_CPU_UNITS/100000))
    DIFF_IOWAIT_PERCENT=$((100000*(DIFF_IOWAIT)/DIFF_TOTAL))
    DIFF_IOWAIT_UNITS=$((DIFF_IOWAIT_PERCENT*TOTAL_CPU_UNITS/100000))
    # Remember the total and idle CPU times for the next check.
    PREV_CPU_TOTAL="$TOTAL"
    PREV_CPU_IDLE="$IDLE"
    PREV_CPU_IOWAIT="$IOWAIT"

    TIMESTAMP=$(date +%s)
    metrics+="gauge.node.cpu.usage;hostname=$NODE_NAME $DIFF_USAGE_UNITS $TIMESTAMP\n"
    metrics+="gauge.node.cpu.iowait;hostname=$NODE_NAME $DIFF_IOWAIT_UNITS $TIMESTAMP\n"
    metrics+="gauge.node.cpu.limit;hostname=$NODE_NAME $TOTAL_CPU_UNITS $TIMESTAMP\n"

    send_to_valkey "gauge.node.cpu.usage" "$NODE_NAME" "$DIFF_USAGE_UNITS" "$TIMESTAMP"
    send_to_valkey "gauge.node.cpu.limit" "$NODE_NAME" "$TOTAL_CPU_UNITS" "$TIMESTAMP"

    _debug "$NODE_NAME: Using ${DIFF_USAGE_UNITS} of ${TOTAL_CPU_UNITS} CPU units"
}

report_disk_metrics() {
    local base_usage base_limit base_part
    if [[ -d /data/deltafi ]]; then
        base_limit=$(df /data/deltafi -P -B 1 | tail -1 | awk '{print $2}')
        base_usage=$(df /data/deltafi -P -B 1 | tail -1 | awk '{print $3}')
        base_part=$(df /data/deltafi -P -B 1 | tail -1 | awk '{print $1}')
    fi

    local token_file="/var/run/secrets/kubernetes.io/serviceaccount/token"
    local ca_file="/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"
    local has_minio= has_postgres=

    if [[ ! -f "$token_file" || ! -f "$ca_file" ]]; then
        _debug "No Kubernetes token/CA found, assuming node has minio and postgres."
        has_minio=true
        has_postgres=true
    else
        local token pods_json
        token=$(cat "$token_file")
        pods_json=$(curl -sS --cacert "$ca_file" -H "Authorization: Bearer $token" \
          "https://${KUBERNETES_SERVICE_HOST}:${KUBERNETES_SERVICE_PORT}/api/v1/pods?fieldSelector=spec.nodeName=${NODE_NAME}")

        if echo "$pods_json" | grep -q '"name":.*deltafi-s3proxy' && [[ -d /data/deltafi/minio ]]; then
            has_minio=true
        fi
        if echo "$pods_json" | grep -q '"name":.*deltafi-postgres' && [[ -d /data/deltafi/postgres ]]; then
            has_postgres=true
        fi
    fi

    local TIMESTAMP
    TIMESTAMP=$(date +%s)

    # Lookup minio values and send valkey metrics immediately.
    if [[ $has_minio ]]; then
        minio_limit=$(df /data/deltafi/minio -P -B 1 | tail -1 | awk '{print $2}')
        minio_usage=$(df /data/deltafi/minio -P -B 1 | tail -1 | awk '{print $3}')
        minio_part=$(df /data/deltafi/minio -P -B 1 | tail -1 | awk '{print $1}')
        send_to_valkey "gauge.node.disk-minio.usage" "$NODE_NAME" "$minio_usage" "$TIMESTAMP" "$minio_part"
        send_to_valkey "gauge.node.disk-minio.limit" "$NODE_NAME" "$minio_limit" "$TIMESTAMP" "$minio_part"
    fi

    # Lookup postgres values and send valkey metrics immediately.
    if [[ $has_postgres ]]; then
        pg_limit=$(df /data/deltafi/postgres -P -B 1 | tail -1 | awk '{print $2}')
        pg_usage=$(df /data/deltafi/postgres -P -B 1 | tail -1 | awk '{print $3}')
        pg_part=$(df /data/deltafi/postgres -P -B 1 | tail -1 | awk '{print $1}')
        send_to_valkey "gauge.node.disk-postgres.usage" "$NODE_NAME" "$pg_usage" "$TIMESTAMP" "$pg_part"
        send_to_valkey "gauge.node.disk-postgres.limit" "$NODE_NAME" "$pg_limit" "$TIMESTAMP" "$pg_part"
    fi

    local used_parts=""
    if [[ $has_minio && $has_postgres ]]; then
        if [[ "$minio_part" == "$pg_part" ]]; then
            metrics+="gauge.node.disk.usage;service=minio+postgres;hostname=$NODE_NAME $minio_usage $TIMESTAMP\n"
            metrics+="gauge.node.disk.limit;service=minio+postgres;hostname=$NODE_NAME $minio_limit $TIMESTAMP\n"
            used_parts="$minio_part"
        else
            metrics+="gauge.node.disk.usage;service=minio;hostname=$NODE_NAME $minio_usage $TIMESTAMP\n"
            metrics+="gauge.node.disk.limit;service=minio;hostname=$NODE_NAME $minio_limit $TIMESTAMP\n"
            metrics+="gauge.node.disk.usage;service=postgres;hostname=$NODE_NAME $pg_usage $TIMESTAMP\n"
            metrics+="gauge.node.disk.limit;service=postgres;hostname=$NODE_NAME $pg_limit $TIMESTAMP\n"
            used_parts="$minio_part,$pg_part"
        fi
    elif [[ $has_minio ]]; then
        metrics+="gauge.node.disk.usage;service=minio;hostname=$NODE_NAME $minio_usage $TIMESTAMP\n"
        metrics+="gauge.node.disk.limit;service=minio;hostname=$NODE_NAME $minio_limit $TIMESTAMP\n"
        used_parts="$minio_part"
    elif [[ $has_postgres ]]; then
        metrics+="gauge.node.disk.usage;service=postgres;hostname=$NODE_NAME $pg_usage $TIMESTAMP\n"
        metrics+="gauge.node.disk.limit;service=postgres;hostname=$NODE_NAME $pg_limit $TIMESTAMP\n"
        used_parts="$pg_part"
    fi

    local produce_other_metric=true
    if [[ -n "$used_parts" ]]; then
        IFS=',' read -ra parts <<< "$used_parts"
        for p in "${parts[@]}"; do
            if [[ "$p" == "$base_part" ]]; then
                produce_other_metric=false
                break
            fi
        done
    fi

    if $produce_other_metric; then
        metrics+="gauge.node.disk.usage;service=other;hostname=$NODE_NAME $base_usage $TIMESTAMP\n"
        metrics+="gauge.node.disk.limit;service=other;hostname=$NODE_NAME $base_limit $TIMESTAMP\n"
    fi
}

report_memory_metrics() {
    TIMESTAMP=$(date +%s)
    TOTAL=$(grep ^MemTotal: < /proc/meminfo | awk '{print $2}')
    AVAILABLE=$(grep ^MemAvailable: < /proc/meminfo | awk '{print $2}')
    LIMIT=$((TOTAL * 1000))
    USAGE=$(((TOTAL - AVAILABLE) * 1000))
    TIMESTAMP=$(date +%s)
    metrics+="gauge.node.memory.usage;hostname=$NODE_NAME $USAGE $TIMESTAMP\n"
    metrics+="gauge.node.memory.limit;hostname=$NODE_NAME $LIMIT $TIMESTAMP\n"

    send_to_valkey "gauge.node.memory.usage" "$NODE_NAME" "$USAGE" "$TIMESTAMP"
    send_to_valkey "gauge.node.memory.limit" "$NODE_NAME" "$LIMIT" "$TIMESTAMP"

    _debug "$NODE_NAME: Using $USAGE of $LIMIT bytes of Memory"
}

convert_to_mbytes() {
    local value
    value=$(echo "$1" | sed -E 's/([KMG]i?B)//')  # Extract numeric value
    local unit
    unit=$(echo "$1" | sed -E 's/[0-9.]+//g')  # Extract the unit (e.g., KiB, MiB, GiB)

    case $unit in
        "KiB")
            result=$(echo "$value * 1024 / 1000000" | bc)  # 1 KiB = 1024 bytes
            ;;
        "MiB")
            result=$(echo "$value * 1024 * 1024 / 1000000" | bc)  # 1 MiB = 1024 * 1024 bytes
            ;;
        "GiB")
            result=$(echo "$value * 1024 * 1024 * 1024 / 1000000" | bc)  # 1 GiB = 1024 * 1024 * 1024 bytes
            ;;
        *)
            result=$(echo "$value" | grep -o '^[0-9]*')
            ;;
    esac
    printf "%.0f\n" "$result"
}

report_container_metrics() {
    if [ -S /var/run/docker.sock ]; then

        TIMESTAMP=$(date +%s)

        stats_output=$(docker stats --no-stream $(docker ps --format '{{.Names}}' --filter 'label=deltafi-group') --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}')

        if [ -z "$stats_output" ]; then
            _warn "No containers for the 'deltafi' project"
        else
          metric_list=$(echo "$stats_output" | while read -r line; do
            read -r name cpu_perc mem_usage <<< "$line"

            cpu=$(echo "$cpu_perc" | sed 's/%//' | awk '{printf "%.0f", ($1 *100)/10}')
            mem_value=$(echo "$mem_usage" | awk '{ print $1 }')
            mem_bytes=$(convert_to_mbytes "$mem_value")
            echo -n "gauge.app.memory;app=${name};namespace=deltafi;node=${NODE_NAME%.local} ${mem_bytes} ${TIMESTAMP}\n"
            echo -n "gauge.app.cpu;app=${name};namespace=deltafi;node=${NODE_NAME%.local} ${cpu} ${TIMESTAMP}\n"
        done)
        metrics+=$metric_list
        fi
    fi
}

# Report system metrics to graphite roughly every $PERIOD seconds
while true; do

    metrics=""
    report_cpu_metrics
    report_disk_metrics
    report_memory_metrics
    report_container_metrics

    printf "%b" "${metrics}" | nc -N "$GRAPHITE_HOST" "$GRAPHITE_PORT"

    sleep "$PERIOD"
done
