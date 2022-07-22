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

import useGraphQL from './useGraphQL'

export default function useErrorResume() {
  const { response, queryGraphQL } = useGraphQL();
  const buildRetryQuery = (dids: Array<string>, removeSourceMetadata: Array<string>, replaceSourceMetadata: Array<Object>) => {
    return {
      retry: {
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

  const resume = async (dids: Array<string>,removeSourceMetadata: Array<string>,replaceSourceMetadata: Array<Object>) => {
    await queryGraphQL(buildRetryQuery(dids,removeSourceMetadata,replaceSourceMetadata), "useErrorsResume", "mutation");
    return Promise.resolve(response);
  };

  return {
    resume,
  };
}