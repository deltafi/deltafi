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

import { ref, readonly, Ref } from "vue";
import useApi from "./useApi";

export type Permission = {
  name: String;
  description: String;
  category: String;
};

const permissions: Ref<Array<Permission>> = ref([]);
const permissionsByName: Ref<Record<string, Permission>> = ref({});
const permissionsByCategory: Ref<Record<string, Array<Permission>>> = ref({});

export default function usePermissions() {
  const fetchAppPermissions = async () => {
    const { response, get } = useApi();
    const endpoint = "permissions";
    try {
      await get(endpoint);
      permissions.value = response.value;

      permissionsByName.value = permissions.value.reduce((r: any, p: any) => {
        r[p.name] = p;
        return r;
      }, {});

      permissionsByCategory.value = permissions.value.reduce((r: any, p: any) => {
        if (r[p.category] === undefined) r[p.category] = []
        r[p.category].push(p);
        return r;
      }, {})

      return permissions.value;
    } catch {
      //
    }
  };

  return {
    appPermissions: readonly(permissions),
    appPermissionsByName: readonly(permissionsByName),
    appPermissionsByCategory: readonly(permissionsByCategory),
    fetchAppPermissions,
  };
}
