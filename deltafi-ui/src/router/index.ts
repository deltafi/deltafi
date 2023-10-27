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
import ActionMetricsPage from "@/pages/ActionMetricsPage.vue";
import AutoResumePage from "@/pages/AutoResumePage.vue";
import DashboardPage from "@/pages/DashboardPage.vue";
import DeletePoliciesPage from "@/pages/DeletePoliciesPage.vue";
import DeltaFileSearchPage from "@/pages/DeltaFileSearchPage.vue";
import DeltaFileUploadPage from "@/pages/DeltaFileUploadPage.vue";
import DeltaFileViewerPage from "@/pages/DeltaFileViewerPage.vue";
import ErrorsPage from "@/pages/ErrorsPage.vue";
import FilteredPage from "@/pages/FilteredPage.vue";
import EventsPage from "@/pages/EventsPage.vue";
import ExternalLinksPage from "@/pages/ExternalLinksPage.vue";
import FlowPlanBuilderPage from "@/pages/FlowPlanBuilderPage.vue";
import FlowPlansPage from "@/pages/FlowPlansPage.vue";
import IngressActionsPage from "@/pages/IngressActionsPage.vue";
import IngressRoutingPage from "@/pages/IngressRoutingPage.vue";
import PageNotFound from "@/pages/PageNotFound.vue";
import PluginRepositoryPage from "@/pages/PluginRepositoryPage.vue";
import PluginsPage from "@/pages/PluginsPage.vue";
import RolesPage from "@/pages/RolesPage.vue";
import SystemMetricsPage from "@/pages/SystemMetricsPage.vue";
import SystemPropertiesPage from "@/pages/SystemPropertiesPage.vue";
import SystemSnapshotsPage from "@/pages/SystemSnapshotsPage.vue";
import UnauthorizedPage from "@/pages/UnauthorizedPage.vue";
import UsersPage from "@/pages/UsersPage.vue";
import VersionsPage from "@/pages/VersionsPage.vue";

const routes: Array<RouteRecordRaw> = [
  {
    path: "/",
    name: "",
    component: DashboardPage,
    meta: {
      permission: "DashboardView",
    },
  },
  {
    path: "/metrics/system",
    name: "System Metrics",
    component: SystemMetricsPage,
    meta: {
      permission: "MetricsView",
    },
  },
  {
    path: "/deltafile/search",
    name: "DeltaFile Search",
    component: DeltaFileSearchPage,
    meta: {
      permission: "DeltaFileMetadataView",
    },
  },
  {
    path: "/deltafile/upload",
    name: "DeltaFile Upload",
    component: DeltaFileUploadPage,
    meta: {
      permission: "DeltaFileIngress",
    },
  },
  {
    path: "/metrics/action",
    name: "Action Metrics",
    component: ActionMetricsPage,
    meta: {
      permission: "MetricsView",
    },
  },
  {
    path: "/errors",
    name: "Errors",
    component: ErrorsPage,
    meta: {
      permission: "DeltaFileMetadataView",
    },
  },
  {
    path: "/deltafile/filtered",
    name: "Filtered",
    component: FilteredPage,
    meta: {
      permission: "DeltaFileMetadataView",
    },
  },
  {
    path: "/deltafile/viewer/:did?",
    name: "DeltaFile Viewer",
    component: DeltaFileViewerPage,
    meta: {
      permission: "DeltaFileMetadataView",
    },
  },
  {
    path: "/config/plugins/:pluginCordinates?",
    name: "Plugins",
    component: PluginsPage,
    meta: {
      permission: "PluginsView",
    },
  },
  {
    path: "/config/plugin-repositories",
    name: "Repositories",
    component: PluginRepositoryPage,
    meta: {
      permission: "PluginImageRepoView",
    },
  },
  {
    path: "/config/system",
    name: "System Properties",
    component: SystemPropertiesPage,
    meta: {
      permission: "SystemPropertiesRead",
    },
  },
  {
    path: "/config/delete-policies",
    name: "Delete Policies",
    component: DeletePoliciesPage,
    meta: {
      permission: "DeletePolicyRead",
    },
  },
  {
    path: "/config/ingress-routing",
    name: "Ingress Routing",
    component: IngressRoutingPage,
    meta: {
      permission: "IngressRoutingRuleRead",
    },
  },
  {
    path: "/config/auto-resume",
    name: "Auto Resume",
    component: AutoResumePage,
    meta: {
      permission: "ResumePolicyRead",
    },
  },
  {
    path: "/config/flows",
    name: "Flows",
    component: FlowPlansPage,
    meta: {
      permission: "FlowView",
    },
  },
  {
    path: "/config/flow-plan-builder",
    name: "Flow Plan Builder",
    component: FlowPlanBuilderPage,
    meta: {
      permission: "FlowUpdate",
    },
  },
  {
    path: "/config/snapshots",
    name: "System Snapshots",
    component: SystemSnapshotsPage,
    meta: {
      permission: "SnapshotRead",
    },
  },
  {
    path: "/versions",
    name: "Versions",
    component: VersionsPage,
    meta: {
      permission: "VersionsView",
    },
  },
  {
    path: "/events",
    name: "Events",
    component: EventsPage,
    meta: {
      permission: "EventRead",
    },
  },
  {
    path: "/admin/users",
    name: "Users",
    component: UsersPage,
    meta: {
      permission: "UserRead",
    },
  },
  {
    path: "/admin/roles",
    name: "Roles",
    component: RolesPage,
    meta: {
      permission: "RoleRead",
    },
  },
  {
    path: "/admin/external-links",
    name: "External Links",
    component: ExternalLinksPage,
    meta: {
      permission: "Admin",
    },
  },
  {
    path: "/config/ingress-actions",
    name: "Ingress Actions",
    component: IngressActionsPage,
    meta: {
      permission: "FlowView",
    },
  },
  {
    path: "/unauthorized",
    name: "Unauthorized",
    component: UnauthorizedPage,
  },
  {
    path: "/:catchAll(.*)",
    name: "404 Not Found",
    component: PageNotFound,
  },
];

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes,
});

export default router;
