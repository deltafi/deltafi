/*
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

const AutoResumePage = () => import("@/pages/AutoResumePage.vue");
const DashboardPage = () => import("@/pages/DashboardPage.vue");
const DataSinkPage = () => import("@/pages/DataSinkPage.vue");
const FleetErrorsPage = () => import("@/pages/FleetErrorsPage.vue");
const FleetSearchPage = () => import("@/pages/FleetSearchPage.vue");
const LeaderConfigPage = () => import("@/pages/LeaderConfigPage.vue");
const LeaderDashboardPage = () => import("@/pages/LeaderDashboardPage.vue");
const DataSourcePage = () => import("@/pages/DataSourcePage.vue");
const DeletePoliciesPage = () => import("@/pages/DeletePoliciesPage.vue");
const DeltaFileSearchPage = () => import("@/pages/DeltaFileSearchPage.vue");
const DeltaFileUploadPage = () => import("@/pages/DeltaFileUploadPage.vue");
const DeltaFileImportPage = () => import("@/pages/DeltaFileImportPage.vue");
const DeltaFileViewerPage = () => import("@/pages/DeltaFileViewerPage.vue");
const ErrorsPage = () => import("@/pages/ErrorsPage.vue");
const EventsPage = () => import("@/pages/EventsPage.vue");
const ExternalLinksPage = () => import("@/pages/ExternalLinksPage.vue");
const FilteredPage = () => import("@/pages/FilteredPage.vue");
const TransformBuilderPage = () => import("@/pages/TransformBuilderPage.vue");
const TransformsPage = () => import("@/pages/TransformsPage.vue");
const PageNotFound = () => import("@/pages/PageNotFound.vue");
const PluginsPage = () => import("@/pages/PluginsPage.vue");
const RolesPage = () => import("@/pages/RolesPage.vue");
const PipelineVisualizationPage = () => import("@/pages/PipelineVisualizationPage.vue");
const SystemMapPage = () => import("@/pages/SystemMapPage.vue");
const SystemMetricsPage = () => import("@/pages/SystemMetricsPage.vue");
const QueueMetricsPage = () => import("@/pages/QueueMetricsPage.vue");
const SystemPropertiesPage = () => import("@/pages/SystemPropertiesPage.vue");
const SystemSnapshotsPage = () => import("@/pages/SystemSnapshotsPage.vue");
const ArtifactRegistryPage = () => import("@/pages/ArtifactRegistryPage.vue");
const ArtifactRegistryDetailPage = () => import("@/pages/ArtifactRegistryDetailPage.vue");
const LookupTablePage = () => import("@/pages/LookupTablePage.vue");
const LookupTableResultPage = () => import("@/pages/LookupTableResultPage.vue");
const TopicsPage = () => import("@/pages/TopicsPage.vue");
const UnauthorizedPage = () => import("@/pages/UnauthorizedPage.vue");
const UsersPage = () => import("@/pages/UsersPage.vue");
const VersionsPage = () => import("@/pages/VersionsPage.vue");

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
    path: "/system-map",
    name: "System Map",
    component: SystemMapPage,
    meta: {
      permission: "DashboardView",
    },
  },
  {
    path: "/pipeline/:flowType?/:flowName?",
    name: "Pipeline",
    component: PipelineVisualizationPage,
    meta: {
      permission: "FlowView",
    },
  },
  {
    path: "/fleet/dashboard",
    name: "Fleet Dashboard",
    component: LeaderDashboardPage,
    meta: {
      permission: "StatusView",
    },
  },
  {
    path: "/fleet/config",
    name: "Fleet Config",
    component: LeaderConfigPage,
    meta: {
      permission: "SnapshotRead",
    },
  },
  {
    path: "/fleet/search",
    name: "Fleet Search",
    component: FleetSearchPage,
    meta: {
      permission: "DeltaFileMetadataView",
    },
  },
  {
    path: "/fleet/errors",
    name: "Fleet Errors",
    component: FleetErrorsPage,
    meta: {
      permission: "DeltaFileMetadataView",
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
    path: "/deltafile/import",
    name: "DeltaFile Import",
    component: DeltaFileImportPage,
    meta: {
      permission: "DeltaFileImport",
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
    path: "/metrics/queues",
    name: "Queue Metrics",
    component: QueueMetricsPage,
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
      permission: "FlowPlanCreate",
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
    path: "/admin/lookup-tables",
    name: "Lookup Table",
    component: LookupTablePage,
    meta: {
      permission: "LookupTableRead",
    },
  },
  {
    path: "/admin/lookup-table/:lookupTableName?",
    name: "Lookup Table Results",
    component: LookupTableResultPage,
    meta: {
      permission: "LookupTableRead",
    },
  },
  {
    path: "/admin/artifact-registry",
    name: "Artifact Registry",
    component: ArtifactRegistryPage,
    meta: {
      permission: "ArtifactRegistryRead",
    },
  },
  {
    path: "/admin/artifact-registry/:owner/:artifactName",
    name: "Artifact Registry Detail",
    component: ArtifactRegistryDetailPage,
    meta: {
      permission: "ArtifactRegistryRead",
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
    path: "/unavailable",
    name: "Service Unavailable",
    component: PageNotFound,
  },
  {
    path: "/:catchAll(.*)",
    name: "404 Not Found",
    component: PageNotFound,
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

export default router;
