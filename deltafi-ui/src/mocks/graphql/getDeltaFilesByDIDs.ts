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
faker.seed(123);

const generateData = (count: number) => {
  return Array.from(Array(count)).map(() => {
    const uuid = faker.datatype.uuid();
    const date = new Date();
    return {
      did: faker.datatype.uuid(),
      stage: "ERROR",
      created: "2021-2023-03-03T19:23:20.823Z",
      modified: "2021-2023-03-03T19:23:20.888Z",
      sourceInfo: {
        filename: faker.system.commonFileName("txt"),
        flow: "mock.ingressFlow",
      },
    };
  });
};

export default {
  deltaFiles: {
    deltaFiles: generateData(Math.floor(Math.random() * 100)),
  },
};
