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
import AutoResumePage from "@/pages/AutoResumePage.vue";
import DashboardPage from "@/pages/DashboardPage.vue";
import DataSinkPage from "@/pages/DataSinkPage.vue";
import DataSourcePage from "@/pages/DataSourcePage.vue";
import DeletePoliciesPage from "@/pages/DeletePoliciesPage.vue";
import DeltaFileSearchPage from "@/pages/DeltaFileSearchPage.vue";
import DeltaFileUploadPage from "@/pages/DeltaFileUploadPage.vue";
import DeltaFileViewerPage from "@/pages/DeltaFileViewerPage.vue";
import ErrorsPage from "@/pages/ErrorsPage.vue";
import EventsPage from "@/pages/EventsPage.vue";
import ExternalLinksPage from "@/pages/ExternalLinksPage.vue";
import FilteredPage from "@/pages/FilteredPage.vue";
import TransformBuilderPage from "@/pages/TransformBuilderPage.vue";
import TransformsPage from "@/pages/TransformsPage.vue";
import PageNotFound from "@/pages/PageNotFound.vue";
import PluginRepositoryPage from "@/pages/PluginRepositoryPage.vue";
import PluginsPage from "@/pages/PluginsPage.vue";
import RolesPage from "@/pages/RolesPage.vue";
import SystemMetricsPage from "@/pages/SystemMetricsPage.vue";
import SystemPropertiesPage from "@/pages/SystemPropertiesPage.vue";
import SystemSnapshotsPage from "@/pages/SystemSnapshotsPage.vue";
import TopicsPage from "@/pages/TopicsPage.vue";
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
    path: "/deltafile/search",
    name: "DeltaFile Search",
    component: DeltaFileSearchPage,
    meta: {
      permission: "DeltaFileMetadataView",
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
    path: "/deltafile/upload",
    name: "DeltaFile Upload",
    component: DeltaFileUploadPage,
    meta: {
      permission: "DeltaFileIngress",
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
    path: "/config/system",
    name: "System Properties",
    component: SystemPropertiesPage,
    meta: {
      permission: "SystemPropertiesRead",
    },
  },
  {
    path: "/config/data-sources",
    name: "Data Sources",
    component: DataSourcePage,
    meta: {
      permission: "FlowView",
    },
  },
  {
    path: "/config/transforms",
    name: "Transforms",
    component: TransformsPage,
    meta: {
      permission: "FlowView",
    },
  },
  {
    path: "/config/transform-builder",
    name: "Transform Builder",
    component: TransformBuilderPage,
    meta: {
      permission: "FlowUpdate",
    },
  },
  {
    path: "/config/data-sinks",
    name: "Data Sinks",
    component: DataSinkPage,
    meta: {
      permission: "FlowView",
    },
  },
  {
    path: "/config/topics",
    name: "Topics",
    component: TopicsPage,
    meta: {
      permission: "FlowView",
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
    path: "/config/delete-policies",
    name: "Delete Policies",
    component: DeletePoliciesPage,
    meta: {
      permission: "DeletePolicyRead",
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
    path: "/config/snapshots",
    name: "System Snapshots",
    component: SystemSnapshotsPage,
    meta: {
      permission: "SnapshotRead",
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
