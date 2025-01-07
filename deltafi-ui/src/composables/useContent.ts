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

import { ref, Ref } from 'vue'
import useApi from './useApi'

interface ContentSegment {
  did: string;
  uuid: number;
  size: number;
  offset: number;
}

interface Content {
  size: number;
  mediaType: string;
  name: string;
  segments: Array<ContentSegment>
}

export default function useContent() {
  const { response, get, loading, loaded, buildURL, errors } = useApi();
  const endpoint: string = 'content';
  const data: Ref<Blob | undefined> = ref();

  const fetch = async (content: Content) => {
    const params = buildParamString(content)
    await get(endpoint, params, false);
    data.value = response.value;
    return data.value;
  }

  const downloadURL = (content: Content) => {
    const params = buildParamString(content)
    return buildURL(endpoint, params);
  }

  const buildParamString = (content: Content) => {
    const base64 = window.btoa(JSON.stringify(content))
    return new URLSearchParams({ content: base64 });
  }

  return { data, loaded, loading, fetch, downloadURL, errors };
}
