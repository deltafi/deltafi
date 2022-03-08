<template>
  <nav v-if="bannerEnabled" :style="{ backgroundColor: backgroundColor, color: textColor }" class="navbar p-0 banner-height">
    <div class="container navbar-content-center">
      <div class="text-center">
        <span>{{ bannerMessage }}</span>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { computed, inject } from "vue";

const uiConfig = inject('uiConfig');

const textColor = computed(() => {
  return uiConfig.securityBanner.textColor;
});

const backgroundColor = computed(() => {
  return uiConfig.securityBanner.backgroundColor;
});

const bannerMessage = computed(() => {
  return uiConfig.securityBanner.text;
});

const bannerEnabled = computed(() => {
  return uiConfig.securityBanner.enabled;
});
</script>

<style scoped lang="scss">
@import "@/styles/components/header/header-banner.scss";
</style>