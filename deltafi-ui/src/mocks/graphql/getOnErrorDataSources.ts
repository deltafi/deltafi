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
    onErrorDataSource: [
      {
        name: "mock-on-error-data-source",
        type: "ON_ERROR_DATA_SOURCE",
        description: "Receive test flow files on dev",
        maxErrors: -1,
        errorMessageRegex: ".*ERROR.*",
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi.testjig",
          artifactId: "deltafi-testjig",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: {
          annotations: {},
          metadataPatterns: [],
          discardPrefix: null
        },
        metadata: {},
        __typename: "OnErrorDataSource",
        topic: "blackhole"
      }
    ]
  };
};

export default () => {
  return {
    getAllFlows: generateData(),
  };
};