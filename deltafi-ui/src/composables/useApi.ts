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

import { ref, Ref } from "vue";
import useNotifications from "./useNotifications";

export default function useApi(version: Number = 2) {
  const notify = useNotifications();
  const basePath: RequestInfo = `/api/v${version}`;
  const response: Ref<any> = ref({});
  const loading: Ref<boolean> = ref(false);
  const loaded: Ref<boolean> = ref(false);
  const errors: Ref<Array<string>> = ref([]);

  const buildURL = (endpoint: String, params: URLSearchParams = new URLSearchParams()) => {
    let url = `${basePath}/${endpoint}`;
    if (Array.from(params.keys()).length > 0) {
      url += `?${params.toString()}`;
    }
    return url;
  }

  const get = async (endpoint: String, params: URLSearchParams = new URLSearchParams(), parseJSON: boolean = true) => {
    const url = buildURL(endpoint, params);
    const req = new Request(url, { referrer: "" });
    loading.value = true;
    errors.value = [];
    try {
      const res = await fetch(req);
      if (!res.ok) {
        if ([500, 404, 403].includes(res.status)) {
          const body = await res.json();
          notify.error("Error Received from API", body.error);
          errors.value.push(body.error)
        } else {
          throw Error(res.statusText);
        }
        return Promise.reject(res);
      }
      response.value = (parseJSON) ? await res.json() : await res.blob()
      loaded.value = true;
      return Promise.resolve(res);
    } catch (error: any) {
      errors.value.push(error)
      notify.error("Error Contacting API", error.message)
      return Promise.reject(error);
    } finally {
      loading.value = false;
    }
  }

  const postPut = async (verb: string, endpoint: string, body: any, parseJSON: boolean = true) => {
    const url = buildURL(endpoint);
    try {
      const res = await fetch(url, {
        method: verb,
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: JSON.stringify(body),
      });
      if (!res.ok) return Promise.reject(res);
      response.value = (parseJSON) ? await res.json() : await res.blob()
      loaded.value = true;
      return Promise.resolve(res);
    } catch (error: any) {
      errors.value.push(error)
      notify.error("Error Contacting API", error.message);
      return Promise.reject(error);
    } finally {
      loading.value = false;
    }
  }

  const put = async (endpoint: string, body: any, parseJSON: boolean = true) => {
    return postPut('PUT', endpoint, body, parseJSON)
  }

  const post = async (endpoint: string, body: any, parseJSON: boolean = true) => {
    return postPut('POST', endpoint, body, parseJSON)
  }

  const remove = async (endpoint: String) => {
    const url = buildURL(endpoint);
    try {
      const res = await fetch(url, { method: 'DELETE' });
      if (!res.ok) return Promise.reject(res);
      if (res.status != 204) {
          response.value = await res.json();
      }
      loaded.value = true;
      return Promise.resolve(res);
    } catch (error: any) {
      errors.value.push(error)
      notify.error("Error Contacting API", error.message);
      return Promise.reject(error);
    } finally {
      loading.value = false;
    }
  }

  return { response, loading, loaded, errors, get, put, remove, post, buildURL };
}
