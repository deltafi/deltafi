<!--
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
-->

<template>
  <nav class="badge-light menu sidebar-scroller">
    <div class="pt-3">
      <ul class="nav flex-column">
        <div v-for="item in menuItems" :key="item">
          <div v-if="item.visible">
            <li v-tooltip.right="item.description" class="nav-item">
              <span v-if="item.children" :class="menuItemClass(item)" @click.prevent="item.expand = !item.expand">
                <i :class="folderIcon(item)" />
                {{ item.name }}
              </span>
              <router-link v-else-if="item.path" :to="item.path" :class="menuItemClass(item)">
                <i :class="item.icon" />
                {{ item.name }}
              </router-link>
              <a v-else-if="item.url" :href="item.url" target="_blank" :class="menuItemClass(item)">
                <i :class="item.icon" />
                {{ item.name }}
              </a>
            </li>
            <div v-for="child in item.children" :key="child" :class="{ hidden: !item.expand, submenu: true }">
              <li v-if="child.visible" v-tooltip.right="child.description" class="nav-item">
                <router-link v-if="child.path" :to="child.path" :class="menuItemClass(child, true)">
                  <span class="d-flex justify-content-between">
                    <span>
                      <i :class="child.icon" />
                      {{ child.name }}
                    </span>
                    <span v-if="child.badge && child.badge().visible" :class="child.badge().class">{{ child.badge().value }}</span>
                  </span>
                </router-link>
                <a v-else-if="child.url" :href="child.url" target="_blank" :class="menuItemClass(child, true)">
                  <i :class="child.icon" />
                  {{ child.name }}
                </a>
              </li>
            </div>
          </div>
        </div>
      </ul>
    </div>
  </nav>
</template>

<script setup>
import useErrorCount from "@/composables/useErrorCount";
import { computed, ref, watch, inject, onMounted } from "vue";
import { useRoute } from "vue-router";
import useUtilFunctions from "@/composables/useUtilFunctions";

const route = useRoute();
const { fetchErrorCount, errorCount } = useErrorCount();
const uiConfig = inject("uiConfig");
const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");
const { buildURL } = useUtilFunctions();

const externalLinks = computed(() => {
  return JSON.parse(JSON.stringify(uiConfig.externalLinks)).map((link) => {
    link.icon = "fas fa-external-link-alt fa-fw";
    return link;
  });
});

