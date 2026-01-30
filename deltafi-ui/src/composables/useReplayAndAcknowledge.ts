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

// ABOUTME: Composable for error replay+acknowledge operations.
// ABOUTME: Provides mutations for replaying and acknowledging errored DeltaFiles.

import { EnumType } from 'json-to-graphql-query';
import useGraphQL from './useGraphQL'

export default function useReplayAndAcknowledge() {
  const { response, queryGraphQL } = useGraphQL();

  const replayAndAcknowledge = async (dids: Array<string>, reason: string, removeSourceMetadata: Array<string>, replaceSourceMetadata: Array<Object>) => {
    const query = {
      replayAndAcknowledge: {
        __args: {
          dids: dids,
          reason: reason,
          removeSourceMetadata: removeSourceMetadata,
          replaceSourceMetadata: replaceSourceMetadata
        },
        did: true,
        replayDid: true,
        success: true,
        error: true
      }
    };
    await queryGraphQL(query, "replayAndAcknowledge", "mutation");
    return Promise.resolve(response);
  };

  const replayAndAcknowledgeErrorsByFlow = async (flowType: string, flowName: string, reason: string, removeSourceMetadata: Array<string>, replaceSourceMetadata: Array<Object>, limit: number = 1000) => {
    const query = {
      replayAndAcknowledgeErrorsByFlow: {
        __args: {
          flowType: new EnumType(flowType),
          flowName: flowName,
          reason: reason,
          removeSourceMetadata: removeSourceMetadata,
          replaceSourceMetadata: replaceSourceMetadata,
          limit: limit
        },
        did: true,
        replayDid: true,
        success: true,
        error: true
      }
    };
    await queryGraphQL(query, "replayAndAcknowledgeErrorsByFlow", "mutation");
    return Promise.resolve(response);
  };

  const replayAndAcknowledgeErrorsByMessage = async (errorCause: string, reason: string, removeSourceMetadata: Array<string>, replaceSourceMetadata: Array<Object>, limit: number = 1000) => {
    const query = {
      replayAndAcknowledgeErrorsByMessage: {
        __args: {
          errorCause: errorCause,
          reason: reason,
          removeSourceMetadata: removeSourceMetadata,
          replaceSourceMetadata: replaceSourceMetadata,
          limit: limit
        },
        did: true,
        replayDid: true,
        success: true,
        error: true
      }
    };
    await queryGraphQL(query, "replayAndAcknowledgeErrorsByMessage", "mutation");
    return Promise.resolve(response);
  };

  return {
    replayAndAcknowledge,
    replayAndAcknowledgeErrorsByFlow,
    replayAndAcknowledgeErrorsByMessage,
  };
}
