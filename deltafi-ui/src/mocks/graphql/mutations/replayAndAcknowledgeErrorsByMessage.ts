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

// ABOUTME: Mock handler for replayAndAcknowledgeErrorsByMessage mutation.
// ABOUTME: Returns success results for batch replay by error message.

const generateData = () => {
  return {
    replayAndAcknowledgeErrorsByMessage: [
      {
        did: "54bc2ef3-9851-4c79-879a-876b8d6226b2",
        replayDid: "64bc2ef3-9851-4c79-879a-876b8d6226b3",
        success: true,
        error: null,
      },
      {
        did: "9ca12462-7112-433d-9049-61dbe25ab190",
        replayDid: "aca12462-7112-433d-9049-61dbe25ab191",
        success: true,
        error: null,
      },
    ],
  };
};

export default generateData;
