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

import _ from "lodash";
import { faker } from "@faker-js/faker";

const generateDeltaFile = () => {
  return {
    did: faker.datatype.uuid(),
    stage: "COMPLETE",
    totalBytes: 65535,
    modified: "2022-02-23T15:19:39.549Z",
    created: "2022-02-23T15:19:39.434Z",
    sourceInfo: {
      filename: "fakeData.txt",
      flow: "smoke",
    },
  }
}

const generateDeltaFiles = (count: number) => {
  return _.times(count, generateDeltaFile);
}

const generateData = (count: number) => {
  const data = {
    offset: 0,
    count: count,
    totalCount: count,
    deltaFiles: generateDeltaFiles(count),
  };

  return data;
};

export default {
  deltaFiles: generateData(1000),
};
