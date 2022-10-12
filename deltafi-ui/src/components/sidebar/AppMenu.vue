<!--
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
-->

<template>
  <nav class="bg-light menu sidebar-scroller">
    <div class="pt-3">
      <ul class="nav flex-column">
        <div v-for="item in menuItems" :key="item">
          <div v-if="!item.hidden">
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
              <li v-if="!child.hidden" v-tooltip.right="child.description" class="nav-item">
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

const route = useRoute();
const { fetchErrorCount, errorCount } = useErrorCount();
const uiConfig = inject("uiConfig");

const externalLinks = computed(() => {
  return JSON.parse(JSON.stringify(uiConfig.externalLinks)).map((link) => {
    link.icon = "fas fa-external-link-alt fa-fw";
    return link;
  });
});

const staticMenuItems = ref([
  { name: "Dashboard", icon: "fas fa-desktop fa-fw", path: "/" },
  {
    name: "Metrics",
    expand: true,
    children: [
      {
        name: "System Metrics",
        icon: "far fa-chart-bar fa-fw",
        path: "/metrics/system",
      },
      {
        name: "Action Metrics",
        icon: "fas fa-chart-line fa-fw",
        path: "/metrics/action",
      },
      {
        name: "Grafana Dashboards",
        icon: "icomoon grafana",
        url: computed(() => `https://metrics.${uiConfig.domain}/dashboards`),
      }
    ],
  },
  {
    name: "Configuration",
    expand: true,
    children: [
      {
        name: "System Properties",
        icon: "fas fa-cogs fa-fw",
        path: "/config/system",
      },
      {
        name: "Flows",
        icon: "fas fa-project-diagram fa-fw",
        path: "/config/flows",
      },
      {
        name: "Plugins",
        icon: "fas fa-plug fa-rotate-90 fa-fw",
        path: "/config/plugins",
      },
      {
        name: "Delete Policies",
        icon: "fas fa-landmark fa-fw",
        path: "/config/delete-policies",
      },
      {
        name: "Ingress Routing",
        icon: "fas fa-route fa-fw",
        path: "/config/ingress-routing",
      },
      {
        name: "System Snapshots",
        icon: "far fa-clock fa-fw",
        path: "/config/snapshots",
      },
    ],
  },
  {
    name: "DeltaFiles",
    expand: true,
    children: [
      {
        name: "Search",
        icon: "fas fa-search fa-fw",
        path: "/deltafile/search",
      },
      {
        name: "Errors",
        icon: "fas fa-exclamation-circle fa-fw",
        path: "/errors",
        badge: () => {
          return errorsBadge.value;
        },
      },
      {
        name: "Viewer",
        icon: "far fa-file fa-fw",
        path: "/deltafile/viewer/",
      },
      {
        name: "Upload",
        icon: "fas fa-upload fa-fw",
        path: "/deltafile/upload/",
      },
    ],
  },
  {
    name: "Administration",
    expand: true,
    children: [
      {
        name: "Audit Log",
        icon: "fas fa-file-alt fa-fw",
        path: "/admin/audit",
        hidden: true,
      },
      {
        name: "Users",
        icon: "fas fa-users fa-fw",
        path: "/admin/users",
      },
      {
        name: "Kubernetes Dashboard",
        icon: "icomoon kubernetes",
        url: computed(() => `https://k8s.${uiConfig.domain}/#/workloads?namespace=deltafi`)
      },
      {
        name: "GraphiQL",
        icon: "icomoon graphql",
        url: computed(() => `https://${uiConfig.domain}/graphiql`)
      },
    ],
  },
  { name: "Versions", icon: "fas fa-info-circle fa-fw", path: "/versions" },
  { name: "Documentation", icon: "fas fa-book fa-fw", url: "/docs" },
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
