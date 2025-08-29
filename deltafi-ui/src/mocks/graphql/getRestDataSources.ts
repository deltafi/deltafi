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
    restDataSource: [
      {
        name: "dev-flow-3-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Receive test flow files on dev",
        maxErrors: -1,
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
        __typename: "RestDataSource",
        topic: "devFlow3"
      },
      {
        name: "hello-world-java-join-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Used to upload test file for join topic",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi.helloworld",
          artifactId: "deltafi-java-hello-world",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: {
          annotations: {
            extraAnnot1: "valueW",
            extraAnnot2: "valueX"
          },
          metadataPatterns: [
            "metaKey.*"
          ],
          discardPrefix: "meta"
        },
        metadata: {
          metaKey3: "java",
          helloKey1: "restJoin1",
          helloKey2: "restJoin2"
        },
        __typename: "RestDataSource",
        topic: "hello-world-java-join"
      },
      {
        name: "smoke-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Create smoke DeltaFiles",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: {
          annotations: {},
          metadataPatterns: [],
          discardPrefix: null
        },
        metadata: {},
        __typename: "RestDataSource",
        topic: "smoke-transform"
      },
      {
        name: "dev-flow-4-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Receive test flow files on dev",
        maxErrors: -1,
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
        __typename: "RestDataSource",
        topic: "devFlow4"
      },
      {
        name: "detect-media-type-rest",
        type: "REST_DATA_SOURCE",
        description: "Detects media type by file name",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: null,
        metadata: null,
        __typename: "RestDataSource",
        topic: "detect-media-type"
      },
      {
        name: "hello-world-java-transform-many-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Used to upload test file for transform-many topic",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi.helloworld",
          artifactId: "deltafi-java-hello-world",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: {
          annotations: {},
          metadataPatterns: [],
          discardPrefix: null
        },
        metadata: {},
        __typename: "RestDataSource",
        topic: "hello-world-java-transform-many"
      },
      {
        name: "meta from fn rest",
        type: "REST_DATA_SOURCE",
        description: "Adds meta data by parsing the filename",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: {
          annotations: {},
          metadataPatterns: [],
          discardPrefix: null
        },
        metadata: {},
        __typename: "RestDataSource",
        topic: "modify-metadata"
      },
      {
        name: "asdf",
        type: "REST_DATA_SOURCE",
        description: "Receive test flow files on dev",
        maxErrors: -1,
        flowStatus: {
          state: "STOPPED",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: null,
        metadata: null,
        __typename: "RestDataSource",
        topic: "devFlow1"
      },
      {
        name: "stix-2_1-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Upload STIX 2.1 content",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi.stix",
          artifactId: "deltafi-stix",
          version: "2.30.2.dev1+gf8d5ce01b"
        },
        annotationConfig: {
          annotations: {},
          metadataPatterns: [],
          discardPrefix: null
        },
        metadata: {},
        __typename: "RestDataSource",
        topic: "stix-2_1"
      },
      {
        name: "decompress-join-source",
        type: "REST_DATA_SOURCE",
        description: "Create DeltaFiles that will pass through the system unchanged",
        maxErrors: -1,
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
        __typename: "RestDataSource",
        topic: "decompress-and-join"
      },
      {
        name: "decompress rest",
        type: "REST_DATA_SOURCE",
        description: "decompress",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: null,
        metadata: null,
        __typename: "RestDataSource",
        topic: "decompress-rest"
      },
      {
        name: "hello-world-python-transform-many-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Used to upload test file for transform-many topic",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi.python-hello-world",
          artifactId: "deltafi-python-hello-world",
          version: "2.30.2.dev3+g1e6812931"
        },
        annotationConfig: {
          annotations: {},
          metadataPatterns: [],
          discardPrefix: null
        },
        metadata: {},
        __typename: "RestDataSource",
        topic: "hello-world-python-transform-many"
      },
      {
        name: "malware-threat-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "AAaccepts a custoom CSV format with malware threats",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi.stix",
          artifactId: "deltafi-stix",
          version: "2.30.2.dev1+gf8d5ce01b"
        },
        annotationConfig: {
          annotations: {},
          metadataPatterns: [],
          discardPrefix: null
        },
        metadata: {},
        __typename: "RestDataSource",
        topic: "malware-threat-csv"
      },
      {
        name: "passthrough-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Create DeltaFiles that will pass through the system unchanged",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: {
          annotations: {},
          metadataPatterns: [],
          discardPrefix: null
        },
        metadata: {},
        __typename: "RestDataSource",
        topic: "passthrough"
      },
      {
        name: "dev-flow-1-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Receive test flow files on dev",
        maxErrors: -1,
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
        __typename: "RestDataSource",
        topic: "devFlow1"
      },
      {
        name: "me-be-clone",
        type: "REST_DATA_SOURCE",
        description: "Create DeltaFiles that will pass through the system unchanged",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        annotationConfig: null,
        metadata: null,
        __typename: "RestDataSource",
        topic: "decompress-and-join"
      },
      {
        name: "hello-world-python-join-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Used to upload test file for join topic",
        maxErrors: -1,
        flowStatus: {
          state: "RUNNING",
          valid: true,
          errors: [],
          testMode: false
        },
        sourcePlugin: {
          groupId: "org.deltafi.python-hello-world",
          artifactId: "deltafi-python-hello-world",
          version: "2.30.2.dev3+g1e6812931"
        },
        annotationConfig: {
          annotations: {
            extraAnnot1: "valueW",
            extraAnnot2: "valueX"
          },
          metadataPatterns: [
            "metaKey.*"
          ],
          discardPrefix: "meta"
        },
        metadata: {
          metaKey3: "python",
          helloKey1: "restJoin1",
          helloKey2: "restJoin2"
        },
        __typename: "RestDataSource",
        topic: "hello-world-python-join"
      },
      {
        name: "dev-flow-2-rest-data-source",
        type: "REST_DATA_SOURCE",
        description: "Receive test flow files on dev",
        maxErrors: -1,
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
        __typename: "RestDataSource",
        topic: "devFlow2"
      }
    ]
  };
};

export default () => {
  return {
    getAllFlows: generateData(),
  };
};