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

import { reactive, ref } from 'vue'
import useGraphQL from './useGraphQL'

export default function useDeltaFiles() {
  const { response, queryGraphQL, loading, errors } = useGraphQL();
  const loaded = ref(false);
  const data = reactive({});

  const buildGetDeltaFileQuery = (did: string) => {
    return {
      deltaFile: {
        __args: {
          did: did,
        },
        did: true,
        parentDids: true,
        childDids: true,
        totalBytes: true,
        sourceInfo: {
          filename: true,
          flow: true,
          metadata: {
            key: true,
            value: true,
          },
        },
        stage: true,
        created: true,
        modified: true,
        actions: {
          name: true,
          state: true,
          created: true,
          modified: true,
          queued: true,
          start: true,
          stop: true,
          errorCause: true,
          errorContext: true,
        },
        domains: {
          name: true,
          value: true,
          mediaType: true,
        },
        egressed: true,
        enrichment: {
          name: true,
          value: true,
          mediaType: true,
        },
        filtered: true,
        formattedData: {
          filename: true,
          metadata: {
            key: true,
            value: true,
          },
          formatAction: true,
          egressActions: true,
          contentReference: {
            did: true,
            uuid: true,
            offset: true,
            size: true,
            mediaType: true,
          },
        },
        protocolStack: {
          action: true,
          metadata: {
            key: true,
            value: true,
          },
          content: {
            name: true,
            metadata: {
              key: true,
              value: true
            },
            contentReference: {
              did: true,
              uuid: true,
              offset: true,
              size: true,
              mediaType: true
            }
          },
        },
        markedForDelete: true,
        markedForDeleteReason: true,
        errorAcknowledged: true,
        errorAcknowledgedReason: true,
      }
    }
  };

  const getDeltaFile = async (did: string) => {
    await queryGraphQL(buildGetDeltaFileQuery(did), "getDeltaFile");
    Object.assign(data, response.value.data.deltaFile)
    loaded.value = true;
  }

  return { data, loading, loaded, getDeltaFile, errors };
}