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
  const data = {
    deltaFiles: [
      {
        did: "27186720-723a-4f82-a5ab-2fff441b2c9b",
        stage: "ERROR",
        created: "2022-03-03T19:23:20.823Z",
        modified: "2022-03-03T19:23:20.888Z",
        sourceInfo: {
          filename: "fakeData.txt",
          flow: "mock.ingressFlow",
        },
      },
      {
        did: "39498989-723a-3333-a5ab-2fff441b2c9b",
        stage: "ERROR",
        created: "2022-03-03T19:23:20.823Z",
        modified: "2022-03-03T19:23:20.888Z",
        sourceInfo: {
          filename: "fakeData.txt",
          flow: "mock.ingressFlow",
        },
      },
    ],
  };

  return data;
};

export default {
  deltaFiles: generateData(),
};
