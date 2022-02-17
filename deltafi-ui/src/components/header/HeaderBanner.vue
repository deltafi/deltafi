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
import useUiConfig from "@/composables/useUiConfig";
import { computed } from "vue";

const { uiConfig } = useUiConfig();

const textColor = computed(() => {
  return uiConfig.value.securityBanner.textColor;
});

const backgroundColor = computed(() => {
  return uiConfig.value.securityBanner.backgroundColor;
});

const bannerMessage = computed(() => {
  return uiConfig.value.securityBanner.text;
});

const bannerEnabled = computed(() => {
  return uiConfig.value.securityBanner.enabled;
});
</script>

<style scoped lang="scss">
@import "@/styles/components/header/header-banner.scss";
</style>