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

const generateData = () => {
  return {
    count: 2,
    totalCount: 2,
    countPerFlow: [
      {
        count: 2,
        flow: "mock-flow",
        dids: [
          "2549aa54-c7e8-4353-a226-622e71956c5d",
          "6cba7eb0-9604-4f93-bc70-1afc201d10f6"
        ]
      },
      {
        count: 1,
        flow: "another-mock-flow",
        dids: [
          "2f5de6d5-c5dd-45b2-9d6f-3dd412524129"
        ]
      }
    ]
  }
};

export default () => {
  return {
    filteredSummaryByFlow: generateData()
  }
}