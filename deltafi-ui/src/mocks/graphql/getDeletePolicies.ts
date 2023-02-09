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

import { faker } from "@faker-js/faker";
faker.seed(321);

const generateDeltaFiles = () => {
  return [
    {
      id: faker.datatype.uuid(),
      name:  "mockOneHourAfterComplete",
      flow: null,
      __typename: "TimedDeletePolicy",
      enabled: true,
      afterCreate: null,
      afterComplete: "PT1H",
      minBytes: null,
      deleteMetadata: true,
    },
    {
      id: faker.datatype.uuid(),
      name: "mockTwoHourAfterComplete",
      flow: "passthrough",
      __typename: "TimedDeletePolicy",
      enabled: false,
      afterCreate: null,
      afterComplete: "PT2H",
      minBytes: null,
      deleteMetadata: true,
    },
    {
      id: faker.datatype.uuid(),
      name: "mockThreeHourAfterComplete",
      flow: null,
      __typename: "TimedDeletePolicy",
      enabled: true,
      afterCreate: null,
      afterComplete: "PT3H",
      minBytes: null,
      deleteMetadata: true,
    },
    {
      id: faker.datatype.uuid(),
      name: "mockFourHourAfterComplete",
      flow: "smoke",
      __typename: "TimedDeletePolicy",
      enabled: false,
      afterCreate: null,
      afterComplete: "PT4H",
      minBytes: null,
      deleteMetadata: true,
    },
    {
      id: faker.datatype.uuid(),
      name: "mockOver98PerCent",
      flow: "passthrough",
      __typename: "DiskSpaceDeletePolicy",
      enabled: false,
      maxPercent: 98,
    },
    {
      id: faker.datatype.uuid(),
      name: "mockOver60PerCent",
      flow: null,
      __typename: "DiskSpaceDeletePolicy",
      enabled: false,
      maxPercent: 60,
    },
    {
      id: faker.datatype.uuid(),
      name: "mockOver20PerCent",
      flow: "smoke",
      __typename: "DiskSpaceDeletePolicy",
      enabled: true,
      maxPercent: 20,
    },
  ];
};

export default {
  getDeletePolicies: generateDeltaFiles(),
};
