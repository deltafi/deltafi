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
  const data = [
    {
      displayName: "Deltafi STIX 1",
      description: "Provides conversions to/from STIX 1.X and 2.1 formats",
      actionKitVersion: "flowplan-2",
      pluginCoordinates: {
        artifactId: "deltafi-stix",
        groupId: "org.deltafi.stix",
        version: "0.19.0-SNAPSHOT",
      },
      actions: [
        {
          name: "org.deltafi.goofy.RubberStampValidateAction",
        },
        {
          name: "org.deltafi.stix.actions.Stix1_XFormatAction",
        },
        {
          name: "org.deltafi.stix.actions.Stix234_1FormatAction",
        },
        {
          name: "org.deltafi.stix.actions.StixLoadAction",
        },
        {
          name: "org.deltafi.stix.actions.StixTransformAction",
        },
      ],
      variables: [
        {
          name: "test",
          value: null,
          description: "sample variable",
          defaultValue: "true",
          dataType: "BOOLEAN",
        },
      ],
      propertySets: null,
    },
    {
      displayName: "Deltafi STIX 2",
      description: "Provides conversions to and 2.1 formats",
      actionKitVersion: "flowplan-3",
      pluginCoordinates: {
        artifactId: "deltafi-stix2",
        groupId: "org.deltafi.stix",
        version: "0.19.0-SNAPSHOT2",
      },
      actions: [
        {
          name: "org.RubberStampValidateAction",
        },
        {
          name: "org.deltafi.stix.actions.Stix1_XFormatAction",
        },
        {
          name: "org.deltafi.stix.actions.Stix2_1FormatAction",
        },
        {
          name: "org.deltafi.stix.actions.StixLoadAction",
        },
        {
          name: "org.deltafi.stix.actions.StixTransformAction",
        },
      ],
      variables: [
        {
          name: "egressUrl",
          value: "http://deltafi-egress-sink-service",
          description: "The URL to post the DeltaFile to",
          defaultValue: "http://deltafi-egress-sink-service",
          dataType: "STRING",
        },
        {
          name: "annotations",
          value: "LOCID: US8772",
          description: "Metadata that will be indexed in the DeltaFile",
          defaultValue: null,
          dataType: "MAP",
        },
        {
          name: "sampleList",
          value: "http://localhost:8081/, http://localhost:8082/, http://localhost:8083/",
          description: "Noop sample list variable",
          defaultValue: null,
          dataType: "LIST",
        },
        {
          name: "sampleBoolean",
          value: "true",
          description: "Noop sample boolean variable",
          defaultValue: null,
          dataType: "BOOLEAN",
        },
        {
          name: "sampleNumber",
          value: "42",
          description: "Noop sample number variable",
          defaultValue: null,
          dataType: "NUMBER",
        },
      ],
      propertySets: null,
    },
    {
      displayName: "Deltafi STIX 5",
      description: "Provides conversions to and 2.1 formats",
      actionKitVersion: "flowplan-4",
      pluginCoordinates: {
        artifactId: "deltafi-stix2",
        groupId: "org.deltafi.stix",
        version: "0.20.1-SNAPSHOT2",
      },
      actions: [
        {
          name: "org.deltafi.stix.actions.RubberStampValidateAction",
        },
        {
          name: "org.deltafi.stix.actions.Stix1_XFormatAction",
        },
        {
          name: "org.deltafi.stix.actions.Stix2_1FormatAction",
        },
        {
          name: "org.deltafi.stix.actions.StixLoadAction",
        },
        {
          name: "org.deltafi.stix.actions.StixTransformAction",
        },
      ],
      variables: [
        {
          name: "egressUrl",
          value: "http://deltafi-egress-sink-service",
          description: "The URL to post the DeltaFile to",
          defaultValue: "http://deltafi-egress-sink-service",
          dataType: "STRING",
        },
        {
          name: "annotations",
          value: "LOCID: US8772",
          description: "Metadata that will be indexed in the DeltaFile",
          defaultValue: null,
          dataType: "MAP",
        },
        {
          name: "sampleList",
          value: "http://localhost:8081/, http://localhost:8082/, http://localhost:8083/",
          description: "Noop sample list variable",
          defaultValue: null,
          dataType: "LIST",
        },
        {
          name: "sampleBoolean",
          value: "true",
          description: "Noop sample boolean variable",
          defaultValue: null,
          dataType: "BOOLEAN",
        },
        {
          name: "sampleNumber",
          value: "42",
          description: "Noop sample number variable",
          defaultValue: null,
          dataType: "NUMBER",
        },
      ],
      propertySets: null,
    },
    {
      displayName: "Deltafi STIX",
      description: "Provides conversions to/from STIX 1.X and 2.1 formats",
      actionKitVersion: "flowplan-2",
      pluginCoordinates: {
        artifactId: "deltafi-stix",
        groupId: "org.deltafi.stix",
        version: "0.25.1-SNAPSHOT",
      },
      actions: [
        {
          name: "org.deltafi.stix.actions.RubberStampValidateAction",
        },
        {
          name: "org.deltafi.stix.actions.Stix2_1FormatAction",
        },
        {
          name: "org.deltafi.stix.actions.StixLoadAction",
        },
        {
          name: "org.deltafi.stix.actions.StixTransformAction",
        },
      ],
      variables: [
        {
          name: "egressUrl",
          value: "http://deltafi-egress-sink-service",
          description: "The URL to post the DeltaFile to",
          defaultValue: "http://deltafi-egress-sink-service",
          dataType: "STRING",
        },
        {
          name: "annotations",
          value: "LOCID: US8772",
          description: "Metadata that will be indexed in the DeltaFile",
          defaultValue: null,
          dataType: "MAP",
        },
        {
          name: "sampleList",
          value: "http://localhost:8081/, http://localhost:8082/, http://localhost:8083/",
          description: "Noop sample list variable",
          defaultValue: null,
          dataType: "LIST",
        },
        {
          name: "sampleBoolean",
          value: "true",
          description: "Noop sample boolean variable",
          defaultValue: null,
          dataType: "BOOLEAN",
        },
        {
          name: "sampleNumber",
          value: "42",
          description: "Noop sample number variable",
          defaultValue: null,
          dataType: "NUMBER",
        },
      ],
      propertySets: null,
    },
  ];

  return data;
};

export default {
  plugins: generateData(),
};
