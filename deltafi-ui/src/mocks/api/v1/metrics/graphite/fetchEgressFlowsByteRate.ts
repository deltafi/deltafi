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
  return [
    {
      target: "passthrough",
      tags: { ingressFlow: "passthrough", name: "bytes_out" },
      datapoints: [
        [null, 1660138930],
        [null, 1660138940],
        [null, 1660138950],
      ],
    },
    {
      target: "smoke",
      tags: { ingressFlow: "smoke", name: "bytes_out" },
      datapoints: [
        [null, 1660138930],
        [100.0, 1660138940],
        [100.0, 1660138950],
      ],
    },
    {
      target: "stix1_x",
      tags: { ingressFlow: "stix1_x", name: "bytes_out" },
      datapoints: [
        [null, 1660138930],
        [null, 1660138940],
        [null, 1660138950],
      ],
    },
  ];
};

export default generateData();
