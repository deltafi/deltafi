#!/bin/sh
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
    local log_level=$1
    local message=$2
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
    _warn "Terminating the nodemonitor."
    exit
}

trap exterminate SIGTERM

# Post usage and limit of /data to graphite roughly every $PERIOD seconds
while true; do
    LIMIT=`df /data -P -B 1 | grep /data | xargs echo | cut -d' ' -f2`
    USAGE=`df /data -P -B 1 | grep /data | xargs echo | cut -d' ' -f3`
    TIMESTAMP=`date +%s`
    echo "gauge.node.disk.usage;hostname=$NODE_NAME $USAGE $TIMESTAMP" | nc -N $GRAPHITE_HOST $GRAPHITE_PORT
    echo "gauge.node.disk.limit;hostname=$NODE_NAME $LIMIT $TIMESTAMP" | nc -N $GRAPHITE_HOST $GRAPHITE_PORT

    _debug "$NODE_NAME: Using $USAGE of $LIMIT"
    sleep $PERIOD
done
