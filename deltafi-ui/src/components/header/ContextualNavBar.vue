<!--
   ABOUTME: Horizontal navigation bar showing sibling pages based on current route.
   ABOUTME: Shows pinned shortcuts (Dashboard, Search, Errors, System Map) on standalone pages.
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
  <nav class="contextual-navbar justify-content-between">
    <div>
      <div class="left-edge-trigger" @mouseenter="startHamburgerHover" @mouseleave="cancelHamburgerHover" />
      <button class="hamburger-btn" @click="hamburgerMenuVisible = true" @mouseenter="startHamburgerHover" @mouseleave="cancelHamburgerHover">
        <i class="fas fa-bars" />
      </button>
      <HamburgerMenu v-model="hamburgerMenuVisible" @sidebar-enter="onSidebarEnter" @sidebar-leave="onSidebarLeave" />
      <template v-for="item in displayedItems" :key="item.path || getUrl(item.url)">
        <!-- Item with children (dropdown) -->
        <div v-if="item.children && hasVisibleChildren(item)" class="nav-item-wrapper" @mouseenter="startHoverOpen(item.name)" @mouseleave="cancelHoverOpen">
          <router-link v-if="item.path" :to="item.path" class="nav-item nav-item-link" :class="{ active: isActive(item) }">
            <i :class="item.icon" class="mr-1" />
            {{ item.name }}
            <span v-if="item.badge && item.badge().visible" :class="item.badge().class" class="ml-1">{{ item.badge().value }}</span>
          </router-link>
          <a v-else-if="item.url" :href="getUrl(item.url)" target="_blank" class="nav-item nav-item-link">
            <i :class="item.icon" class="mr-1" />
            {{ item.name }}
          </a>
          <button class="nav-item-dropdown-toggle" @click="toggleDropdown(item.name)">
            <i :class="['fas', openDropdown === item.name ? 'fa-chevron-up' : 'fa-chevron-down']" />
          </button>
          <div v-if="openDropdown === item.name" class="nav-dropdown">
            <template v-for="child in item.children" :key="child.path || getUrl(child.url)">
              <router-link v-if="child.path && isVisible(child)" :to="child.path" class="nav-dropdown-item" @click="openDropdown = null">
                <i :class="child.icon" class="mr-1" />
                {{ child.name }}
              </router-link>
              <a v-else-if="child.url && isVisible(child)" :href="getUrl(child.url)" target="_blank" class="nav-dropdown-item" @click="openDropdown = null">
                <i :class="child.icon" class="mr-1" />
                {{ child.name }}
                <i class="fas fa-external-link-alt ml-1 small" />
              </a>
            </template>
          </div>
        </div>

        <!-- Simple internal link (no children) -->
        <router-link v-else-if="item.path" :to="item.path" class="nav-item" :class="{ active: isActive(item) }">
          <i :class="item.icon" class="mr-1" />
          {{ item.name }}
          <span v-if="item.badge && item.badge().visible" :class="item.badge().class" class="ml-2">{{ item.badge().value }}</span>
        </router-link>

        <!-- External link (no children) -->
        <a v-else-if="item.url" :href="getUrl(item.url)" target="_blank" class="nav-item">
          <i :class="item.icon" class="mr-1" />
          {{ item.name }}
          <i class="fas fa-external-link-alt ml-1 small" />
        </a>
      </template>
    </div>
    <div>
      <CommandPaletteButton class="mt-2 mr-2"/>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from "vue";
import { useRoute } from "vue-router";
import HamburgerMenu from "@/components/header/HamburgerMenu.vue";
import useMenuItems, { type MenuItem } from "@/composables/useMenuItems";
import CommandPaletteButton from "@/components/header/CommandPaletteButton.vue";

const route = useRoute();
const { getSiblingMenuItems, pinnedPages, getUrl, isVisible } = useMenuItems();
const hamburgerMenuVisible = ref(false);
const openDropdown = ref<string | null>(null);

const activePage = ref(route.fullPath);

watch(
  () => route.fullPath,
  (fullPath) => {
    activePage.value = fullPath;
    openDropdown.value = null; // Close dropdown on navigation
  }
);

const displayedItems = computed<MenuItem[]>(() => {
  const siblings = getSiblingMenuItems(route.path);
  if (siblings && siblings.length > 0) {
    return siblings;
  }
  // Convert pinned pages to MenuItem format for consistent rendering
  return pinnedPages.value.map((page) => ({
    name: page.name,
    icon: page.icon,
    path: page.path,
    url: page.url,
    visible: page.visible,
    badge: page.badge,
  }));
});

const hasVisibleChildren = (item: MenuItem): boolean => {
  if (!item.children) return false;
  return item.children.some((child) => isVisible(child));
};

const isActive = (item: MenuItem): boolean => {
  if (!item.path) return false;

  // Dashboard needs exact match
  if (item.path === "/") {
    return activePage.value === "/";
  }

  // For items with children, check if any child path matches
  if (item.children) {
    for (const child of item.children) {
      if (child.path) {
        const hasQuery = child.path.includes("?");
        if (hasQuery) {
          // If child has query params, require exact match
          if (activePage.value === child.path) return true;
        } else {
          // No query params - use startsWith matching
          if (activePage.value.startsWith(child.path)) return true;
        }
      }
    }
  }

  // For other pages, check if the current path starts with or equals the item path
  const basePath = item.path.split("?")[0];
  return activePage.value === basePath || activePage.value.startsWith(basePath);
};

