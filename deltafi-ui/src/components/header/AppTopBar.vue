<template>
  <nav class="navbar navbar-dark bg-dark p-0 shadow">
    <div class="navbar-brand col mr-0 px-3">
      <div class="row">
        <div class="col title">{{ uiConfig.title }}</div>
        <div class="col text-right">
          <button v-tooltip.right="'Toggle sidebar menu'" class="navbar-toggler btn btn-sm" @click="toggleSidebar">
            <i :class="toggleSidebarIcon" />
          </button>
        </div>
      </div>
    </div>
    <span>
      <Clock class="mr-3" />
      <StatusBadge />
    </span>
  </nav>
</template>

<script setup>
import Clock from "@/components/Clock";
import StatusBadge from "@/components/StatusBadge";
import { computed, inject } from "vue";

const uiConfig = inject('uiConfig');
const sidebarHidden = inject('sidebarHidden');

const toggleSidebarIcon = computed(() => {
  return sidebarHidden.value ? "pi pi-angle-double-right" : "pi pi-angle-double-left";
});

const toggleSidebar = () => sidebarHidden.value = !sidebarHidden.value
</script>

<style scoped lang="scss">
@import "@/styles/components/header/app-top-bar.scss";
</style>