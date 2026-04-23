<!--
   ABOUTME: Primary top navigation bar for major DeltaFi sections.
   ABOUTME: Uses click-open dropdowns on desktop and a popup menu fallback on narrow screens.
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
  <nav class="primary-navbar">
    <div class="nav-main">
      <button class="hamburger-btn" @click="toggleMobileMenu">
        <i class="fas fa-bars mr-2" />
        Menu
      </button>
      <TieredMenu ref="mobileMenu" id="primary-nav-mobile-menu" :model="mobileMenuItems" popup breakpoint="840px" class="mobile-tiered-menu">
        <template #item="{ item, props, hasSubmenu }">
          <router-link v-if="item.route" v-slot="{ href, navigate }" :to="item.route" custom>
            <a :href="href" v-bind="props.action" class="mobile-tiered-menu-item" @click="onMobileRouteClick($event, navigate)">
              <i v-if="item.icon" :class="item.icon" class="mr-2" />
              <span>{{ item.label }}</span>
              <span v-if="item.badgeValue" :class="item.badgeClass" class="ml-auto">{{ item.badgeValue }}</span>
              <i v-else-if="hasSubmenu" class="fas fa-angle-right ml-auto" />
            </a>
          </router-link>
          <a
            v-else
            :href="item.url"
            :target="item.target"
            v-bind="props.action"
            class="mobile-tiered-menu-item"
          >
            <i v-if="item.icon" :class="item.icon" class="mr-2" />
            <span>{{ item.label }}</span>
            <span v-if="item.badgeValue" :class="item.badgeClass" class="ml-auto">{{ item.badgeValue }}</span>
            <i v-else-if="hasSubmenu" class="fas fa-angle-right ml-auto" />
            <i v-else-if="item.target === '_blank'" class="fas fa-external-link-alt ml-auto small" />
          </a>
        </template>
      </TieredMenu>

      <div class="desktop-nav">
        <template v-for="item in visibleMenuItems" :key="item.path || item.name">
          <div v-if="item.children && hasVisibleChildren(item)" class="nav-item-wrapper">
            <button
              class="nav-item nav-item-button"
              :class="{ active: isActive(item), open: openDropdown === item.name }"
              @click="toggleDropdown(item.name)"
            >
              <i v-if="item.icon" :class="item.icon" class="mr-2" />
              <span>{{ item.name }}</span>
              <span v-if="getGroupBadge(item)" :class="getGroupBadge(item).class" class="ml-2">
                {{ getGroupBadge(item).value }}
              </span>
              <i :class="['fas', openDropdown === item.name ? 'fa-chevron-up' : 'fa-chevron-down']" class="dropdown-icon ml-2" />
            </button>

            <div v-if="openDropdown === item.name" class="nav-dropdown">
              <template v-for="child in item.children" :key="child.path || child.name">
                <router-link
                  v-if="child.path && isVisible(child)"
                  :to="child.path"
                  class="nav-dropdown-item"
                  :class="{ active: isActive(child) }"
                  @click="closeDropdown"
                >
                  <i v-if="child.icon" :class="child.icon" class="mr-2" />
                  <span>{{ child.name }}</span>
                  <span v-if="child.badge && child.badge().visible" :class="child.badge().class" class="ml-auto">
                    {{ child.badge().value }}
                  </span>
                </router-link>

                <a
                  v-else-if="child.url && isVisible(child)"
                  :href="getUrl(child.url)"
                  target="_blank"
                  class="nav-dropdown-item"
                  @click="closeDropdown"
                >
                  <i v-if="child.icon" :class="child.icon" class="mr-2" />
                  <span>{{ child.name }}</span>
                  <i class="fas fa-external-link-alt ml-auto small" />
                </a>
              </template>
            </div>
          </div>

          <router-link
            v-else-if="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: isActive(item) }"
          >
            <i v-if="item.icon" :class="item.icon" class="mr-2" />
            <span>{{ item.name }}</span>
            <span v-if="item.badge && item.badge().visible" :class="item.badge().class" class="ml-2">
              {{ item.badge().value }}
            </span>
          </router-link>

          <a
            v-else-if="item.url"
            :href="getUrl(item.url)"
            target="_blank"
            class="nav-item"
          >
            <i v-if="item.icon" :class="item.icon" class="mr-2" />
            <span>{{ item.name }}</span>
            <i class="fas fa-external-link-alt ml-2 small" />
          </a>
        </template>
      </div>
    </div>

    <div class="nav-actions">
      <CommandPaletteButton />
    </div>
  </nav>
</template>

<script setup lang="ts">
import TieredMenu from "primevue/tieredmenu";
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import type { MenuItem as PrimeMenuItem } from "primevue/menuitem";
import useMenuItems, { type MenuItem } from "@/composables/useMenuItems";
import CommandPaletteButton from "@/components/header/CommandPaletteButton.vue";

