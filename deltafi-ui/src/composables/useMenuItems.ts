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
import useUtilFunctions from "./useUtilFunctions";

export interface MenuItem {
  name: string;
  icon?: string;
  path?: string;
  url?: string | ComputedRef<string>;
  children?: MenuItem[];
  visible: boolean | ComputedRef<boolean>;
  badge?: () => { visible: boolean; class: string; value: number };
  description?: string;
}

export interface FlattenedPage {
  name: string;
  icon?: string;
  path?: string;
  url?: string;
  group?: string;
  badge?: () => { visible: boolean; class: string; value: number };
  visible: boolean | ComputedRef<boolean>;
}

export default function useMenuItems(): {
  menuItems: ComputedRef<MenuItem[]>;
  flattenedPages: ComputedRef<FlattenedPage[]>;
  pinnedPages: ComputedRef<FlattenedPage[]>;
  getSiblings: (routePath: string) => FlattenedPage[] | null;
  getSiblingMenuItems: (routePath: string) => MenuItem[] | null;
  errorsBadge: ComputedRef<{ visible: boolean; class: string; value: number }>;
  getUrl: (url: string | ComputedRef<string>) => string;
  isVisible: (item: { visible: boolean | ComputedRef<boolean> }) => boolean;
} {
  const { errorCount } = useErrorCount();
  const uiConfig = inject<any>("uiConfig");
  const hasPermission = inject<(permission: string) => boolean>("hasPermission")!;
  const hasSomePermissions = inject<(...permissions: string[]) => boolean>("hasSomePermissions")!;
  const { buildURL } = useUtilFunctions();

  const errorsBadge = computed(() => ({
    visible: errorCount.value > 0,
    class: "badge badge-danger badge-pill",
    value: errorCount.value,
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
      visible: computed(() => uiConfig.leader && hasSomePermissions("StatusView", "SnapshotRead", "DeltaFileMetadataView")),
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
          children: [
            {
              name: "By Member",
              icon: "fas fa-list fa-fw",
              path: "/fleet/errors?tab=0",
              visible: computed(() => hasPermission("DeltaFileMetadataView")),
            },
            {
              name: "By Flow",
              icon: "fas fa-project-diagram fa-fw",
              path: "/fleet/errors?tab=1",
              visible: computed(() => hasPermission("DeltaFileMetadataView")),
            },
            {
              name: "By Message",
              icon: "fas fa-comment-alt fa-fw",
              path: "/fleet/errors?tab=2",
              visible: computed(() => hasPermission("DeltaFileMetadataView")),
            },
          ],
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
          children: [
            {
              name: "All",
              icon: "fas fa-list fa-fw",
              path: "/errors?tab=0",
              visible: computed(() => hasPermission("DeltaFileMetadataView")),
            },
            {
              name: "By Flow",
              icon: "fas fa-project-diagram fa-fw",
              path: "/errors?tab=1",
              visible: computed(() => hasPermission("DeltaFileMetadataView")),
            },
            {
              name: "By Message",
              icon: "fas fa-comment-alt fa-fw",
              path: "/errors?tab=2",
              visible: computed(() => hasPermission("DeltaFileMetadataView")),
            },
          ],
        },
        {
          name: "Filtered",
          icon: "fas fa-filter fa-fw",
          path: "/deltafile/filtered",
          visible: computed(() => hasPermission("DeltaFileMetadataView")),
          children: [
            {
              name: "All",
              icon: "fas fa-list fa-fw",
              path: "/deltafile/filtered?tab=0",
              visible: computed(() => hasPermission("DeltaFileMetadataView")),
            },
            {
              name: "By Flow",
              icon: "fas fa-project-diagram fa-fw",
              path: "/deltafile/filtered?tab=1",
              visible: computed(() => hasPermission("DeltaFileMetadataView")),
            },
            {
              name: "By Cause",
              icon: "fas fa-ban fa-fw",
              path: "/deltafile/filtered?tab=2",
              visible: computed(() => hasPermission("DeltaFileMetadataView")),
            },
          ],
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
          children: [
            {
              name: "System Overview",
              icon: "icomoon grafana",
              url: "/visualization/d/system-overview",
              visible: computed(() => hasPermission("MetricsView")),
            },
            {
              name: "System Performance",
              icon: "icomoon grafana",
              url: "/visualization/d/system-performance",
              visible: computed(() => hasPermission("MetricsView")),
            },
            {
              name: "Provenance",
              icon: "icomoon grafana",
              url: "/visualization/d/provenance",
              visible: computed(() => hasPermission("MetricsView")),
            },
          ],
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
          visible: computed(() => hasPermission("FlowView")),
        },
        {
          name: "Transforms",
          icon: "fas fa-project-diagram fa-fw",
          path: "/config/transforms",
          visible: computed(() => hasPermission("FlowView")),
        },
        {
          name: "Data Sinks",
          icon: "fas fas fa-file-export fa-fw",
          path: "/config/data-sinks",
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
          visible: computed(() => hasPermission("LookupTableRead") && uiConfig.lookupTablesEnabled),
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
          visible: computed(() => hasPermission("Admin") && uiConfig.clusterMode),
        },
        {
          name: "Logs",
          icon: "far fa-rectangle-list fa-fw",
          url: "/logs",
          visible: computed(() => hasPermission("Admin") && !uiConfig.clusterMode),
        },
        {
          name: "GraphiQL",
          icon: "icomoon graphql",
          url: computed(() => buildURL(undefined, "/graphiql")),
          visible: computed(() => hasPermission("Admin")),
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
          });
          // Handle third level (e.g., Grafana dashboard sub-items)
          if (child.children) {
            for (const grandchild of child.children) {
              pages.push({
                name: `${child.name} » ${grandchild.name}`,
                icon: grandchild.icon,
                path: grandchild.path,
                url: grandchild.url ? getUrl(grandchild.url) : undefined,
                group: item.name,
                badge: grandchild.badge,
                visible: grandchild.visible,
              });
            }
          }
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
        });
      }
    }

    return pages;
  });

  // Route path prefix to menu group name mapping
  const routeGroupMap: Record<string, string> = {
    "/system-map": "System Map",
    "/pipeline": "System Map",
    "/fleet/": "Fleet",
    "/deltafile/": "DeltaFiles",
    "/errors": "DeltaFiles",
    "/metrics/": "Metrics",
    "/config/": "Configuration",
    "/admin/": "Administration",
    "/versions": "Administration",
  };

  const getSiblings = (routePath: string): FlattenedPage[] | null => {
    // Find which group this route belongs to
    let groupName: string | null = null;

    for (const [prefix, group] of Object.entries(routeGroupMap)) {
      if (routePath === prefix || routePath.startsWith(prefix)) {
        groupName = group;
        break;
      }
    }

    if (!groupName) {
      return null;
    }

    // Return all visible pages in this group (flattened, for command palette)
    return flattenedPages.value.filter(
      (page) => page.group === groupName && isVisible(page)
    );
  };

  const getSiblingMenuItems = (routePath: string): MenuItem[] | null => {
    // Find which group this route belongs to
    let groupName: string | null = null;

    for (const [prefix, group] of Object.entries(routeGroupMap)) {
      if (routePath === prefix || routePath.startsWith(prefix)) {
        groupName = group;
        break;
      }
    }

    if (!groupName) {
      return null;
    }

    // Find the menu group and return its children (with their own children intact)
    const group = menuItems.value.find((item) => item.name === groupName);
    if (!group || !group.children) {
      return null;
    }

    return group.children.filter((child) => isVisible(child));
  };

  const pinnedPages = computed<FlattenedPage[]>(() => {
    const pins: FlattenedPage[] = [];

    // Dashboard
    const dashboard = flattenedPages.value.find((p) => p.path === "/" && p.name === "Dashboard");
    if (dashboard && isVisible(dashboard)) {
      pins.push(dashboard);
    }

    // DeltaFile Search
    const search = flattenedPages.value.find((p) => p.path === "/deltafile/search");
    if (search && isVisible(search)) {
      pins.push(search);
    }

    // Errors (with badge)
    const errors = flattenedPages.value.find((p) => p.path === "/errors");
    if (errors && isVisible(errors)) {
      pins.push(errors);
    }

    // System Map (Full)
    const systemMap = flattenedPages.value.find((p) => p.path === "/system-map");
    if (systemMap && isVisible(systemMap)) {
      pins.push({ ...systemMap, name: "System Map" });
    }

    return pins;
  });

  return {
    menuItems,
    flattenedPages,
    pinnedPages,
    getSiblings,
    getSiblingMenuItems,
    errorsBadge,
    getUrl,
    isVisible,
  };
}
