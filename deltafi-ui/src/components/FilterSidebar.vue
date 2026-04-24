<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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

<!-- ABOUTME: Generic slide-out filter sidebar component. -->
<!-- ABOUTME: Provides a customizable sidebar with slot for filter content. -->

<template>
  <div v-if="visible" ref="sidebarRef" class="filter-sidebar" :class="{ open: modelValue, 'position-left': position === 'left' }" :style="sidebarStyle">
    <div class="filter-sidebar-content">
      <div class="filter-sidebar-header">
        <h5 class="mb-0">{{ hasTabs ? tabs?.find((t) => t.name === activeTab)?.label : title }}</h5>
        <slot name="header-actions">
          <Button
            v-if="showClearButton"
            v-tooltip.left="clearButtonTooltip"
            icon="pi pi-filter-slash"
            class="p-button-text p-button-sm"
            :disabled="!currentTabHasActiveFilters"
            @click="$emit('clear', activeTab)"
          />
        </slot>
      </div>
      <div class="filter-sidebar-body">
        <!-- Multi-tab mode -->
        <template v-if="hasTabs">
          <template v-for="tab in tabs" :key="tab.name">
            <div v-show="activeTab === tab.name" class="tab-content">
              <slot :name="tab.name" />
            </div>
          </template>
        </template>
        <!-- Single tab mode (backward compatible) -->
        <template v-else>
          <slot />
        </template>
      </div>
    </div>
    <!-- Multi-tab pull-out tabs -->
    <div v-if="hasTabs" class="filter-sidebar-tabs" :style="tabsContainerStyle">
      <div
        v-for="tab in tabs"
        :key="tab.name"
        role="button"
        tabindex="0"
        class="filter-sidebar-tab"
        :class="{ active: activeTab === tab.name && modelValue }"
        @click="selectTab(tab.name)"
        @keydown.enter="selectTab(tab.name)"
      >
        <i :class="activeTab === tab.name && modelValue ? chevronIcon : inactiveChevronIcon" />
        <span class="tab-label">{{ tab.label }}</span>
        <span
          v-if="tab.hasActiveFilters"
          v-tooltip.left="'Clear filters'"
          role="button"
          tabindex="0"
          class="filter-badge clickable"
          @click.stop="$emit('clear', tab.name)"
          @keydown.enter.stop="$emit('clear', tab.name)"
        />
      </div>
    </div>
    <!-- Single tab pull-out tab (backward compatible) -->
    <div v-else role="button" tabindex="0" class="filter-sidebar-tab" :style="tabStyle" @click="toggle" @keydown.enter="toggle">
      <i :class="chevronIcon" />
      <span class="tab-label">{{ tabLabel }}</span>
      <span
        v-if="hasActiveFilters"
        v-tooltip.left="'Clear filters'"
        role="button"
        tabindex="0"
        class="filter-badge clickable"
        @click.stop="$emit('clear')"
        @keydown.enter.stop="$emit('clear')"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch, type PropType } from "vue";
import Button from "primevue/button";

interface Tab {
  name: string;
  label: string;
  hasActiveFilters?: boolean;
}

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: "Filters",
  },
  tabLabel: {
    type: String,
    default: "Filters",
  },
  tabs: {
    type: Array as PropType<Tab[]>,
    default: null,
  },
  width: {
    type: String,
    default: "350px",
  },
  hasActiveFilters: {
    type: Boolean,
    default: false,
  },
  showClearButton: {
    type: Boolean,
    default: true,
  },
  clearButtonTooltip: {
    type: String,
    default: "Clear Filters",
  },
  visible: {
    type: Boolean,
    default: true,
  },
  position: {
    type: String,
    default: "right",
    validator: (value: string) => ["left", "right"].includes(value),
  },
  topOffset: {
    type: String,
    default: "100px",
  },
});

const activeTab = ref<string | null>(null);

// Initialize activeTab when tabs are provided
watch(
  () => props.tabs,
  (tabs) => {
    if (tabs && tabs.length > 0 && !activeTab.value) {
      activeTab.value = tabs[0].name;
    }
  },
  { immediate: true }
);

const hasTabs = computed(() => props.tabs && props.tabs.length > 0);

const currentTabHasActiveFilters = computed(() => {
  if (!hasTabs.value) return props.hasActiveFilters;
  const tab = props.tabs?.find((t) => t.name === activeTab.value);
  return tab?.hasActiveFilters ?? false;
});

const inactiveChevronIcon = computed(() => {
  return props.position === "left" ? "pi pi-chevron-right" : "pi pi-chevron-left";
});

const emit = defineEmits(["update:modelValue", "clear", "tab-change"]);

function selectTab(tabName: string) {
  // If clicking the same tab that's already active and sidebar is open, close it
  if (activeTab.value === tabName && props.modelValue) {
    emit("update:modelValue", false);
    return;
  }
  activeTab.value = tabName;
  emit("tab-change", tabName);
  if (!props.modelValue) {
    emit("update:modelValue", true);
  }
}

const sidebarRef = ref<HTMLElement | null>(null);
const navHeight = ref(0);
const navElement = ref<HTMLElement | null>(null);
const resizeObserver = ref<ResizeObserver | null>(null);

function measureNav() {
  const nav = navElement.value ?? document.querySelector("nav.sticky-top");
  if (nav) {
    navElement.value = nav as HTMLElement;
    navHeight.value = nav.getBoundingClientRect().height;
  }
}

const sidebarStyle = computed(() => {
  const top = navHeight.value > 0 ? `${navHeight.value}px` : props.topOffset;
  return {
    width: props.width,
    top,
    height: `calc(100vh - ${top})`,
    [props.position]: 0,
    transform: props.modelValue
      ? "translateX(0)"
      : props.position === "left"
        ? "translateX(-100%)"
        : "translateX(100%)",
  };
});

