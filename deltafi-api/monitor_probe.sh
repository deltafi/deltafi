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

if [[ -z $PROBE_DIR ]]; then
  echo "\$PROBE_DIR must be set"
  exit 1
fi
mkdir -p $PROBE_DIR
STALE_FILES="$(find $PROBE_DIR -type f -mmin +1)"
if [[ "$STALE_FILES" != "" ]]; then
  echo "Stale probe files:"
  echo $STALE_FILES
  exit 1
else
  exit 0
fi