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

import { faker } from "@faker-js/faker";
faker.seed(321);

const generateData = () => {
  const data = {
    getAllFlowAssignmentRules: [
      {
        id: faker.datatype.uuid(),
        name: "filenameRegex136",
        flow: "mock_smoke",
        priority: 500,
        filenameRegex: "^abc.*",
        requiredMetadata: null,
      },
      {
        id: faker.datatype.uuid(),
        name: "filenameRegex562",
        flow: "mock_passthrough",
        priority: 600,
        filenameRegex: "^xyz.*",
        requiredMetadata: null,
      },
      {
        id: faker.datatype.uuid(),
        name: "filenameRegex873",
        flow: "mock_stix1_x",
        priority: 500,
        filenameRegex: "^123.*",
        requiredMetadata: null,
      },
      {
        id: faker.datatype.uuid(),
        name: "filenameRegex936",
        flow: "mock_stix1_1",
        priority: 400,
        filenameRegex: "^kjkl.*",
        requiredMetadata: null,
      },
      {
        id: faker.datatype.uuid(),
        name: "requiredMetadata136",
        flow: "mock_smoke",
        priority: 400,
        filenameRegex: null,
        requiredMetadata: [
          {
            key: "a",
            value: "b",
          },
          {
            key: "c",
            value: "d",
          },
          {
            key: "e",
            value: "f",
          },
        ],
      },
      {
        id: faker.datatype.uuid(),
        name: "requiredMetadata369",
        flow: "mock_smoke",
        priority: 500,
        filenameRegex: null,
        requiredMetadata: [
          {
            key: "g",
            value: "h",
          },
          {
            key: "i",
            value: "j",
          },
          {
            key: "k",
            value: "l",
          },
        ],
      },
      {
        id: faker.datatype.uuid(),
        name: "requiredMetadata543",
        flow: "mock_stix1_1",
        priority: 300,
        filenameRegex: null,
        requiredMetadata: [
          {
            key: "m",
            value: "b",
          },
          {
            key: "n",
            value: "o",
          },
          {
            key: "p",
            value: "q",
          },
        ],
      },
    ],
  };

  return data;
};

export default generateData();
