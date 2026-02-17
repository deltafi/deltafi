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
  <nav class="navbar navbar-dark p-0 shadow" :style="{ backgroundColor: backgroundColor, color: textColor }">
    <span class="navbar-brand-wrapper">
      <div class="navbar-brand col mr-0 px-3">
        <div class="row align-items-center">
          <img class="logo" src="/logo.png">
          <div class="col title" :style="{ color: textColor }">{{ uiConfig.title }}</div>
        </div>
      </div>
    </span>
    <span class="navbar-text col">
      <Version style="font-size: 0.9rem" />
    </span>
    <span class="d-flex align-items-center">
      <button class="search-trigger mr-3" @click="openCommandPalette">
        <i class="pi pi-search" />
        <span class="search-placeholder">Search...</span>
        <kbd class="search-hotkey">{{ isMac ? '⌘' : 'Ctrl' }}K</kbd>
      </button>
      <Clock class="mr-3" />
      <NotificationBadge v-if="$hasPermission('EventRead')" class="mr-3" />
      <ErrorCountBadge v-if="$hasPermission('DeltaFileMetadataView')" class="mr-3" />
      <StatusBadge class="mr-3" />
      <UserBadge class="mr-3" />
    </span>
  </nav>
</template>

<script setup>
import Clock from "@/components/Clock.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import NotificationBadge from "@/components/NotificationBadge.vue";
import ErrorCountBadge from "@/components/header/ErrorCountBadge.vue";
import UserBadge from "@/components/UserBadge.vue";
import Version from "@/components/Version.vue";
import useCommandPalette from "@/composables/useCommandPalette";
import { computed, inject, ref } from "vue";

const uiConfig = inject("uiConfig");
const { open: openCommandPalette } = useCommandPalette();
const isMac = navigator.platform.toUpperCase().indexOf("MAC") >= 0;

const textColor = computed(() => {
  return uiConfig.topBar.textColor ? `${uiConfig.topBar.textColor} !important` : null;
});

const backgroundColor = computed(() => {
  return uiConfig.topBar.backgroundColor || "#343a40";
});
</script>

<style scoped lang="scss">
@use '/src/styles/global.scss';

.navbar-brand-wrapper {
  flex-shrink: 0;
}

.navbar-brand {
  padding-top: 0.6rem;
  padding-bottom: 0.6rem;
  font-size: 1.1rem;
}

.navbar-brand .title {
  align-items: center;
  display: flex;
  font-weight: 300;
}

.navbar .form-control {
  padding: 0.75rem 1rem;
  border-width: 0;
  border-radius: 0;
}

.logo {
  margin-left: 0.5rem;
  width: 1.9rem;
  height: 1.9rem;
}

.search-trigger {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  padding: 0.35rem 0.6rem;
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: rgba(255, 255, 255, 0.15);
    border-color: rgba(255, 255, 255, 0.3);
    color: rgba(255, 255, 255, 0.9);
  }

  .pi-search {
    font-size: 0.85rem;
  }

  .search-placeholder {
    opacity: 0.8;
  }

  .search-hotkey {
    background: rgba(255, 255, 255, 0.15);
    border: none;
    border-radius: 4px;
    padding: 0.15rem 0.4rem;
    font-size: 0.75rem;
    font-family: inherit;
    color: inherit;
  }
}
</style>
