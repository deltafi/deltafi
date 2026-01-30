/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

// ABOUTME: Mock handler for replayAndAcknowledge mutation.
// ABOUTME: Returns success results for each DID provided.

interface RequestBody {
  variables: {
    dids: string[];
    reason: string;
    removeSourceMetadata?: string[];
    replaceSourceMetadata?: { key: string; value: string }[];
  };
}

const generateData = (req: { body: RequestBody }) => {
  const dids = req.body?.variables?.dids || [];
  return {
    replayAndAcknowledge: dids.map((did: string) => ({
      did: did,
      replayDid: crypto.randomUUID(),
      success: true,
      error: null,
    })),
  };
};

export default generateData;
