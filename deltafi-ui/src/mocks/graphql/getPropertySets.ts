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
      id: "mock-deltafi-common",
      displayName: "Mock Common Properties",
      description: "Mock properties used across all parts of the system.",
      properties: [
        {
          key: "mock.app.url",
          value: "http://mock_url",
          hidden: false,
          editable: true,
          refreshable: false,
          description: "URL to send spans to.",
          propertySource: "DEFAULT",
        },
        {
          key: "mock.app.log.level",
          value: "INFO",
          hidden: true,
          editable: false,
          refreshable: false,
          description: "The log level for app.",
          propertySource: "DEFAULT",
        },
        {
          key: "mock.app.password",
          value: "${APP_PASSWORD}",
          hidden: false,
          editable: false,
          refreshable: false,
          description: "The password used to access app",
          propertySource: "DEFAULT",
        },
        {
          key: "mock.db.password",
          value: "${DB_PASSWORD}",
          hidden: false,
          editable: false,
          refreshable: false,
          description: "Login password of the db server. Cannot be set with URI.",
          propertySource: "DEFAULT",
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
          hidden: false,
          editable: false,
          refreshable: false,
          description: "Mock initial delay in milliseconds before actions attempt to register.",
          propertySource: "DEFAULT",
        },
        {
          key: "mock.actions.hostname",
          value: null,
          hidden: false,
          editable: false,
          refreshable: false,
          description: "Mock hostname of the server running the action.",
          propertySource: "DEFAULT",
        },
      ],
    },
  ];

  return data;
};

export default {
  getPropertySets: generateData(),
};
