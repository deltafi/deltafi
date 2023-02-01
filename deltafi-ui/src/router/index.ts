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

import { createRouter, createWebHistory, RouteRecordRaw } from "vue-router";
import Dashboard from "@/pages/DashboardPage.vue";
import _ from "lodash";

const routes: Array<RouteRecordRaw> = [
  {
    path: "/",
    name: "",
    component: Dashboard,
    meta: {
      permission: "DashboardView",
    },
  },
  {
    path: "/metrics/system",
    name: "System Metrics",
    component: () => import("@/pages/SystemMetricsPage.vue"),
    meta: {
      permission: "MetricsView",
    },
  },
  {
    path: "/deltafile/search",
    name: "DeltaFile Search",
    component: () => import("@/pages/DeltaFileSearchPage.vue"),
    meta: {
      permission: "DeltaFileMetadataView",
    },
  },
  {
    path: "/deltafile/upload",
    name: "DeltaFile Upload",
    component: () => import("@/pages/DeltaFileUploadPage.vue"),
    meta: {
      permission: "DeltaFileIngress",
    },
  },
  {
    path: "/metrics/action",
    name: "Action Metrics",
    component: () => import("@/pages/ActionMetricsPage.vue"),
    meta: {
      permission: "MetricsView",
    },
  },
  {
    path: "/errors",
    name: "Errors",
    component: () => import("@/pages/ErrorsPage.vue"),
    meta: {
      permission: "DeltaFileMetadataView",
    },
  },
  {
    path: "/deltafile/viewer/:did?",
    name: "DeltaFile Viewer",
    component: () => import("@/pages/DeltaFileViewerPage.vue"),
    meta: {
      permission: "DeltaFileMetadataView",
    },
  },
  {
    path: "/config/plugins/:pluginCordinates?",
    name: "Plugins",
    component: () => import("@/pages/PluginsPage.vue"),
    meta: {
      permission: "PluginsView",
    },
  },
  {
    path: "/config/plugin-repositories",
    name: "Repositories",
    component: () => import("@/pages/PluginRepositoryPage.vue"),
    meta: {
      permission: "PluginImageRepoView",
    },
  },
  {
    path: "/config/system",
    name: "System Properties",
    component: () => import("@/pages/SystemPropertiesPage.vue"),
    meta: {
      permission: "SystemPropertiesRead",
    },
  },
  {
    path: "/config/delete-policies",
    name: "Delete Policies",
    component: () => import("@/pages/DeletePoliciesPage.vue"),
    meta: {
      permission: "DeletePolicyRead",
    },
  },
  {
    path: "/config/ingress-routing",
    name: "Ingress Routing",
    component: () => import("@/pages/IngressRoutingPage.vue"),
    meta: {
      permission: "IngressRoutingRuleRead",
    },
  },
  {
    path: "/config/flows",
    name: "Flows",
    component: () => import("@/pages/FlowPlansPage.vue"),
    meta: {
      permission: "FlowView",
    },
  },
  {
    path: "/config/snapshots",
    name: "System Snapshots",
    component: () => import("@/pages/SystemSnapshotsPage.vue"),
    meta: {
      permission: "SnapshotRead",
    },
  },
  {
    path: "/versions",
    name: "Versions",
    component: () => import("@/pages/VersionsPage.vue"),
    meta: {
      permission: "VersionsView",
    },
  },
  {
    path: "/events",
    name: "Events",
    component: () => import("@/pages/EventsPage.vue"),
    meta: {
      permission: "EventRead",
    },
  },
  {
    path: "/admin/users",
    name: "Users",
    component: () => import("@/pages/UsersPage.vue"),
    meta: {
      permission: "UserRead",
    },
  },
  {
    path: "/admin/roles",
    name: "Roles",
    component: () => import("@/pages/RolesPage.vue"),
    meta: {
      permission: "RoleRead",
    },
  },
  {
    path: "/admin/audit",
    name: "Audit Log",
    component: () => import("@/pages/AuditLogPage.vue"),
    meta: {
      permission: "Admin",
    },
  },
  {
    path: "/unauthorized",
    name: "Unauthorized",
    component: () => import("@/pages/UnauthorizedPage.vue"),
  },
  {
    path: "/:catchAll(.*)",
    name: "404 Not Found",
    component: () => import("@/pages/PageNotFound.vue"),
  },
];

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
});

export default router;
