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

import vkbeautify from "vkbeautify";

export const prettyPrint = async (content: string, format: string, indent: number = 2) => {
  const workerStart = Date.now();
  console.debug("PrettyPrint worker started");

  const output = (function (format) {
    try {
      switch (format) {
        case "json":
          return vkbeautify.json(content, indent);
        case "xml":
          return vkbeautify.xml(content, indent);
        default:
          return content;
      }
    } catch (e: any) {
      return "Error: " + e.message;
    }
  })(format);

  console.debug("PrettyPrint worker completed in", Date.now() - workerStart, "milliseconds");
  return output;
};
