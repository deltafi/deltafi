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

// ABOUTME: Composable for error replay operations.
// ABOUTME: Provides mutations for replaying errored DeltaFiles.

import { EnumType } from 'json-to-graphql-query';
import useGraphQL from './useGraphQL'

export default function useReplayErrors() {
  const { response, queryGraphQL } = useGraphQL();

  const replayErrorsByFlow = async (flowType: string, flowName: string, removeSourceMetadata: Array<string>, replaceSourceMetadata: Array<Object>, limit: number = 1000) => {
    const query = {
      replayErrorsByFlow: {
        __args: {
          flowType: new EnumType(flowType),
          flowName: flowName,
          removeSourceMetadata: removeSourceMetadata,
          replaceSourceMetadata: replaceSourceMetadata,
          limit: limit
        },
        did: true,
        success: true,
        error: true
      }
    };
    await queryGraphQL(query, "replayErrorsByFlow", "mutation");
    return Promise.resolve(response);
  };

  const replayErrorsByMessage = async (errorCause: string, removeSourceMetadata: Array<string>, replaceSourceMetadata: Array<Object>, limit: number = 1000) => {
    const query = {
      replayErrorsByMessage: {
        __args: {
          errorCause: errorCause,
          removeSourceMetadata: removeSourceMetadata,
          replaceSourceMetadata: replaceSourceMetadata,
          limit: limit
        },
        did: true,
        success: true,
        error: true
      }
    };
    await queryGraphQL(query, "replayErrorsByMessage", "mutation");
    return Promise.resolve(response);
  };

  return {
    replayErrorsByFlow,
    replayErrorsByMessage,
  };
}
