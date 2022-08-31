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
  return {
    metadata: [
      {
        key: "city 1",
        value: ["a", "b", "c"],
      },
      {
        key: "state 2",
        value: ["vfg"],
      },
      {
        key: "county 3",
        value: ["a"],
      },
      {
        key: "grate 4",
        value: ["gdeds"],
      },
      {
        key: "district 5",
        value: ["ghr", "fed"],
      },
      {
        key: "money 6",
        value: ["bdrf"],
      },
      {
        key: "hotel 7",
        value: ["dcv"],
      },
    ],
  };
};

export default generateData();
