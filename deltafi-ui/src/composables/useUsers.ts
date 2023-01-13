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

export default function useUsers() {
  const notify = useNotifications();
  const { response, get, put, remove: deleteUser, post, loading, loaded } = useApi();
  const endpoint: string = 'users';
  const data: Ref<Array<any>> = ref([]);
  const errors: Ref<Array<string>> = ref([]);

  const fetch = async () => {
    try {
      await get(endpoint);
      data.value = response.value.map((user: any) => {
        user.role_ids = user.roles.map((role: any) => role.id)
        return user;
      });
    } catch (response: any) {
      processErrorResponse(response);
      return Promise.reject(response);
    }
  }

  const update = async (id: number, userObject: Record<string, any>) => {
    const path = `${endpoint}/${id}`;

    for (const property in userObject) {
      if (userObject[property] === "") userObject[property] = null;
    }

    try {
      await put(path, userObject);
      notify.info("User Updated", userObject.name)
    } catch (response: any) {
      processErrorResponse(response);
      return Promise.reject(response);
    }
  }

  const remove = async (userObject: Record<string, string>) => {
    try {
      await deleteUser(endpoint, userObject.id);
      notify.warn("User Deleted", userObject.name)
    } catch (response: any) {
      processErrorResponse(response);
      return Promise.reject(response);
    }
  }

  const create = async (userObject: Record<string, string>) => {
    try {
      await post(endpoint, userObject);
      notify.info("User Created", userObject.name)
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
    } else if ('validation_errors' in body) {
      for (const field in body.validation_errors) {
        for (const error of body.validation_errors[field]) {
          const validationError = `${field} ${error}`
          errors.value.push(validationError)
        }
      }
    }
  }

  return { data, loaded, loading, fetch, update, remove, create, errors };
}
