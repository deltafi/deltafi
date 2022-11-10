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
  <nav class="navbar navbar-dark bg-dark p-0 shadow" :style="{ backgroundColor: backgroundColor, color: textColor }">
    <span class="navbar-brand-wrapper">
      <div class="navbar-brand col mr-0 px-3">
        <div class="row">
          <div class="col title" :style="{ color: textColor }">{{ uiConfig.title }}</div>
          <div class="col text-right">
            <button v-tooltip.right="'Toggle sidebar menu'" class="navbar-toggler btn btn-sm" @click="toggleSidebar">
              <i :class="toggleSidebarIcon" />
            </button>
          </div>
        </div>
      </div>
    </span>
    <span class="navbar-text col">
      <Version style="font-size: 0.9rem;" />
    </span>
    <span>
      <Clock class="mr-3" />
      <StatusBadge class="mr-3" />
      <UserBadge class="mr-3" />
    </span>
  </nav>
</template>

<script setup>
import Clock from "@/components/Clock";
import StatusBadge from "@/components/StatusBadge";
import UserBadge from "@/components/UserBadge.vue";
import Version from "@/components/Version.vue";
import { computed, inject } from "vue";

const uiConfig = inject('uiConfig');
const sidebarHidden = inject('sidebarHidden');

const textColor = computed(() => {
  return (uiConfig.topBar.textColor) ? `${uiConfig.topBar.textColor} !important` : null;
});

const backgroundColor = computed(() => {
  return (uiConfig.topBar.backgroundColor) ? `${uiConfig.topBar.backgroundColor} !important` : null;
});

const toggleSidebarIcon = computed(() => {
  return sidebarHidden.value ? "pi pi-angle-double-right" : "pi pi-angle-double-left";
});

const toggleSidebar = () => sidebarHidden.value = !sidebarHidden.value
</script>

<style scoped lang="scss">
@import "@/styles/components/header/app-top-bar.scss";
</style>