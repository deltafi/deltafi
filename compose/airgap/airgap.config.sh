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

# This is the configuration script run against the DeltaFi before the snapshot is taken
#

set -e

deltafi egress-flow start passthrough
deltafi egress-flow start smoke
deltafi normalize-flow start passthrough
deltafi normalize-flow start decompress-passthrough
deltafi normalize-flow start smoke
deltafi enrich-flow start artificial-enrichment
deltafi load-policies airgap.delete-policy.json

deltafi ingress-action start smoke-test-ingress

echo "Configuring plugin variables"
# shellcheck disable=SC2016
deltafi mongo-eval --quiet 'db.pluginVariable.updateOne({"variables.name": "passthroughEgressUrl"}, {$set: {"variables.$.value": "http://deltafi-egress-sink:9292"}})' > /dev/null
# shellcheck disable=SC2016
deltafi mongo-eval --quiet 'db.pluginVariable.updateOne({"variables.name": "smokeEgressUrl"}, {$set: {"variables.$.value": "http://deltafi-egress-sink:9292/blackhole"}})' > /dev/null

