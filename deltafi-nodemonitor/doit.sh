#!/bin/bash
#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

GRAPHITE_HOST=${GRAPHITE_HOST:-deltafi-graphite}
GRAPHITE_PORT=${GRAPHITE_PORT:-2003}
VALKEY_HOST=${DELTAFI_VALKEY_MASTER_SERVICE_HOST}
VALKEY_PORT=${DELTAFI_VALKEY_MASTER_SERVICE_PORT}
PERIOD=${PERIOD:-9}

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

    echo -e "AUTH ${VALKEY_PASSWORD}\nHSET ${metric_name} ${hostname} \"[${metric_value}, ${timestamp}]\"" | nc -N "${VALKEY_HOST}" "${VALKEY_PORT}" >/dev/null
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
    LIMIT=$(df /data -P -B 1 | grep /data | xargs echo | cut -d' ' -f2)
    USAGE=$(df /data -P -B 1 | grep /data | xargs echo | cut -d' ' -f3)
    TIMESTAMP=$(date +%s)
    metrics+="gauge.node.disk.usage;hostname=$NODE_NAME $USAGE $TIMESTAMP\n"
    metrics+="gauge.node.disk.limit;hostname=$NODE_NAME $LIMIT $TIMESTAMP\n"

    send_to_valkey "gauge.node.disk.usage" "$NODE_NAME" "$USAGE" "$TIMESTAMP"
    send_to_valkey "gauge.node.disk.limit" "$NODE_NAME" "$LIMIT" "$TIMESTAMP"

    _debug "$NODE_NAME: Using $USAGE of $LIMIT bytes on disk (/data)"
}

report_memory_metrics() {
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

# Report system metrics to graphite roughly every $PERIOD seconds
while true; do

    metrics=""
    report_cpu_metrics
    report_disk_metrics
    report_memory_metrics
    printf "%b" "${metrics}" | nc -N "$GRAPHITE_HOST" "$GRAPHITE_PORT"

    sleep "$PERIOD"
done
