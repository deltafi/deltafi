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

const generateData = (count: number) => {
  return Array.from(Array(count)).map(() => {
    return {
      id: faker.string.uuid(),
      name: faker.lorem.sentence(5),
      errorSubstring: faker.lorem.sentence(5),
      flow: "mock " + faker.lorem.word(),
      action: "mocksmoke.SmokeEgressAction",
      maxAttempts: faker.number.int({
        min: 2,
        max: 5,
      }),
      priority: faker.number.int({
        min: 250,
        max: 500,
      }),
      backOff: {
        delay: faker.number.int({
          min: 250,
          max: 500,
        }),
        maxDelay: null,
        multiplier: null,
        random: null,
      },
    };
  });
};

export default {
  getAllResumePolicies: generateData(10),
};