const route = useRoute();
const { menuItems, getUrl, isVisible, getGroupBadge } = useMenuItems();
const mobileMenu = ref<InstanceType<typeof TieredMenu> | null>(null);
const openDropdown = ref<string | null>(null);
const activePage = ref(route.fullPath);

watch(
  () => route.fullPath,
  (fullPath) => {
    activePage.value = fullPath;
    openDropdown.value = null;
  }
);

const visibleMenuItems = computed(() => menuItems.value.filter((item) => isVisible(item)));

const toPrimeMenuItem = (item: MenuItem): PrimeMenuItem | null => {
  if (!isVisible(item)) return null;

  const childItems = item.children
    ?.map((child) => toPrimeMenuItem(child))
    .filter((child): child is PrimeMenuItem => child !== null);

  const badge = item.children ? getGroupBadge(item) : item.badge?.();

  return {
    key: item.path || item.name,
    label: item.name,
    icon: item.icon,
    items: childItems && childItems.length > 0 ? childItems : undefined,
    route: item.path,
    url: item.url ? getUrl(item.url) : undefined,
    target: item.url ? "_blank" : undefined,
    badgeValue: badge?.visible ? badge.value : undefined,
    badgeClass: badge?.visible ? badge.class : undefined,
  };
};

const mobileMenuItems = computed(() =>
  visibleMenuItems.value
    .map((item) => toPrimeMenuItem(item))
    .filter((item): item is PrimeMenuItem => item !== null)
);

const hasVisibleChildren = (item: MenuItem): boolean => {
  if (!item.children) return false;
  return item.children.some((child) => isVisible(child));
};

const isActive = (item: MenuItem): boolean => {
  if (item.children) {
    for (const child of item.children) {
      if (!isVisible(child) || !child.path) continue;

      const hasQuery = child.path.includes("?");
      if (hasQuery) {
        if (activePage.value === child.path) {
          return true;
        }
      } else {
        const childBasePath = child.path.split("?")[0];
        if (activePage.value === childBasePath || activePage.value.startsWith(childBasePath)) {
          return true;
        }
      }
    }
  }

  if (item.path) {
    if (item.path === "/") {
      return activePage.value === "/";
    }

    const basePath = item.path.split("?")[0];
    if (activePage.value === item.path || activePage.value === basePath) {
      return true;
    }

    if (!item.path.includes("?") && activePage.value.startsWith(basePath)) {
      return true;
    }
  }

  if (!item.children) {
    return false;
  }

  return false;
};

const toggleDropdown = (name: string) => {
  openDropdown.value = openDropdown.value === name ? null : name;
};

const closeDropdown = () => {
  openDropdown.value = null;
};

const toggleMobileMenu = (event: MouseEvent) => {
  closeDropdown();
  mobileMenu.value?.toggle?.(event);
};

const onMobileRouteClick = (event: Event, navigate: (event?: Event) => void) => {
  mobileMenu.value?.hide?.();
  navigate(event);
};

const handleClickOutside = (event: MouseEvent) => {
  const target = event.target as HTMLElement;
  if (!target.closest(".nav-item-wrapper")) {
    closeDropdown();
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
.primary-navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.3rem 0.75rem;
  background: linear-gradient(180deg, var(--df-topnav-gradient-start) 0%, var(--df-topnav-gradient-end) 100%);
  border-bottom: 1px solid var(--df-topnav-border);
  box-shadow: inset 0 1px 0 var(--df-topnav-shadow);
}

.nav-main {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  gap: 0.6rem;
}

.desktop-nav {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
  overflow: visible;
}

.nav-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.hamburger-btn {
  display: none;
  align-items: center;
  justify-content: center;
  padding: 0.45rem 0.7rem;
  border: 1px solid var(--df-topnav-hamburger-border);
  border-radius: 8px;
  background: var(--df-topnav-hamburger-bg);
  color: var(--df-topnav-hamburger-color);
  font-weight: 600;
  font-size: 0.9rem;
  line-height: 1;
  transition: background-color 0.2s, border-color 0.2s, box-shadow 0.2s;

  &:hover {
    background-color: var(--df-topnav-item-hover-bg);
    border-color: var(--df-topnav-item-hover-border);
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  }
}

.nav-item-wrapper {
  position: relative;
  flex-shrink: 0;
}

.nav-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  gap: 0.1rem;
  min-height: 2.7rem;
  padding: 0.52rem 0.7rem;
  color: var(--df-topnav-item-color);
  text-decoration: none;
  border: 1px solid transparent;
  border-radius: 8px;
  border-top-width: 2px;
  font-size: 0.86rem;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  transition: background-color 0.2s, border-color 0.2s, color 0.2s, box-shadow 0.2s;

  &:hover {
    background-color: var(--df-topnav-item-hover-bg);
    border-color: var(--df-topnav-item-hover-border);
    color: var(--df-topnav-item-hover-color);
    text-decoration: none;
  }

  &.active {
    background-color: var(--df-topnav-item-active-bg);
    border-color: var(--df-topnav-item-active-border);
    border-top-color: var(--primary-color);
    color: var(--df-topnav-item-active-color);
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
  }

  &.open:not(.active) {
    background-color: var(--df-topnav-item-hover-bg);
    border-color: var(--df-topnav-item-hover-border);
    border-top-color: var(--df-topnav-item-hover-border);
    color: var(--df-topnav-item-hover-color);
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
  }

  &.open {
    position: relative;
    z-index: 101;
    border-bottom-left-radius: 0;
    border-bottom-right-radius: 0;

    &::after {
      content: "";
      position: absolute;
      left: -1px;
      right: -1px;
      bottom: -1px;
      height: 2px;
      background: var(--df-topnav-item-active-bg);
      border-left: 1px solid var(--df-topnav-item-hover-border);
      border-right: 1px solid var(--df-topnav-item-hover-border);
    }
  }

  i {
    color: var(--df-topnav-item-icon-color);
    flex-shrink: 0;
  }

  .badge {
    font-size: 0.68rem;
    line-height: 1.1;
    flex-shrink: 0;
  }
}

