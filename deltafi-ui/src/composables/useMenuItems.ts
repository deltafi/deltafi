// ABOUTME: Shared menu data and navigation helpers for the DeltaFi UI.
// ABOUTME: Provides menu items, siblings lookup, and flattened page list for navigation components.

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

import { computed, inject, ref, type ComputedRef, type Ref } from "vue";
import useErrorCount from "./useErrorCount";
import useInvalidFlowCount from "./useInvalidFlowCount";
import useUiConfig from "./useUiConfig";
import useUtilFunctions from "./useUtilFunctions";

export interface MenuItem {
  name: string;
  icon?: string;
  path?: string;
  url?: string | ComputedRef<string>;
  children?: MenuItem[];
  visible: boolean | ComputedRef<boolean>;
  badge?: () => { visible: boolean; class: string; value: number | string };
  description?: string;
  keywords?: string[];
}

export interface MenuBadge {
  visible: boolean;
  class: string;
  value: number | string;
}

export interface FlattenedPage {
  name: string;
  icon?: string;
  path?: string;
  url?: string;
  group?: string;
  badge?: () => MenuBadge;
  visible: boolean | ComputedRef<boolean>;
  keywords?: string[];
}

export default function useMenuItems(): {
  menuItems: ComputedRef<MenuItem[]>;
  flattenedPages: ComputedRef<FlattenedPage[]>;
  errorsBadge: ComputedRef<MenuBadge>;
  getUrl: (url: string | ComputedRef<string>) => string;
  isVisible: (item: { visible: boolean | ComputedRef<boolean> }) => boolean;
  getGroupBadge: (item: MenuItem) => MenuBadge | null;
} {
  const { errorCount } = useErrorCount();
  const { invalidDataSourceCount, invalidTransformCount, invalidDataSinkCount, startPolling } = useInvalidFlowCount();
  const { uiConfig } = useUiConfig();
  const hasPermission = inject<(permission: string) => boolean>("hasPermission")!;
  const hasSomePermissions = inject<(...permissions: string[]) => boolean>("hasSomePermissions")!;
  const { buildURL } = useUtilFunctions();

  startPolling();

  const errorsBadge = computed(() => ({
    visible: errorCount.value > 0,
    class: "badge badge-danger badge-pill",
    value: errorCount.value,
  }));

  const invalidDataSourceBadge = computed(() => ({
    visible: invalidDataSourceCount.value > 0,
    class: "badge badge-danger badge-pill",
    value: invalidDataSourceCount.value,
  }));

  const invalidTransformBadge = computed(() => ({
    visible: invalidTransformCount.value > 0,
    class: "badge badge-danger badge-pill",
    value: invalidTransformCount.value,
  }));

  const invalidDataSinkBadge = computed(() => ({
    visible: invalidDataSinkCount.value > 0,
    class: "badge badge-danger badge-pill",
    value: invalidDataSinkCount.value,
  }));

  const externalLinks = computed(() => {
    return JSON.parse(JSON.stringify(uiConfig.externalLinks || [])).map((link: any) => {
      link.icon = "fas fa-external-link-alt fa-fw";
      return link;
    });
  });

  const staticMenuItems = ref<MenuItem[]>([
    { name: "Dashboard", icon: "fas fa-desktop fa-fw", path: "/", visible: computed(() => hasPermission("DashboardView")) },
    {
      name: "System Map",
      icon: "fas fa-map fa-fw",
      visible: computed(() => hasSomePermissions("DashboardView", "FlowView")),
      children: [
        {
          name: "Full",
          icon: "fas fa-hexagon-nodes fa-fw",
          path: "/system-map",
          visible: computed(() => hasPermission("DashboardView")),
        },
        {
          name: "Detail",
          icon: "fas fa-project-diagram fa-fw",
          path: "/pipeline",
          visible: computed(() => hasPermission("FlowView")),
        },
      ],
    },
    {
      name: "Fleet",
      icon: "fas fa-server fa-fw",
      visible: computed(() => !!uiConfig.leader && hasSomePermissions("StatusView", "SnapshotRead", "DeltaFileMetadataView")),
      children: [
        {
          name: "Dashboard",
          icon: "fas fa-chart-line fa-fw",
          path: "/fleet/dashboard",
          visible: computed(() => hasPermission("StatusView")),
        },
        {
          name: "Search",
          icon: "fas fa-search fa-fw",
          path: "/fleet/search",
          visible: computed(() => hasPermission("DeltaFileMetadataView")),
        },
        {
          name: "Errors",
          icon: "fas fa-exclamation-circle fa-fw",
          path: "/fleet/errors",
          visible: computed(() => hasPermission("DeltaFileMetadataView")),
        },
        {
          name: "Config",
          icon: "fas fa-sliders fa-fw",
          path: "/fleet/config",
          visible: computed(() => hasPermission("SnapshotRead")),
        },
      ],
    },
    {
      name: "DeltaFiles",
      icon: "fas fa-file-alt fa-fw",
      visible: computed(() => hasSomePermissions("DeltaFileMetadataView", "DeltaFileIngress", "DeltaFileImport")),
      children: [
        {
          name: "Search",
          icon: "fas fa-search fa-fw",
          path: "/deltafile/search",
          visible: computed(() => hasPermission("DeltaFileMetadataView")),
        },
        {
          name: "Errors",
          icon: "fas fa-exclamation-circle fa-fw",
          path: "/errors",
          badge: () => errorsBadge.value,
          visible: computed(() => hasPermission("DeltaFileMetadataView")),
        },
        {
          name: "Filtered",
          icon: "fas fa-filter fa-fw",
          path: "/deltafile/filtered",
          visible: computed(() => hasPermission("DeltaFileMetadataView")),
        },
        {
          name: "Viewer",
          icon: "far fa-file fa-fw",
          path: "/deltafile/viewer/",
          visible: computed(() => hasPermission("DeltaFileMetadataView")),
        },
        {
          name: "Upload",
          icon: "fas fa-upload fa-fw",
          path: "/deltafile/upload/",
          visible: computed(() => hasPermission("DeltaFileIngress")),
        },
        {
          name: "Import",
          icon: "fas fa-file-import fa-fw",
          path: "/deltafile/import",
          visible: false,
        },
      ],
    },
    {
      name: "Metrics",
      icon: "far fa-chart-bar fa-fw",
      visible: computed(() => hasPermission("MetricsView")),
      children: [
        {
          name: "System Metrics",
          icon: "far fa-chart-bar fa-fw",
          path: "/metrics/system",
          visible: computed(() => hasPermission("MetricsView")),
        },
        {
          name: "Queue Metrics",
          icon: "fas fa-layer-group fa-fw",
          path: "/metrics/queues",
          visible: computed(() => hasPermission("MetricsView")),
        },
        {
          name: "Grafana Dashboards",
          icon: "icomoon grafana",
          url: "/visualization/dashboards",
          visible: computed(() => hasPermission("MetricsView")),
        },
      ],
    },
    {
      name: "Configuration",
      icon: "fas fa-cogs fa-fw",
      visible: computed(() => hasSomePermissions("SystemPropertiesRead", "FlowView", "PluginsView", "DeletePolicyRead", "ResumePolicyRead", "SnapshotRead")),
      children: [
        {
          name: "System Properties",
          icon: "fas fa-cogs fa-fw",
          path: "/config/system",
          visible: computed(() => hasPermission("SystemPropertiesRead")),
        },
        {
          name: "Data Sources",
          icon: "fas fas fa-file-import fa-fw",
          path: "/config/data-sources",
          badge: () => invalidDataSourceBadge.value,
          visible: computed(() => hasPermission("FlowView")),
        },
        {
          name: "Transforms",
          icon: "fas fa-project-diagram fa-fw",
          path: "/config/transforms",
          badge: () => invalidTransformBadge.value,
          visible: computed(() => hasPermission("FlowView")),
        },
        {
          name: "Data Sinks",
          icon: "fas fas fa-file-export fa-fw",
          path: "/config/data-sinks",
          badge: () => invalidDataSinkBadge.value,
          visible: computed(() => hasPermission("FlowView")),
        },
        {
          name: "Topics",
          icon: "fas fas fa-database fa-fw",
          path: "/config/topics",
          visible: computed(() => hasPermission("FlowView")),
        },
        {
          name: "Plugins",
          icon: "fas fa-plug fa-rotate-90 fa-fw",
          path: "/config/plugins",
          visible: computed(() => hasPermission("PluginsView")),
        },
        {
          name: "Delete Policies",
          icon: "fas fa-landmark fa-fw",
          path: "/config/delete-policies",
          visible: computed(() => hasPermission("DeletePolicyRead")),
        },
        {
          name: "Auto Resume",
          icon: "fas fa-repeat fa-fw",
          path: "/config/auto-resume",
          visible: computed(() => hasPermission("ResumePolicyRead")),
        },
        {
          name: "System Snapshots",
          icon: "far fa-clock fa-fw",
          path: "/config/snapshots",
          visible: computed(() => hasPermission("SnapshotRead")),
        },
      ],
    },
    {
      name: "Administration",
      icon: "fas fa-user-shield fa-fw",
      visible: computed(() => hasSomePermissions("UserRead", "RoleRead", "LookupTableRead")),
      children: [
        {
          name: "Audit Log",
          icon: "fas fa-file-alt fa-fw",
          path: "/admin/audit",
          visible: false,
        },
        {
          name: "Users",
          icon: "fas fa-users fa-fw",
          path: "/admin/users",
          visible: computed(() => hasPermission("UserRead")),
        },
        {
          name: "Roles",
          icon: "far fa-id-badge fa-fw",
          path: "/admin/roles",
          visible: computed(() => hasPermission("RoleRead")),
        },
        {
          name: "Lookup Tables",
          icon: "fas fa-book-atlas fa-fw",
          path: "/admin/lookup-tables",
          visible: computed(() => hasPermission("LookupTableRead") && !!uiConfig.lookupTablesEnabled),
        },
        {
          name: "External Links",
          icon: "fas fa-link fa-fw",
          path: "/admin/external-links",
          visible: computed(() => hasPermission("Admin")),
        },
        {
          name: "Kubernetes",
          icon: "icomoon kubernetes",
          url: "/orchestration/c/main/workloads",
          visible: computed(() => hasPermission("Admin") && !!uiConfig.clusterMode),
        },
        {
          name: "Logs",
          icon: "far fa-rectangle-list fa-fw",
          url: "/logs",
          visible: computed(() => hasPermission("Admin") && !uiConfig.clusterMode),
        },
        {
          name: "Web Terminal",
          icon: "fas fa-terminal fa-fw",
          url: "/shell/",
          visible: computed(() => hasPermission("Admin") && !!uiConfig.webTerminalEnabled),
          keywords: ["shell"],
        },
        {
          name: "GraphiQL",
          icon: "icomoon graphql",
          url: computed(() => buildURL(undefined, "/graphiql")),
          visible: computed(() => hasPermission("Admin")),
        },
        {
          name: "Swagger UI",
          icon: "fas fa-code fa-fw",
          url: computed(() => buildURL(undefined, "/swagger")),
          visible: computed(() => hasPermission("Admin")),
          keywords: ["rest", "api", "openapi", "swagger"],
        },
        {
          name: "Versions",
          icon: "fas fa-info-circle fa-fw",
          path: "/versions",
          visible: computed(() => hasPermission("VersionsView")),
        },
      ],
    },
    { name: "External Links", icon: "fas fa-external-link-alt fa-fw", visible: false, children: [] },
    { name: "Documentation", icon: "fas fa-book fa-fw", url: buildURL(undefined, "/docs"), visible: true },
  ]);

  const menuItems = computed(() => {
    const items = staticMenuItems.value;
    if (externalLinks.value.length > 0) {
      const index = items.findIndex((obj) => obj.name == "External Links");
      const menu = items[index];
      menu.children = externalLinks.value.map((link: any) => ({
        ...link,
        visible: true,
      }));
      menu.visible = true;
    }
    return items;
  });

  const getUrl = (url: string | ComputedRef<string>): string => {
    return typeof url === "string" ? url : url.value;
  };

  const isVisible = (item: { visible?: boolean | ComputedRef<boolean> }): boolean => {
    if (item.visible === undefined) return true;
    return typeof item.visible === "boolean" ? item.visible : item.visible.value;
  };

  const getGroupBadge = (item: MenuItem): MenuBadge | null => {
    if (!item.children) return null;

    const badges = item.children
      .filter((child) => isVisible(child) && child.badge)
      .map((child) => child.badge!())
      .filter((badge) => badge.visible);

    if (badges.length === 0) {
      return null;
    }

    return {
      ...badges[0],
      value: badges.reduce((total, badge) => total + badge.value, 0),
    };
  };

  const flattenedPages = computed<FlattenedPage[]>(() => {
    const pages: FlattenedPage[] = [];

    for (const item of menuItems.value) {
      if (item.children) {
        for (const child of item.children) {
          pages.push({
            name: child.name,
            icon: child.icon,
            path: child.path,
            url: child.url ? getUrl(child.url) : undefined,
            group: item.name,
            badge: child.badge,
            visible: child.visible,
            keywords: child.keywords,
          });
        }
      } else if (item.path || item.url) {
        pages.push({
          name: item.name,
          icon: item.icon,
          path: item.path,
          url: item.url ? getUrl(item.url) : undefined,
          group: undefined,
          badge: item.badge,
          visible: item.visible,
          keywords: item.keywords,
        });
      }
    }

    return pages;
  });

  return {
    menuItems,
    flattenedPages,
    errorsBadge,
    getUrl,
    isVisible,
    getGroupBadge,
  };
}
