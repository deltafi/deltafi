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

import { ref, Ref } from 'vue'
import useApi from './useApi'
import useNotifications from "./useNotifications";

export default function useRoles() {
  const notify = useNotifications();
  const { response, get, put, remove: deleteRole, post, loading, loaded } = useApi();
  const endpoint: string = 'roles';
  const data: Ref<Array<any>> = ref([]);
  const errors: Ref<Array<string>> = ref([]);

  const fetch = async () => {
    try {
      await get(endpoint);
      data.value = response.value;
    } catch (response: any) {
      processErrorResponse(response);
      return Promise.reject(response);
    }
  }

  const update = async (id: number, roleObject: Record<string, any>) => {
    const path = `${endpoint}/${id}`;

    for (const property in roleObject) {
      if (roleObject[property] === "") roleObject[property] = null;
    }

    try {
      await put(path, roleObject);
      notify.info("Role Updated", roleObject.name)
    } catch (response: any) {
      processErrorResponse(response);
      return Promise.reject(response);
    }
  }

  const remove = async (roleObject: Record<string, string>) => {
    try {
      await deleteRole(endpoint, roleObject.id);
      notify.warn("Role Deleted", roleObject.name)
    } catch (response: any) {
      processErrorResponse(response);
      return Promise.reject(response);
    }
  }

  const create = async (roleObject: Record<string, string>) => {
    try {
      await post(endpoint, roleObject);
      notify.info("Role Created", roleObject.name)
    } catch (response: any) {
      processErrorResponse(response);
      return Promise.reject(response);
    }
  }

  const processErrorResponse = async (response: Response) => {
    errors.value.splice(0, errors.value.length)
    const body = await response.json();
    console.error(body)
    if ('error' in body) {
      notify.error("Error Received from API", body.error);
      errors.value.push(body.error)
    } else if ('errors' in body) {
      for (const error of body.errors) {
        notify.error(error)
        errors.value.push(error)
      }
    } else if ('validationErrors' in body) {
      for (const field in body.validationErrors) {
        for (const error of body.validationErrors[field]) {
          const validationError = `${field} ${error}`
          errors.value.push(validationError)
        }
      }
    }
  }

  return { data, loaded, loading, fetch, update, remove, create, errors };
}
