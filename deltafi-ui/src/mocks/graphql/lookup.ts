/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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

export default {
  lookup: {
    totalCount: 195,
    rows: [
      [
        { column: "code", value: "US" },
        { column: "name", value: "United States" },
        { column: "region", value: "North America" },
        { column: "last_updated", value: new Date(Date.now() - 86400000).toISOString() },
      ],
      [
        { column: "code", value: "CA" },
        { column: "name", value: "Canada" },
        { column: "region", value: "North America" },
        { column: "last_updated", value: new Date(Date.now() - 86400000).toISOString() },
      ],
      [
        { column: "code", value: "MX" },
        { column: "name", value: "Mexico" },
        { column: "region", value: "North America" },
        { column: "last_updated", value: new Date(Date.now() - 86400000).toISOString() },
      ],
      [
        { column: "code", value: "GB" },
        { column: "name", value: "United Kingdom" },
        { column: "region", value: "Europe" },
        { column: "last_updated", value: new Date(Date.now() - 86400000).toISOString() },
      ],
      [
        { column: "code", value: "DE" },
        { column: "name", value: "Germany" },
        { column: "region", value: "Europe" },
        { column: "last_updated", value: new Date(Date.now() - 86400000).toISOString() },
      ],
    ],
  },
};