const staticMenuItems = ref([
  { name: "Dashboard", icon: "fas fa-desktop fa-fw", path: "/", visible: computed(() => hasPermission("DashboardView")) },
  {
    name: "Metrics",
    expand: true,
    visible: computed(() => hasPermission("MetricsView")),
    children: [
      {
        name: "System Metrics",
        icon: "far fa-chart-bar fa-fw",
        path: "/metrics/system",
        visible: computed(() => hasPermission("MetricsView")),
      },
      {
        name: "Action Metrics",
        icon: "fas fa-chart-line fa-fw",
        path: "/metrics/action",
        visible: computed(() => hasPermission("MetricsView")),
      },
      {
        name: "Grafana Dashboards",
        icon: "icomoon grafana",
        url: computed(() => buildURL("metrics", "/dashboards")),
        visible: computed(() => hasPermission("MetricsView")),
      },
    ],
  },
  {
    name: "DeltaFiles",
    expand: true,
    visible: computed(() => hasSomePermissions("DeltaFileMetadataView", "DeltaFileIngress")),
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
        badge: () => {
          return errorsBadge.value;
        },
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
    ],
  },
  {
    name: "Configuration",
    expand: true,
    visible: computed(() => hasSomePermissions("SystemPropertiesRead", "FlowView", "PluginsView", "PluginImageRepoView", "DeletePolicyRead", "IngressRoutingRuleRead", "ResumePolicyRead", "SnapshotRead")),
    children: [
      {
        name: "System Properties",
        icon: "fas fa-cogs fa-fw",
        path: "/config/system",
        visible: computed(() => hasPermission("SystemPropertiesRead")),
      },
      {
        name: "Flows",
        icon: "fas fa-project-diagram fa-fw",
        path: "/config/flows",
        visible: computed(() => hasPermission("FlowView")),
      },
      {
        name: "Flow Plan Builder",
        icon: "fa-solid fa-trowel-bricks fa-fw",
        path: "/config/flow-plan-builder",
        visible: computed(() => hasPermission("FlowView")),
      },
      {
        name: "Plugins",
        icon: "fas fa-plug fa-rotate-90 fa-fw",
        path: "/config/plugins",
        visible: computed(() => hasPermission("PluginsView")),
      },
      {
        name: "Repositories",
        icon: "fas fa-code-branch fa-fw",
        path: "/config/plugin-repositories",
        visible: computed(() => hasPermission("PluginImageRepoView")),
      },
      {
        name: "Delete Policies",
        icon: "fas fa-landmark fa-fw",
        path: "/config/delete-policies",
        visible: computed(() => hasPermission("DeletePolicyRead")),
      },
      {
        name: "Ingress Actions",
        icon: "fa-solid fa-cubes fa-fw",
        path: "/config/ingress-actions",
        visible: computed(() => hasPermission("FlowView")),
      },
      {
        name: "Ingress Routing",
        icon: "fas fa-route fa-fw",
        path: "/config/ingress-routing",
        visible: computed(() => hasPermission("IngressRoutingRuleRead")),
      },
      {
        name: "Auto Resume",
        icon: "fas fa-clock-rotate-left fa-flip-horizontal fa-fw",
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
    expand: true,
    visible: computed(() => hasSomePermissions("UserRead", "RoleRead")),
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
        name: "External Links",
        icon: "fas fa-link fa-fw",
        path: "/admin/external-links",
        visible: computed(() => hasPermission("Admin")),
      },
      {
        name: "Kubernetes Dashboard",
        icon: "icomoon kubernetes",
        url: computed(() => buildURL("k8s", "/#/workloads?namespace=deltafi")),
        visible: computed(() => hasPermission("Admin") && uiConfig.clusterMode),
      },
      {
        name: "GraphiQL",
        icon: "icomoon graphql",
        url: computed(() => buildURL(null, "/graphiql")),
        visible: computed(() => hasPermission("Admin")),
      },
    ],
  },
  { name: "Versions", icon: "fas fa-info-circle fa-fw", path: "/versions", visible: computed(() => hasPermission("VersionsView")) },
  { name: "Documentation", icon: "fas fa-book fa-fw", url: "/docs", visible: true },
]);

const menuItems = computed(() => {
  let items = staticMenuItems.value;
  if (externalLinks.value.length > 0) {
    const externalLinksObject = {
      name: "External Links",
      expand: true,
      children: externalLinks.value,
    };

    const objIndex = items.findIndex((obj) => obj.name == "External Links");
    if (objIndex != -1) {
      items[objIndex] = externalLinksObject;
    } else {
      items.splice(5, 0, externalLinksObject);
    }
  }
  return items;
});

const errorsBadge = computed(() => {
  return {
    visible: errorCount.value > 0,
    class: "badge badge-danger badge-pill",
    value: errorCount.value,
  };
});

const folderIcon = (item) => {
  return item.expand ? "fas fa-angle-down fa-fw" : "fas fa-angle-right fa-fw";
};

const menuItemClass = (item, isChild = false) => {
  let classes = ["nav-link", "noselect"];
  if (isChild) classes.push("indent");
  if (item.children) classes.push("folder");
  if (item.name === "Dashboard") {
    if (activePage.value === item.path) classes.push("active");
  } else {
    if (activePage.value.includes(item.path)) classes.push("active");
  }
  return classes.join(" ");
};

const activePage = ref(route.path);

watch(
  () => route.path,
  (path) => {
    activePage.value = path;
  }
);

onMounted(() => {
  fetchErrorCount();
});
</script>

<style scoped lang="scss">
@import "@/styles/components/sidebar/app-menu.scss";
</style>
