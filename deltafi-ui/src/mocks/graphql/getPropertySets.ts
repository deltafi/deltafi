/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

// Property shape mirrors the backend properties-schema.graphqls Property type:
//   key, description, defaultValue, refreshable, value, propertySource (CUSTOM|DEFAULT),
//   dataType (STRING|BOOLEAN|NUMBER|LIST|SET|MAP), editable
const generateData = () => {
  const data = [
    {
      id: "mock-deltafi-common",
      displayName: "Mock Common Properties",
      description: "Mock properties used across all parts of the system.",
      properties: [
        {
          key: "mock.app.url",
          value: "http://mock_url",
          defaultValue: "http://mock_url",
          editable: true,
          refreshable: false,
          description: "URL to send spans to.",
          propertySource: "DEFAULT",
          dataType: "STRING",
        },
        {
          key: "mock.app.log.level",
          value: "INFO",
          defaultValue: "INFO",
          editable: true,
          refreshable: true,
          description: "The log level for app.",
          propertySource: "DEFAULT",
          dataType: "STRING",
        },
        {
          key: "mock.app.password",
          value: "${APP_PASSWORD}",
          defaultValue: "${APP_PASSWORD}",
          editable: true,
          refreshable: false,
          description: "The password used to access app",
          propertySource: "DEFAULT",
          dataType: "STRING",
        },
        {
          key: "mock.db.password",
          value: "${DB_PASSWORD}",
          defaultValue: "${DB_PASSWORD}",
          editable: true,
          refreshable: false,
          description: "Login password of the db server. Cannot be set with URI.",
          propertySource: "DEFAULT",
          dataType: "STRING",
        },
      ],
    },
    {
      id: "mock-action-kit",
      displayName: "Mock Action-Kit Properties",
      description: "Mock properties shared across all installed actions.",
      properties: [
        {
          key: "mock.actions.mock-action-registration-initial-delay-ms",
          value: "1000",
          defaultValue: "1000",
          editable: true,
          refreshable: false,
          description: "Mock initial delay in milliseconds before actions attempt to register.",
          propertySource: "DEFAULT",
          dataType: "NUMBER",
        },
        {
          key: "mock.actions.hostname",
          value: null,
          defaultValue: null,
          editable: true,
          refreshable: false,
          description: "Mock hostname of the server running the action.",
          propertySource: "DEFAULT",
          dataType: "STRING",
        },
      ],
    },
    {
      id: "mock-core",
      displayName: "Mock Core Properties",
      description: "Mock core properties exercising every data type and override state.",
      properties: [
        {
          // BOOLEAN at its default value -> toggle ON, no override gavel
          key: "mock.auto-resume.enabled",
          value: "true",
          defaultValue: "true",
          editable: true,
          refreshable: true,
          description: "Whether auto resume is enabled.",
          propertySource: "DEFAULT",
          dataType: "BOOLEAN",
        },
        {
          // BOOLEAN overridden (CUSTOM, value != defaultValue) -> toggle OFF + override gavel
          key: "mock.egress.enabled",
          value: "false",
          defaultValue: "true",
          editable: true,
          refreshable: true,
          description: "Whether egress is enabled. Overridden from its default.",
          propertySource: "CUSTOM",
          dataType: "BOOLEAN",
        },
        {
          // BOOLEAN with a null value -> falls back to default, toggle OFF
          key: "mock.metrics.enabled",
          value: null,
          defaultValue: "false",
          editable: true,
          refreshable: true,
          description: "Whether metrics collection is enabled.",
          propertySource: "DEFAULT",
          dataType: "BOOLEAN",
        },
        {
          // NUMBER overridden -> override gavel
          key: "mock.age-off.days",
          value: "30",
          defaultValue: "13",
          editable: true,
          refreshable: true,
          description: "Number of days before a DeltaFile is aged off.",
          propertySource: "CUSTOM",
          dataType: "NUMBER",
        },
        {
          // LIST -> rendered as chips
          key: "mock.ingress.allowed-sources",
          value: "[\"sourceA\", \"sourceB\", \"sourceC\"]",
          defaultValue: null,
          editable: true,
          refreshable: true,
          description: "Sources permitted to ingress data.",
          propertySource: "CUSTOM",
          dataType: "LIST",
        },
        {
          // MAP -> rendered as chips
          key: "mock.metadata.tags",
          value: "team: platform, env: prod, region: us-east",
          defaultValue: null,
          editable: true,
          refreshable: false,
          description: "Metadata tags applied to every DeltaFile.",
          propertySource: "CUSTOM",
          dataType: "MAP",
        },
        {
          // Non-editable property -> read-only, shown muted, no edit dialog
          key: "mock.cluster.read-only-setting",
          value: "managed-by-operator",
          defaultValue: "managed-by-operator",
          editable: false,
          refreshable: false,
          description: "A property that the current installation does not allow editing.",
          propertySource: "DEFAULT",
          dataType: "STRING",
        },
        {
          // Extremely long value -> verifies wrapping (spaces + a long unbroken token)
          key: "mock.system.message-of-the-day",
          value:
            '{ "tactical": { "url": "https://tactical.deltafi.org", "tags": ["tactical"], "credentials": { "type": "basic", "username": "admin", "passwordEnvVar": "TACTICAL_PASSWORD" } } } and-here-is-a-very-long-unbroken-token-with-no-spaces-to-test-word-breaking-https://example.deltafi.org/very/long/path/segment/that/keeps/going/and/going/and/going',
          defaultValue: null,
          editable: true,
          refreshable: false,
          description: "An extremely long value used to verify text wrapping within the value column.",
          propertySource: "CUSTOM",
          dataType: "STRING",
        },
      ],
    },
  ];

  return data;
};

export default {
  getPropertySets: generateData(),
};
