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
  return [
    {
      target: "passthrough",
      tags: { ingressFlow: "passthrough", name: "stats_counts.bytes_in" },
      datapoints: [
        [null, 1660139070],
        [null, 1660139080],
        [null, 1660139090],
      ],
    },
    {
      target: "smoke",
      tags: { ingressFlow: "smoke", name: "stats_counts.bytes_in" },
      datapoints: [
        [null, 1660139070],
        [100.0, 1660139080],
        [null, 1660139090],
      ],
    },
    {
      target: "stix1_x",
      tags: { ingressFlow: "stix1_x", name: "stats_counts.bytes_in" },
      datapoints: [
        [null, 1660139070],
        [null, 1660139080],
        [null, 1660139090],
      ],
    },
  ];
};

export default generateData();
