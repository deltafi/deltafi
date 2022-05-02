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

import hljs from "highlight.js/lib/common";

export const highlightCode = async (code: string, language: string) => {
  const workerStart = Date.now();
  console.debug("Highlight worker started");

  const options = { language: language, ignoreIllegals: true };
  const result = (language && hljs.getLanguage(language))
    ? hljs.highlight(code, options)
    : hljs.highlightAuto(code);

  console.debug("Highlight worker completed in", Date.now() - workerStart, "milliseconds");

  return {
    code: result.value,
    language: result.language
  };
};