const toggleDropdown = (name: string) => {
  openDropdown.value = openDropdown.value === name ? null : name;
};

let hoverTimer: ReturnType<typeof setTimeout> | null = null;

const startHoverOpen = (name: string) => {
  cancelHoverOpen();
  hoverTimer = setTimeout(() => {
    openDropdown.value = name;
  }, 300);
};

const cancelHoverOpen = () => {
  if (hoverTimer) {
    clearTimeout(hoverTimer);
    hoverTimer = null;
  }
};

// Hamburger menu hover-to-open logic
let hamburgerHoverTimer: ReturnType<typeof setTimeout> | null = null;
let hamburgerCloseTimer: ReturnType<typeof setTimeout> | null = null;

const startHamburgerHover = () => {
  clearHamburgerCloseTimer();
  if (hamburgerMenuVisible.value) return;
  hamburgerHoverTimer = setTimeout(() => {
    hamburgerMenuVisible.value = true;
  }, 300);
};

const cancelHamburgerHover = () => {
  if (hamburgerHoverTimer) {
    clearTimeout(hamburgerHoverTimer);
    hamburgerHoverTimer = null;
  }
};

const onSidebarEnter = () => {
  clearHamburgerCloseTimer();
  cancelHamburgerHover();
};

const onSidebarLeave = () => {
  hamburgerCloseTimer = setTimeout(() => {
    hamburgerMenuVisible.value = false;
  }, 300);
};

const clearHamburgerCloseTimer = () => {
  if (hamburgerCloseTimer) {
    clearTimeout(hamburgerCloseTimer);
    hamburgerCloseTimer = null;
  }
};

// Close dropdown when clicking outside
const handleClickOutside = (event: MouseEvent) => {
  const target = event.target as HTMLElement;
  if (!target.closest(".nav-item-wrapper")) {
    openDropdown.value = null;
  }
};

onMounted(() => {
  document.addEventListener("click", handleClickOutside);
});

onUnmounted(() => {
  document.removeEventListener("click", handleClickOutside);
});
</script>

<style scoped lang="scss">
.contextual-navbar {
  display: flex;
  align-items: stretch;
  justify-content: flex-start;
  background-color: #f8f9fa;
  border-bottom: 1px solid #dee2e6;
  padding: 0 0.5rem 0 2.5rem;
  overflow: visible;
  white-space: nowrap;
  position: relative;
}

.left-edge-trigger {
  position: fixed;
  left: 0;
  top: 0;
  width: 6px;
  height: 100vh;
  z-index: 10;
}

.hamburger-btn {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  background-color: #343a40;
  color: #fff;
  border: none;
  border-radius: 0 50% 50% 0;
  cursor: pointer;
  font-size: 1rem;
  transition: background-color 0.2s;
  z-index: 1;

  &:hover {
    background-color: #495057;
  }
}

.nav-item-wrapper {
  position: relative;
  display: inline-flex;
  align-items: stretch;

  &:hover > .nav-item-link,
  &:hover > .nav-item-dropdown-toggle {
    background-color: #e9ecef;
  }
}

.nav-item {
  display: inline-flex;
  align-items: center;
  flex-shrink: 1;
  min-width: 0;
  padding: 0.6rem clamp(0.4rem, 1vw, 1.25rem);
  cursor: pointer;
  color: #333;
  text-decoration: none;
  border-bottom: 2px solid transparent;
  transition: background-color 0.2s, border-color 0.2s;
  font-size: clamp(0.7rem, 1.1vw, 1rem);

  &:hover {
    background-color: #e9ecef;
    text-decoration: none;
    color: #333;
  }

  &.active {
    border-bottom-color: #007bff;
    font-weight: 600;
  }

  i {
    color: #666;
    flex-shrink: 0;
  }

  .badge {
    font-size: 0.75em;
    line-height: 1.2;
    flex-shrink: 0;
  }
}

.nav-item-link {
  padding-right: 0.25rem;
}

.nav-item-dropdown-toggle {
  display: inline-flex;
  align-items: center;
  padding: 0.6rem 0.4rem;
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  color: #666;
  font-size: 0.7rem;
  transition: background-color 0.2s;

  &:hover {
    background-color: #e9ecef;
  }
}

.nav-item.active ~ .nav-item-dropdown-toggle {
  border-bottom-color: #007bff;
}

.nav-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  min-width: 150px;
  background: white;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 100;
}

.nav-dropdown-item {
  display: flex;
  align-items: center;
  padding: 0.5rem 0.75rem;
  color: #333;
  text-decoration: none;
  font-size: 0.875rem;
  white-space: nowrap;

  &:hover {
    background-color: #f8f9fa;
    text-decoration: none;
    color: #333;
  }

  &:first-child {
    border-radius: 4px 4px 0 0;
  }

  &:last-child {
    border-radius: 0 0 4px 4px;
  }

  i {
    color: #666;
  }
}

@media (max-width: 1200px) {
  .nav-item {
    font-size: clamp(0.65rem, 1vw, 0.9rem);
    padding: 0.5rem clamp(0.3rem, 0.8vw, 0.75rem);
  }
}

@media (max-width: 768px) {
  .nav-item {
    font-size: clamp(0.6rem, 1.5vw, 0.85rem);
    padding: 0.5rem clamp(0.25rem, 0.6vw, 0.5rem);

    i.mr-1 {
      margin-right: 0.25rem !important;
    }
  }
}

@media (max-width: 480px) {
  .nav-item {
    font-size: 0.65rem;
    padding: 0.4rem 0.25rem;
  }
}
</style>
