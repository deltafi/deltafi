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

const generateFlows = () => {
  return {
    dataSink: [
      {
        name: "blackhole-data-sink",
        description: "POST to nowhere",
        sourcePlugin: {
          groupId: "org.deltafi.testjig",
          artifactId: "deltafi-testjig",
          version: "2.30.2-SNAPSHOT"
        },
        flowStatus: {
          state: "RUNNING",
          valid: true,
          testMode: false,
          errors: []
        },
        expectedAnnotations: null,
        subscribe: [
          {
            condition: null,
            topic: "blackhole"
          }
        ],
        egressAction: {
          name: "BlackHoleEgressAction",
          apiVersion: null,
          actionType: "EGRESS",
          type: "org.deltafi.core.action.egress.HttpEgress",
          parameters: {
            url: "http://deltafi-egress-sink-service/blackhole",
            method: "POST",
            retryCount: "3",
            metadataKey: "deltafiMetadata",
            extraHeaders: {},
            retryDelayMs: "150"
          }
        },
        variables: []
      },
      {
        name: "smoke-test-data-sink",
        description: "Test flow that passes data through unchanged",
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "2.30.2-SNAPSHOT"
        },
        flowStatus: {
          state: "RUNNING",
          valid: true,
          testMode: false,
          errors: []
        },
        expectedAnnotations: null,
        subscribe: [
          {
            condition: null,
            topic: "smoke-egress"
          }
        ],
        egressAction: {
          name: "SmokeEgressAction",
          apiVersion: null,
          actionType: "EGRESS",
          type: "org.deltafi.core.action.egress.HttpEgress",
          parameters: {
            url: "http://deltafi-egress-sink-service/blackhole",
            method: "POST",
            retryCount: "3",
            metadataKey: "deltafiMetadata",
            extraHeaders: {},
            retryDelayMs: "150"
          }
        },
        variables: []
      },
      {
        name: "sample-error",
        description: "Test flow that passes data through unchanged",
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        flowStatus: {
          state: "RUNNING",
          valid: true,
          testMode: false,
          errors: []
        },
        expectedAnnotations: null,
        subscribe: [
          {
            condition: null,
            topic: "passthrough-egress"
          }
        ],
        egressAction: {
          name: "PassthroughEgress",
          apiVersion: null,
          actionType: "EGRESS",
          type: "org.deltafi.core.action.egress.HttpEgress",
          parameters: {
            url: "http://deltafi-egress-sink-service",
            method: "POST",
            metadataKey: "deltafiMetadata",
            extraHeaders: {
              XMyHeader: "test"
            }
          }
        },
        variables: []
      },
      {
        name: "test-by-dan",
        description: "testing by dan",
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        flowStatus: {
          state: "STOPPED",
          valid: true,
          testMode: false,
          errors: []
        },
        expectedAnnotations: null,
        subscribe: [
          {
            condition: "!content.?[name == 'required.name'].isEmpty()",
            topic: "blackhole"
          },
          {
            condition: "hasMediaType('application/json')",
            topic: "hello-world-java-egress"
          }
        ],
        egressAction: {
          name: "HttpEgress",
          apiVersion: null,
          actionType: "EGRESS",
          type: "org.deltafi.core.action.egress.HttpEgress",
          parameters: {
            url: "http://localhost/nonexistent/path",
            method: "POST",
            retryCount: "3",
            metadataKey: "key",
            extraHeaders: {},
            retryDelayMs: "150"
          }
        },
        variables: []
      },
      {
        name: "copy-smoke",
        description: "smoke copy",
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        flowStatus: {
          state: "STOPPED",
          valid: true,
          testMode: false,
          errors: []
        },
        expectedAnnotations: null,
        subscribe: [
          {
            condition: null,
            topic: "smoke-egress"
          }
        ],
        egressAction: {
          name: "SmokeEgressAction",
          apiVersion: null,
          actionType: "EGRESS",
          type: "org.deltafi.core.action.egress.RestPostEgress",
          parameters: {
            url: "http://deltafi-egress-sink-service/blackhole",
            metadataKey: "deltafiMetadata"
          }
        },
        variables: []
      },
      {
        name: "stix-2_1-data-sink",
        description: "Egress validated STIX 2.1",
        sourcePlugin: {
          groupId: "org.deltafi.stix",
          artifactId: "deltafi-stix",
          version: "2.30.2.dev1+gf8d5ce01b"
        },
        flowStatus: {
          state: "RUNNING",
          valid: true,
          testMode: false,
          errors: []
        },
        expectedAnnotations: null,
        subscribe: [
          {
            condition: null,
            topic: "stix-2_1-validated"
          }
        ],
        egressAction: {
          name: "Stix2_1Egress",
          apiVersion: null,
          actionType: "EGRESS",
          type: "org.deltafi.core.action.egress.RestPostEgress",
          parameters: {
            url: "http://deltafi-egress-sink-service/",
            retryCount: "3",
            metadataKey: "deltafiMetadata",
            retryDelayMs: "150"
          }
        },
        variables: []
      },
      {
        name: "Test",
        description: "Do not delete",
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "system-plugin",
          version: "2.30.2-SNAPSHOT"
        },
        flowStatus: {
          state: "RUNNING",
          valid: true,
          testMode: false,
          errors: []
        },
        expectedAnnotations: null,
        subscribe: [
          {
            condition: null,
            topic: "devFlow1"
          }
        ],
        egressAction: {
          name: "BlackHoleEgressAction",
          apiVersion: null,
          actionType: "EGRESS",
          type: "org.deltafi.core.action.egress.HttpEgress",
          parameters: {
            url: "http://deltafi-egress-sink-service/blackhole",
            method: "POST",
            retryCount: "3",
            metadataKey: "deltafiMetadata",
            extraHeaders: {},
            retryDelayMs: "150"
          }
        },
        variables: []
      },
      {
        name: "passthrough-data-sink",
        description: "Test flow that passes data through unchanged",
        sourcePlugin: {
          groupId: "org.deltafi",
          artifactId: "deltafi-core-actions",
          version: "2.30.2-SNAPSHOT"
        },
        flowStatus: {
          state: "RUNNING",
          valid: true,
          testMode: true,
          errors: []
        },
        expectedAnnotations: null,
        subscribe: [
          {
            condition: null,
            topic: "passthrough-egress"
          }
        ],
        egressAction: {
          name: "PassthroughEgress",
          apiVersion: null,
          actionType: "EGRESS",
          type: "org.deltafi.core.action.egress.HttpEgress",
          parameters: {
            url: "http://deltafi-egress-sink-service/blackhole",
            method: "POST",
            retryCount: "3",
            metadataKey: "deltafiMetadata",
            extraHeaders: {},
            retryDelayMs: "150"
          }
        },
        variables: []
      },
      {
        name: "hello-world-java-data-sink",
        description: "Hello World Java egress",
        sourcePlugin: {
          groupId: "org.deltafi.helloworld",
          artifactId: "deltafi-java-hello-world",
          version: "2.30.2-SNAPSHOT"
        },
        flowStatus: {
          state: "RUNNING",
          valid: true,
          testMode: false,
          errors: []
        },
        expectedAnnotations: null,
        subscribe: [
          {
            condition: null,
            topic: "hello-world-java-egress"
          }
        ],
        egressAction: {
          name: "HelloWorldEgressAction",
          apiVersion: null,
          actionType: "EGRESS",
          type: "org.deltafi.helloworld.actions.HelloWorldEgressAction",
          parameters: {}
        },
        variables: []
      },
      {
        name: "hello-world-python-data-sink",
        description: "Hello World Python egress",
        sourcePlugin: {
          groupId: "org.deltafi.python-hello-world",
          artifactId: "deltafi-python-hello-world",
          version: "2.30.2.dev3+g1e6812931"
        },
        flowStatus: {
          state: "RUNNING",
          valid: true,
          testMode: false,
          errors: []
        },
        expectedAnnotations: null,
        subscribe: [
          {
            condition: null,
            topic: "hello-world-python-egress"
          }
        ],
        egressAction: {
          name: "HelloWorldEgressAction",
          apiVersion: null,
          actionType: "EGRESS",
          type: "org.deltafi.python-hello-world.HelloWorldEgressAction",
          parameters: {}
        },
        variables: []
      }
    ]
  };
};

export default {
  getAllFlows: generateFlows(),
};