.nav-item-button {
  background: transparent;
  cursor: pointer;
  appearance: none;
}

.dropdown-icon {
  font-size: 0.62rem;
  color: var(--df-topnav-item-icon-color);
}

.nav-dropdown {
  position: absolute;
  top: calc(100% - 1px);
  left: 0;
  min-width: 235px;
  max-width: 320px;
  padding: 0.35rem;
  background: var(--df-topnav-dropdown-bg);
  border: 1px solid var(--df-topnav-dropdown-border);
  border-radius: 0 8px 12px 12px;
  box-shadow: 0 14px 32px var(--df-topnav-dropdown-shadow);
  z-index: 100;
}

.nav-dropdown-item {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.62rem 0.75rem;
  color: var(--df-topnav-dropdown-item-color);
  text-decoration: none;
  border-radius: 9px;
  font-size: 0.84rem;
  line-height: 1.2;

  &:hover {
    background-color: var(--df-topnav-dropdown-item-hover-bg);
    color: var(--df-topnav-item-hover-color);
    text-decoration: none;
  }

  &.active {
    background-color: var(--df-topnav-dropdown-active-bg);
    color: var(--df-topnav-dropdown-active-color);
    font-weight: 600;
  }

  .badge {
    font-size: 0.68rem;
  }
}

@media (max-width: 1200px) {
  .primary-navbar {
    gap: 0.6rem;
  }

  .nav-item {
    padding: 0.54rem 0.62rem 0.5rem;
    font-size: 0.82rem;
  }
}

@media (max-width: 840px) {
  .primary-navbar {
    padding: 0.45rem 0.65rem;
  }

  .desktop-nav {
    display: none;
  }

  .hamburger-btn {
    display: inline-flex;
  }
}

@media (max-width: 520px) {
  .primary-navbar {
    flex-wrap: wrap;
    gap: 0.45rem;
  }

  .nav-main,
  .nav-actions {
    width: 100%;
  }

  .nav-actions {
    justify-content: flex-end;
  }
}

</style>

<style lang="scss">
#primary-nav-mobile-menu.mobile-tiered-menu .p-tieredmenu-root-list {
  min-width: 240px;
}

#primary-nav-mobile-menu.mobile-tiered-menu .p-tieredmenu-item-content {
  border-radius: 10px;
}

#primary-nav-mobile-menu .mobile-tiered-menu-item {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 0.15rem;
  padding: 0.65rem 0.8rem;
  color: var(--df-topnav-mobile-item-color);
  text-decoration: none;
  font-size: 0.88rem;
  font-weight: 600;
}

#primary-nav-mobile-menu .mobile-tiered-menu-item:hover {
  color: var(--df-topnav-item-hover-color);
  text-decoration: none;
}

#primary-nav-mobile-menu.mobile-tiered-menu.p-tieredmenu-mobile .p-tieredmenu-submenu {
  position: absolute;
  top: 0;
  left: calc(100% - 0.35rem);
  min-width: 100%;
  padding-inline-start: 0;
  padding-inline-end: 0;
  border: 1px solid var(--p-tieredmenu-border-color, #d6dbe1);
  box-shadow: var(--p-tieredmenu-shadow, 0 14px 32px rgba(15, 23, 42, 0.14));
}

#primary-nav-mobile-menu.mobile-tiered-menu.p-tieredmenu-mobile .p-tieredmenu-submenu:dir(rtl) {
  left: auto;
  right: calc(100% - 0.35rem);
}

#primary-nav-mobile-menu.mobile-tiered-menu.p-tieredmenu-mobile .p-tieredmenu-submenu-icon {
  transform: none;
}
</style>
