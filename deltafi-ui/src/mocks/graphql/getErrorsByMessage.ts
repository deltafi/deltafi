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
    errorSummaryByMessage: {
      count: 4,
      totalCount: 4,
      countPerMessage: [
        {
          count: 7,
          message: "Mock unable to convert STIX 1.x XML to STIX 2.1 JSON",
          flow: "mock_stix1_x",
          dids: ["cae2da97-cbf0-4630-a521-376390e1cb55", "e6601190-3ee3-4d16-a5ed-f8a9f9bf582e", "463743f5-8115-4b4b-b218-70e2ece13032", "1fa21aea-a540-4676-a024-e91e6ce1e19a", "968c1b48-3b19-4468-8fee-f210f02af8d9", "b6faae99-aaae-477a-a15f-6d7275f82465", "7d5af4d4-56b9-46eb-afd1-7421aa02841d"],
        },
        {
          count: 6,
          message: "Mock no compression or archive formats detected",
          flow: "mock_decompress-passthrough",
          dids: ["49fbca43-07dc-4cee-a046-0c8209ec810e", "478429ea-516b-4d36-8a9d-51cd92a59d23", "7872757b-3f69-4c16-bd78-0253f1ba5f42", "1fd33eff-ebdf-48a9-9162-221f88fb690e", "9369451c-3bbb-4af7-88d5-9a44d61f743d", "be132b2b-0bd2-4150-90c9-a3b803ebc8a2"],
        },
        {
          count: 2,
          message: "Mock invalid STIX 2.1 JSON",
          flow: "mock_stix2_1",
          dids: ["aea797bb-f329-4b36-ab3b-0d218158b42a", "032fdf08-adda-4d5c-9fdd-269cccaaca52"],
        },
        {
          count: 2,
          message: "Mock failed to load STIX 2.1 JSON from storage",
          flow: "mock_stix2_1",
          dids: ["fe8b6d9f-7833-45c2-a003-7cbfa630a0a8", "3df9b0b8-7b81-4401-9d7b-118008bbeeed"],
        },
      ],
    },
  };
};

export default generateData();
