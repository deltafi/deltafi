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

<template>
  <nav class="navbar navbar-dark bg-dark p-0 shadow" :style="{ backgroundColor: backgroundColor, color: textColor }">
    <span class="navbar-brand-wrapper">
      <div class="navbar-brand col mr-0 px-3">
        <div class="row">
          <img class="logo" src="/logo.png">
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
      <Version style="font-size: 0.9rem" />
    </span>
    <span>
      <Clock class="mr-3" />
      <NotificationBadge v-if="$hasPermission('EventRead')" class="mr-3" />
      <StatusBadge class="mr-3" />
      <UserBadge class="mr-3" />
    </span>
  </nav>
</template>

<script setup>
import Clock from "@/components/Clock.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import NotificationBadge from "@/components/NotificationBadge.vue";
import UserBadge from "@/components/UserBadge.vue";
import Version from "@/components/Version.vue";
import { computed, inject } from "vue";

const uiConfig = inject("uiConfig");
const sidebarHidden = inject("sidebarHidden");

const textColor = computed(() => {
  return uiConfig.topBar.textColor ? `${uiConfig.topBar.textColor} !important` : null;
});

const backgroundColor = computed(() => {
  return uiConfig.topBar.backgroundColor ? `${uiConfig.topBar.backgroundColor} !important` : null;
});

const toggleSidebarIcon = computed(() => {
  return sidebarHidden.value ? "pi pi-angle-double-right" : "pi pi-angle-double-left";
});

const toggleSidebar = () => (sidebarHidden.value = !sidebarHidden.value);
</script>

<style scoped lang="scss">
@use '/src/styles/global.scss';

.navbar-brand-wrapper {
  width: 300px;
}

.navbar-brand {
  padding-top: 0.6rem;
  padding-bottom: 0.6rem;
  font-size: 1.1rem;
  background-color: rgba(0, 0, 0, 0.25);
  box-shadow: inset -1px 0 0 rgba(0, 0, 0, 0.25);
  -ms-flex: 0 0 300px;
  flex: 0 0 300px;
}

.navbar-brand .title {
  align-items: center;
  display: flex;
  font-weight: 300;
}

.navbar-brand .navbar-toggler:focus {
  outline: none;
  box-shadow: none;
}

.navbar .form-control {
  padding: 0.75rem 1rem;
  border-width: 0;
  border-radius: 0;
}

@media (max-width: 768px) {
  .navbar-brand {
    padding-top: 0.6rem;
    padding-bottom: 0.6rem;
    font-size: 1.1rem;
    background-color: rgba(0, 0, 0, 0.25);
    box-shadow: inset -1px 0 0 rgba(0, 0, 0, 0.25);
    -ms-flex: 0 0 150px;
    flex: 0 0 150px;
  }
}

.logo {
  margin-left: 1rem;
  width: 1.9rem;
  height: 1.9rem;
}

.navbar-toggler {
  color: hsla(0, 0%, 100%, .5) !important;
  border-color: hsla(0, 0%, 100%, .1) !important;

  &:hover,
  &:focus,
  &:active {
    outline: none !important;
    box-shadow: none !important;
    color: hsla(0, 0%, 100%, .5) !important;
    border-color: hsla(0, 0%, 100%, .1) !important;
    /* Styles here */
  }
}
</style>