const tabStyle = computed(() => {
  if (props.position === "left") {
    return {
      right: "-36px",
      borderRadius: "0 6px 6px 0",
      boxShadow: "2px 0 8px rgba(0, 0, 0, 0.15)",
    };
  }
  return {
    left: "-36px",
    borderRadius: "6px 0 0 6px",
    boxShadow: "-2px 0 8px rgba(0, 0, 0, 0.15)",
  };
});

const tabsContainerStyle = computed(() => {
  if (props.position === "left") {
    return { right: "-36px" };
  }
  return { left: "-36px" };
});

const chevronIcon = computed(() => {
  if (props.position === "left") {
    return props.modelValue ? "pi pi-chevron-left" : "pi pi-chevron-right";
  }
  return props.modelValue ? "pi pi-chevron-right" : "pi pi-chevron-left";
});

function toggle() {
  emit("update:modelValue", !props.modelValue);
}

function handleClickOutside(event: MouseEvent) {
  if (!props.modelValue || !sidebarRef.value) return;
  const target = event.target as HTMLElement;
  if (sidebarRef.value.contains(target)) return;

  // Don't close when clicking inside PrimeVue overlay portals (dropdowns, multiselects, popovers, etc.)
  if (target.closest?.(".p-select-overlay, .p-multiselect-overlay, .p-popover, .p-datepicker-panel, .p-autocomplete-overlay, .p-tooltip, .p-confirmpopup")) return;

  emit("update:modelValue", false);
}

onMounted(() => {
  document.addEventListener("click", handleClickOutside);
  measureNav();
  if (navElement.value) {
    resizeObserver.value = new ResizeObserver(measureNav);
    resizeObserver.value.observe(navElement.value);
  }
});

onUnmounted(() => {
  document.removeEventListener("click", handleClickOutside);
  resizeObserver.value?.disconnect();
});
</script>

<style scoped lang="scss">
.filter-sidebar {
  position: fixed;
  background: var(--surface-card, #fff);
  box-shadow: 4px 0 12px rgba(0, 0, 0, 0.15);
  transition: transform 0.3s ease;
  z-index: 1000;
  display: flex;
}

.filter-sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
  display: flex;
  flex-direction: column;
}

.filter-sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--surface-border, #dee2e6);
  flex-shrink: 0;
}

.filter-sidebar-body {
  flex: 1;
  overflow-y: auto;
}

.filter-sidebar-tabs {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.filter-sidebar-tab {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 36px;
  background: var(--primary-color, #007bff);
  color: white;
  padding: 1rem 0.5rem;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  transition:
    background-color 0.2s,
    opacity 0.3s ease;
  opacity: 0.8;

  &:hover {
    background: var(--primary-600, #0056b3);
    opacity: 1;
  }
}

.filter-sidebar-tabs .filter-sidebar-tab {
  position: relative;
  top: auto;
  transform: none;
  border-radius: 6px 0 0 6px;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.15);

  &:first-child {
    border-radius: 6px 0 0 0;
  }

  &:last-child {
    border-radius: 0 0 0 6px;
  }

  &:only-child {
    border-radius: 6px 0 0 6px;
  }

  &.active {
    opacity: 1;
    background: var(--primary-600, #0056b3);
  }
}

.filter-sidebar.position-left .filter-sidebar-tabs .filter-sidebar-tab {
  border-radius: 0 6px 6px 0;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);

  &:first-child {
    border-radius: 0 6px 0 0;
  }

  &:last-child {
    border-radius: 0 0 6px 0;
  }

  &:only-child {
    border-radius: 0 6px 6px 0;
  }
}

.filter-sidebar.open .filter-sidebar-tab {
  opacity: 1;
}

.filter-sidebar.open .filter-sidebar-tabs .filter-sidebar-tab:not(.active) {
  opacity: 0.6;
}

.tab-label {
  writing-mode: vertical-lr;
  text-orientation: mixed;
  font-size: 0.85rem;
  font-weight: 500;
}

.filter-badge {
  width: 8px;
  height: 8px;
  background: #ffc107;
  border-radius: 50%;
  transition: transform 0.15s ease, box-shadow 0.15s ease;

  &.clickable {
    cursor: pointer;

    &:hover {
      transform: scale(1.5);
      box-shadow: 0 0 6px rgba(255, 193, 7, 0.8);
    }
  }
}

/* Responsive styles for smaller screens */
@media (max-width: 768px) {
  .filter-sidebar {
    max-width: calc(100vw - 50px) !important;
  }

  .filter-sidebar-content {
    padding: 0.75rem;
  }

  .filter-sidebar-tab {
    padding: 0.75rem 0.4rem;
  }

  .tab-label {
    font-size: 0.75rem;
  }
}

@media (max-width: 480px) {
  .filter-sidebar {
    max-width: calc(100vw - 44px) !important;
  }

  .filter-sidebar-content {
    padding: 0.5rem;
  }

  .filter-sidebar-header {
    margin-bottom: 0.75rem;
    padding-bottom: 0.5rem;

    h5 {
      font-size: 1rem;
    }
  }

  .filter-sidebar-tab {
    width: 32px;
    padding: 0.5rem 0.25rem;
    left: -32px !important;
    right: auto !important;
  }

  .filter-sidebar.position-left .filter-sidebar-tab {
    right: -32px !important;
    left: auto !important;
  }

  .filter-sidebar-tabs {
    left: -32px !important;
    right: auto !important;
  }

  .filter-sidebar.position-left .filter-sidebar-tabs {
    right: -32px !important;
    left: auto !important;
  }

  .tab-label {
    font-size: 0.7rem;
  }
}
</style>
