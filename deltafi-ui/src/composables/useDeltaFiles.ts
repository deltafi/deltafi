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
        ingressBytes: true,
        sourceInfo: {
          filename: true,
          flow: true,
          metadata: true,
          processingType: true,
        },
        stage: true,
        testMode: true,
        testModeReason: true,
        created: true,
        modified: true,
        actions: {
          name: true,
          type: true,
          state: true,
          created: true,
          modified: true,
          queued: true,
          start: true,
          stop: true,
          filteredCause: true,
          errorCause: true,
          errorContext: true,
          metadata: true,
          content: {
            name: true,
            segments: {
              did: true,
              uuid: true,
              offset: true,
              size: true,
            },
            size: true,
            mediaType: true,
          },
          deleteMetadataKeys: true,
        },
        domains: {
          name: true,
          value: true,
          mediaType: true,
        },
        annotations: true,
        egressed: true,
        enrichment: {
          name: true,
          value: true,
          mediaType: true,
        },
        filtered: true,
        formattedData: {
          metadata: true,
          formatAction: true,
          egressActions: true,
          content: {
            name: true,
            segments: {
              did: true,
              uuid: true,
              offset: true,
              size: true,
            },
            size: true,
            mediaType: true,
          },
        },
        contentDeleted: true,
        contentDeletedReason: true,
        errorAcknowledged: true,
        errorAcknowledgedReason: true,
        replayed: true,
        nextAutoResume: true,
        nextAutoResumeReason: true,
        metadata: true,
      }
    }
  };

  const getDeltaFile = async (did: string) => {
    await queryGraphQL(buildGetDeltaFileQuery(did), "getDeltaFile");
    Object.assign(data, response.value.data.deltaFile)
    loaded.value = true;
  }

  const getRawDeltaFile = async (did: string) => {
    const query = {
      rawDeltaFile: {
        __args: {
          did: did,
          pretty: false
        }
      }
    }
    await queryGraphQL(query, "getRawDeltaFile");
    return response.value.data.rawDeltaFile;
  }

  const cancelDeltaFile = async (dids: Array<string>) => {
    const query = {
      cancel: {
        __args: {
          dids: dids,
        },
        did: true,
        success: true,
        error: true
      }
    };
    await queryGraphQL(query, "cancelDeltaFile", "mutation");
    return response.value.data.cancel;
  }

  return { data, loading, loaded, getDeltaFile, getRawDeltaFile, cancelDeltaFile, errors };
}
