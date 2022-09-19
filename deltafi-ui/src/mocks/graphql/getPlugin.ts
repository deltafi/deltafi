/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.Stix1_XFormatAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.Stix234_1FormatAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.StixLoadAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.StixTransformAction",
          requiresDomains: null,
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
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.Stix1_XFormatAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.Stix2_1FormatAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.StixLoadAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.StixTransformAction",
          requiresDomains: null,
        },
      ],
      variables: [
        {
          name: "test",
          value: true,
          description: "sample variable",
          defaultValue: "true",
          dataType: "BOOLEAN",
        },
        {
          name: "test 2",
          value: false,
          description: "sample variable 3",
          defaultValue: "false",
          dataType: "BOOLEAN",
        },
        {
          name: "test 4",
          value: "test",
          description: "sample variable 4",
          defaultValue: "true",
          dataType: "STRING",
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
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.Stix1_XFormatAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.Stix2_1FormatAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.StixLoadAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.StixTransformAction",
          requiresDomains: null,
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
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.Stix2_1FormatAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.StixLoadAction",
          requiresDomains: null,
        },
        {
          name: "org.deltafi.stix.actions.StixTransformAction",
          requiresDomains: null,
        },
      ],
      variables: [],
      propertySets: null,
    },
  ];

  return data;
};

export default {
  plugins: generateData(),
};
