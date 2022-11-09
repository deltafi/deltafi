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
import _ from "lodash";

const Admin = "Admin"

export default {
  install: (app: any, currentUser: any, router: any) => {
    // Checks if the current user has ALL of permissions provided or has the Admin permission.
    const hasAllPermissions = (...permissions: Array<string>) => {
      return _.every(permissions, (permission) => {
        return currentUser.permissions.includes(permission)
      }) || currentUser.permissions.includes(Admin)
    }

    // Checks if the current user has SOME (or any) of permissions provided or has the Admin permission.
    const hasSomePermissions = (...permissions: Array<string>) => {
      return _.some(permissions.concat([Admin]), (permission) => {
        return currentUser.permissions.includes(permission)
      })
    }

    app.config.globalProperties.$hasPermission = hasAllPermissions;
    app.config.globalProperties.$hasAllPermissions = hasAllPermissions;
    app.config.globalProperties.$hasSomePermissions = hasSomePermissions;

    app.provide("hasPermission", hasAllPermissions);
    app.provide("hasAllPermissions", hasAllPermissions);
    app.provide("hasSomePermissions", hasSomePermissions);

    app.directive("has-permission", (el: any, binding: any) => {
      el.style.display = hasAllPermissions(binding.arg) ? "block" : "none";
    });

    router.beforeEach((to: any, from: any, next: any) => {
      const permissionName = _.get(to, "meta.permission", null);
      if (permissionName) {
        if (hasAllPermissions(permissionName)) {
          next();
        } else {
          next({ path: "/unauthorized" });
        }
      } else {
        next();
      }
    });
  },
};
