#!/bin/sh
#
#    DeltaFi - Data transformation and enrichment platform
#
#    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

echo "Volume metrics running"

HOSTNAME=`hostname`
GRAPHITE_HOST=${GRAPHITE_HOST:-deltafi-graphite}
GRAPHITE_PORT=${GRAPHITE_PORT:-2003}
PERIOD=${PERIOD:-9}

# Post usage and limit of /data to graphite roughly every $PERIOD seconds
while true; do
    LIMIT=`df /data -B 1 | grep /data | xargs echo | cut -d' ' -f2`
    USAGE=`df /data -B 1 | grep /data | xargs echo | cut -d' ' -f3`
    TIMESTAMP=`date +%s`
    echo "gauge.node.disk.usage;hostname=$HOSTNAME $USAGE $TIMESTAMP" | nc -N $GRAPHITE_HOST $GRAPHITE_PORT
    echo "gauge.node.disk.limit;hostname=$HOSTNAME $LIMIT $TIMESTAMP" | nc -N $GRAPHITE_HOST $GRAPHITE_PORT

    echo "$HOSTNAME $TIMESTAMP $USAGE of $LIMIT"
    sleep $PERIOD
done
