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

import useGraphQL from './useGraphQL'

export default function useReplay() {
  const { response, queryGraphQL } = useGraphQL();
  const buildReplayQuery = (dids: Array<string>, removeSourceMetadata: Array<string>, replaceSourceMetadata: Array<Object>) => {
    return {
      replay: {
        __args: {
          dids: dids,
          removeSourceMetadata: removeSourceMetadata,
          replaceSourceMetadata: replaceSourceMetadata
        },
        did: true,
        success: true,
        error: true
      }
    };
  };

  const replay = async (dids: Array<string>, removeSourceMetadata: Array<string>, replaceSourceMetadata: Array<Object>) => {
    await queryGraphQL(buildReplayQuery(dids, removeSourceMetadata, replaceSourceMetadata), "useReplay", "mutation");
    return Promise.resolve(response);
  };

  return {
    replay,
  };
}
