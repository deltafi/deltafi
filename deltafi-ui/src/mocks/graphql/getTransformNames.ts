/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

const generateData = () => {
  return {
    transform: [
      "hello-world-python-transform",
      "stix-elevator-transform",
      "devFlow1",
      "gen-data-route-or-filter",
      "gen-data-route-or-error",
      "hello-world-python-data-route-or-filter",
      "hello-world-java-transform-many",
      "hello-world-java-transform",
      "hello-world-java-data-route-or-filter",
      "xml-subscriber",
      "hello-world-python-transform-many",
      "passthrough-transform",
      "hello-world-python-join",
      "jolt",
      "stix-2_1-validate",
      "decompress-and-join-transform",
      "devFlow4",
      "malware-threat-transform",
      "hello-world-java-join",
      "decompress",
      "gen-data-route-or-detect",
      "devFlow2",
      "smoke-test-transform",
      "json-subscriber",
      "devFlow3",
      "gen-data-route-all-matching"
    ]
  };
};

export default {
  getFlowNames: generateData(),
};
