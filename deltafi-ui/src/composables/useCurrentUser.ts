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

import { reactive, readonly } from "vue";
import useApi from "./useApi";

export type User = {
  name: String;
  permissions: Array<String>;
};

const currentUser: User = reactive({
  name: "Unknown",
  permissions: [],
});

export default function useCurrentUser() {
  const fetchCurrentUser = async () => {
    const { response, get } = useApi();
    const endpoint = "me";
    try {
      await get(endpoint);
      Object.assign(currentUser, response.value);
      return currentUser;
    } catch {
      // Continue regardless of error
    }
  };

  return {
    currentUser: readonly(currentUser),
    fetchCurrentUser,
  };
}
