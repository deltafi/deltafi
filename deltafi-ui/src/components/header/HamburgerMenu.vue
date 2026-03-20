<!--
   ABOUTME: Slide-out sidebar with full navigation menu.
   ABOUTME: Triggered by hamburger button in AppTopBar, displays all navigation groups.
-->
<!--
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
-->

<template>
  <Drawer ref="sidebarRef" v-model:visible="visible" position="left" class="hamburger-menu-sidebar">
    <template #header>
      <div class="sidebar-header">
        <img class="logo" src="/logo.png" alt="DeltaFi">
        <span class="title">Navigation</span>
      </div>
    </template>

    <div class="menu-content">
      <template v-for="item in menuItems" :key="item.name">
        <template v-if="isVisible(item)">
          <!-- Direct link items (no children) -->
          <router-link v-if="!item.children && item.path" :to="item.path" class="menu-item" :class="{ active: isActive(item) }" @click="close">
            <i :class="item.icon" class="menu-icon" />
            <span class="menu-label">{{ item.name }}</span>
          </router-link>

          <!-- External link items (no children) -->
          <a v-else-if="!item.children && item.url" :href="getUrl(item.url)" target="_blank" class="menu-item" @click="close">
            <i :class="item.icon" class="menu-icon" />
            <span class="menu-label">{{ item.name }}</span>
            <i class="fas fa-external-link-alt external-icon" />
          </a>

          <!-- Group items (with children) -->
          <div v-else-if="item.children && hasVisibleChildren(item)" class="menu-group">
            <div class="group-header" @click="toggleGroup(item.name)">
              <i :class="item.icon || 'fas fa-folder fa-fw'" class="menu-icon" />
              <span class="group-label">{{ item.name }}</span>
              <span v-if="getGroupBadge(item)" :class="getGroupBadge(item).class" class="ml-2">{{ getGroupBadge(item).value }}</span>
              <i class="fas fa-chevron-down chevron-icon" :class="{ expanded: isGroupExpanded(item.name) }" />
            </div>
            <div v-show="isGroupExpanded(item.name)" class="group-children">
              <template v-for="child in item.children" :key="child.name">
                <template v-if="isVisible(child)">
                  <!-- Child with nested children (third level) -->
                  <template v-if="child.children && hasVisibleChildren(child)">
                    <router-link v-if="child.path" :to="child.path" class="menu-item child-item nested-parent" :class="{ active: isChildActive(child) }" @click="close">
                      <i :class="child.icon" class="menu-icon" />
                      <span class="menu-label">{{ child.name }}</span>
                      <span v-if="child.badge && child.badge().visible" :class="child.badge().class" class="ml-auto">{{ child.badge().value }}</span>
                    </router-link>
                    <a v-else-if="child.url" :href="getUrl(child.url)" target="_blank" class="menu-item child-item nested-parent" @click="close">
                      <i :class="child.icon" class="menu-icon" />
                      <span class="menu-label">{{ child.name }}</span>
                      <i class="fas fa-external-link-alt external-icon" />
                    </a>
                    <div class="nested-children">
                      <template v-for="grandchild in child.children" :key="grandchild.name">
                        <template v-if="isVisible(grandchild)">
                          <router-link v-if="grandchild.path" :to="grandchild.path" class="menu-item grandchild-item" :class="{ active: isChildActive(grandchild) }" @click="close">
                            <i :class="grandchild.icon" class="menu-icon" />
                            <span class="menu-label">{{ grandchild.name }}</span>
                          </router-link>
                          <a v-else-if="grandchild.url" :href="getUrl(grandchild.url)" target="_blank" class="menu-item grandchild-item" @click="close">
                            <i :class="grandchild.icon" class="menu-icon" />
                            <span class="menu-label">{{ grandchild.name }}</span>
                            <i class="fas fa-external-link-alt external-icon" />
                          </a>
                        </template>
                      </template>
                    </div>
                  </template>
                  <!-- Regular child item -->
                  <router-link v-else-if="child.path" :to="child.path" class="menu-item child-item" :class="{ active: isChildActive(child) }" @click="close">
                    <i :class="child.icon" class="menu-icon" />
                    <span class="menu-label">{{ child.name }}</span>
                    <span v-if="child.badge && child.badge().visible" :class="child.badge().class" class="ml-auto">{{ child.badge().value }}</span>
                  </router-link>
                  <a v-else-if="child.url" :href="getUrl(child.url)" target="_blank" class="menu-item child-item" @click="close">
                    <i :class="child.icon" class="menu-icon" />
                    <span class="menu-label">{{ child.name }}</span>
                    <i class="fas fa-external-link-alt external-icon" />
                  </a>
                </template>
              </template>
            </div>
          </div>
        </template>
      </template>
    </div>
  </Drawer>
</template>

<script setup lang="ts">
import Drawer from "primevue/drawer";
import { ref, watch, nextTick } from "vue";
import { useRoute } from "vue-router";
import useMenuItems, { type MenuItem } from "@/composables/useMenuItems";

