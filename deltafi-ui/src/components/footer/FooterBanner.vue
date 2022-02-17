<template>
  <footer v-if="bannerEnabled" :style="{ backgroundColor: backgroundColor, color: textColor }" class="sticky-footer">
    <div class="container my-auto">
      <div class="text-center my-auto">
        <span>{{ bannerMessage }}</span>
      </div>
    </div>
  </footer>
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

<style lang="scss">
@import "@/styles/components/footer/footer-banner.scss";
</style>