/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
    transform: [
      {
        sourcePlugin: {
          artifactId: "deltafi-testjig",
          groupId: "org.deltafi.testjig",
          version: "2.0-beta5"
        },
        name: "mockFlow4",
        subscribe: [
          {
            condition: null,
            topic: "mockFlow4"
          }
        ],
        publish: {
          matchingPolicy: "ALL_MATCHING",
          defaultRule: {
            defaultBehavior: "ERROR",
            topic: null
          },
          rules: [
            {
              condition: null,
              topic: "blackhole"
            }
          ]
        },
        description: "A mock flow for testing",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false
        },
        maxErrors: -1,
        transformActions: [
          {
            name: "SampleTransformAction",
            type: "org.deltafi.core.action.delay.Delay",
            parameters: null,
            apiVersion: null,
            collect: null
          }
        ],
        variables: []
      },
      {
        sourcePlugin: {
          artifactId: "deltafi-testjig",
          groupId: "org.deltafi.testjig",
          version: "2.0-beta5"
        },
        name: "mockFlow3",
        subscribe: [
          {
            condition: null,
            topic: "mockFlow3"
          }
        ],
        publish: {
          matchingPolicy: "ALL_MATCHING",
          defaultRule: {
            defaultBehavior: "ERROR",
            topic: null
          },
          rules: [
            {
              condition: null,
              topic: "blackhole"
            }
          ]
        },
        description: "A mock flow for testing",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false
        },
        maxErrors: -1,
        transformActions: [
          {
            name: "SampleTransformAction",
            type: "org.deltafi.core.action.delay.Delay",
            parameters: null,
            apiVersion: null,
            collect: null
          }
        ],
        variables: []
      },
      {
        sourcePlugin: {
          artifactId: "deltafi-testjig",
          groupId: "org.deltafi.testjig",
          version: "2.0-beta5"
        },
        name: "mockFlow2",
        subscribe: [
          {
            condition: null,
            topic: "mockFlow2"
          }
        ],
        publish: {
          matchingPolicy: "ALL_MATCHING",
          defaultRule: {
            defaultBehavior: "ERROR",
            topic: null
          },
          rules: [
            {
              condition: null,
              topic: "blackhole"
            }
          ]
        },
        description: "A mock flow for testing",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false
        },
        maxErrors: -1,
        transformActions: [
          {
            name: "SampleTransformAction",
            type: "org.deltafi.core.action.delay.Delay",
            parameters: null,
            apiVersion: null,
            collect: null
          }
        ],
        variables: []
      },
      {
        sourcePlugin: {
          artifactId: "deltafi-testjig",
          groupId: "org.deltafi.testjig",
          version: "2.0-beta5"
        },
        name: "mockFlow1",
        subscribe: [
          {
            condition: null,
            topic: "mockFlow1"
          }
        ],
        publish: {
          matchingPolicy: "ALL_MATCHING",
          defaultRule: {
            defaultBehavior: "ERROR",
            topic: null
          },
          rules: [
            {
              condition: null,
              topic: "blackhole"
            }
          ]
        },
        description: "A mock flow for testing",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false
        },
        maxErrors: -1,
        transformActions: [
          {
            name: "SampleTransformAction",
            type: "org.deltafi.core.action.delay.Delay",
            parameters: null,
            apiVersion: null,
            collect: null
          }
        ],
        variables: []
      },

    ],
    egress: [
      {
        sourcePlugin: {
          artifactId: "deltafi-core-actions",
          groupId: "org.deltafi",
          version: "2.0-beta6"
        },
        name: "passthrough-egress",
        subscribe: [
          {
            condition: null,
            topic: "passthrough-egress"
          }
        ],
        description: "Test flow that passes data through unchanged",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false
        },
        egressAction: {
          name: "PassthroughEgress",
          type: "org.deltafi.core.action.egress.RestPostEgress",
          parameters: {
            metadataKey: "deltafiMetadata",
            url: "http://deltafi-egress-sink-service"
          },
          apiVersion: null
        },
        variables: [],
        expectedAnnotations: null
      },
      {
        sourcePlugin: {
          artifactId: "deltafi-testjig",
          groupId: "org.deltafi.testjig",
          version: "2.0-beta5"
        },
        name: "blackhole-egress",
        subscribe: [
          {
            condition: null,
            topic: "blackhole"
          }
        ],
        description: "POST to nowhere",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false
        },
        egressAction: {
          name: "BlackHoleEgressAction",
          type: "org.deltafi.core.action.egress.RestPostEgress",
          parameters: {
            metadataKey: "deltafiMetadata",
            url: "http://deltafi-egress-sink-service/blackhole"
          },
          apiVersion: null
        },
        variables: [],
        expectedAnnotations: null
      },
      {
        sourcePlugin: {
          artifactId: "deltafi-stix",
          groupId: "org.deltafi.stix",
          version: "2.0b7.dev0+g4f25d95.d20240524"
        },
        name: "stix-2_1-egress",
        subscribe: [
          {
            condition: null,
            topic: "stix-2_1-validated"
          }
        ],
        description: "Egress validated STIX 2.1",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false
        },
        egressAction: {
          name: "Stix2_1Egress",
          type: "org.deltafi.core.action.egress.RestPostEgress",
          parameters: {
            metadataKey: "deltafiMetadata",
            url: "http://deltafi-egress-sink-service/"
          },
          apiVersion: null
        },
        variables: [],
        expectedAnnotations: null
      },
      {
        sourcePlugin: {
          artifactId: "deltafi-java-hello-world",
          groupId: "org.deltafi.helloworld",
          version: "2.0-beta5"
        },
        name: "hello-world-java-egress",
        subscribe: [
          {
            condition: null,
            topic: "hello-world-java-egress"
          }
        ],
        description: "Hello World Java egress",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false
        },
        egressAction: {
          name: "HelloWorldEgressAction",
          type: "org.deltafi.helloworld.actions.HelloWorldEgressAction",
          parameters: null,
          apiVersion: null
        },
        variables: [],
        expectedAnnotations: null
      },
      {
        sourcePlugin: {
          artifactId: "deltafi-core-actions",
          groupId: "org.deltafi",
          version: "2.0-beta6"
        },
        name: "smoke-test-egress",
        subscribe: [
          {
            condition: null,
            topic: "smoke-egress"
          }
        ],
        description: "Test flow that passes data through unchanged",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false
        },
        egressAction: {
          name: "SmokeEgressAction",
          type: "org.deltafi.core.action.egress.RestPostEgress",
          parameters: {
            metadataKey: "deltafiMetadata",
            url: "http://deltafi-egress-sink-service/blackhole"
          },
          apiVersion: null
        },
        variables: [],
        expectedAnnotations: null
      },
      {
        sourcePlugin: {
          artifactId: "deltafi-python-hello-world",
          groupId: "org.deltafi.python-hello-world",
          version: "2.0b7.dev0+gfa7ef50.d20240524"
        },
        name: "hello-world-python-egress",
        subscribe: [
          {
            condition: null,
            topic: "hello-world-python-egress"
          }
        ],
        description: "Hello World Python egress",
        flowStatus: {
          state: "RUNNING",
          errors: [],
          testMode: false
        },
        egressAction: {
          name: "HelloWorldEgressAction",
          type: "org.deltafi.python-hello-world.HelloWorldEgressAction",
          parameters: null,
          apiVersion: null
        },
        variables: [],
        expectedAnnotations: null
      }
    ],
  };
};

export default {
  getAllFlows: generateFlows(),
};