const visible = defineModel<boolean>({ default: false });
const emit = defineEmits<{
  sidebarEnter: [];
  sidebarLeave: [];
}>();

const attachSidebarHoverEvents = () => {
  nextTick(() => {
    const el = document.querySelector(".hamburger-menu-sidebar");
    if (el) {
      el.addEventListener("mouseenter", () => emit("sidebarEnter"));
      el.addEventListener("mouseleave", () => emit("sidebarLeave"));
    }
  });
};

watch(visible, (val) => {
  if (val) {
    attachSidebarHoverEvents();
  }
});

const route = useRoute();
const { menuItems, getUrl, isVisible } = useMenuItems();

const activePage = ref(route.fullPath);
const expandedGroups = ref<Set<string>>(new Set());

// Initialize all groups as expanded
menuItems.value.forEach((item) => {
  if (item.children) {
    expandedGroups.value.add(item.name);
  }
});

const toggleGroup = (name: string) => {
  if (expandedGroups.value.has(name)) {
    expandedGroups.value.delete(name);
  } else {
    expandedGroups.value.add(name);
  }
};

const isGroupExpanded = (name: string): boolean => {
  return expandedGroups.value.has(name);
};

watch(
  () => route.fullPath,
  (fullPath) => {
    activePage.value = fullPath;
  }
);

const close = () => {
  visible.value = false;
};

const isActive = (item: MenuItem): boolean => {
  if (!item.path) return false;
  if (item.name === "Dashboard") {
    return activePage.value === item.path;
  }
  const basePath = item.path.split("?")[0];
  return activePage.value === basePath || activePage.value.startsWith(basePath);
};

const isChildActive = (child: MenuItem): boolean => {
  if (!child.path) return false;
  const hasQuery = child.path.includes("?");
  if (hasQuery) {
    // If child has query params, require exact match
    return activePage.value === child.path;
  }
  // No query params - use startsWith matching
  const basePath = child.path.split("?")[0];
  return activePage.value === basePath || activePage.value.startsWith(basePath);
};

const hasVisibleChildren = (item: MenuItem): boolean => {
  if (!item.children) return false;
  return item.children.some((child) => isVisible(child));
};

const getGroupBadge = (item: MenuItem) => {
  if (!item.children) return null;
  for (const child of item.children) {
    if (child.badge) {
      const badge = child.badge();
      if (badge.visible) return badge;
    }
  }
  return null;
};
</script>

<style scoped lang="scss">
.sidebar-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;

  .logo {
    width: 1.5rem;
    height: 1.5rem;
  }

  .title {
    font-size: 1.1rem;
    font-weight: 500;
  }
}

.menu-content {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 0.4rem 1rem;
  color: #333;
  text-decoration: none;
  border-radius: 4px;
  transition: background-color 0.15s;

  &:hover {
    background-color: #e9ecef;
    text-decoration: none;
    color: #333;
  }

  &.active {
    background-color: #e3f2fd;
    color: #1976d2;
    font-weight: 500;

    .menu-icon {
      color: #1976d2;
    }
  }
}

.menu-icon {
  width: 1.5rem;
  color: #666;
  text-align: center;
  margin-right: 0.75rem;
}

.menu-label {
  flex: 1;
}

.external-icon {
  font-size: 0.75rem;
  color: #999;
  margin-left: auto;
}

.menu-group {
  margin-top: 0.25rem;

  .group-header {
    display: flex;
    align-items: center;
    padding: 0.35rem 1rem;
    font-size: 0.85rem;
    font-weight: 600;
    color: #666;
    text-transform: uppercase;
    letter-spacing: 0.025em;
    cursor: pointer;
    border-radius: 4px;
    transition: background-color 0.15s;

    &:hover {
      background-color: #f5f5f5;
    }
  }

  .chevron-icon {
    margin-left: auto;
    font-size: 0.7rem;
    transition: transform 0.2s ease;
    transform: rotate(-90deg);

    &.expanded {
      transform: rotate(0deg);
    }
  }

  .group-children {
    display: flex;
    flex-direction: column;
    gap: 0.125rem;
  }

  .child-item {
    padding-left: 2.5rem;
  }

  .nested-parent {
    margin-bottom: 0;
  }

  .nested-children {
    display: flex;
    flex-direction: column;
    gap: 0.125rem;
  }

  .grandchild-item {
    padding-left: 3.5rem;
    font-size: 0.9rem;
  }
}

.badge {
  font-size: 0.75rem;
  line-height: 1.2;
}
</style>

<style>
.hamburger-menu-sidebar {
  width: 280px !important;
}

.hamburger-menu-sidebar .p-drawer-header {
  padding: 1rem !important;
  border-bottom: 1px solid #dee2e6;
}

.hamburger-menu-sidebar .p-drawer-content {
  padding: 0.5rem !important;
}
</style>
