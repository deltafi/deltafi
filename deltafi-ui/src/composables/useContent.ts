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
// ABOUTME: Composable for fetching DeltaFile content by location pointer.
// ABOUTME: Builds content requests and provides fetch/download URL functionality.

import { ref, Ref } from 'vue'
import useApi from './useApi'

export interface ContentPointer {
  did: string;
  flowNumber: number;
  actionIndex?: number;  // undefined for flow input
  contentIndex: number;
  size?: number;         // for partial reads
  // Display fields (not sent to API):
  name: string;
  mediaType: string;
  totalSize: number;
  tags?: string[];
}

interface ContentRequest {
  did: string;
  flowNumber: number;
  actionIndex?: number;
  contentIndex: number;
  size?: number;
}

export default function useContent() {
  const { response, get, loading, loaded, buildURL, errors } = useApi();
  const endpoint: string = 'content';
  const data: Ref<Blob | undefined> = ref();

  const fetch = async (pointer: ContentPointer) => {
    const params = buildParamString(pointer)
    await get(endpoint, params, false);
    data.value = response.value;
    return data.value;
  }

  const downloadURL = (pointer: ContentPointer) => {
    const params = buildParamString(pointer)
    return buildURL(endpoint, params);
  }

  const buildParamString = (pointer: ContentPointer) => {
    const request: ContentRequest = {
      did: pointer.did,
      flowNumber: pointer.flowNumber,
      contentIndex: pointer.contentIndex,
    };
    if (pointer.actionIndex !== undefined) {
      request.actionIndex = pointer.actionIndex;
    }
    if (pointer.size !== undefined) {
      request.size = pointer.size;
    }
    const base64 = window.btoa(JSON.stringify(request))
    return new URLSearchParams({ content: base64 });
  }

  return { data, loaded, loading, fetch, downloadURL, errors };
}
