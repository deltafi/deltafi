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
_info "Reporting period: ${PERIOD} seconds"

exterminate() {
    _warn "Terminating the clustermonitor."
    exit
}

trap exterminate SIGTERM

kubectl_top() {
  kubectl top pod --containers=true | awk 'NR>1 {
      gsub(/m/,"",$3);
      gsub(/Mi/,"",$4);
      name=$2;
      cpu=$3;
      memory=$4;
      if (name in counts) {
          counts[name]++;
          name = name"-"counts[name];
      } else {
          counts[name] = 1;
      }
      print name "  " cpu "  " memory;
  }'
}

report_app_metrics() {
    TIMESTAMP=$(date +%s)
    while read -r name cpu memory; do
        metrics+="gauge.app.memory;app=$name $memory $TIMESTAMP\n"
        metrics+="gauge.app.cpu;app=$name $cpu $TIMESTAMP\n"
    done < <(kubectl_top)

    printf "%b" "$metrics" | nc -N "$GRAPHITE_HOST" "$GRAPHITE_PORT"
}

# Report system metrics to graphite roughly every $PERIOD seconds
while true; do
    report_app_metrics

    sleep "$PERIOD"
done
