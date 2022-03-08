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

<style lang="scss">
@import "@/styles/components/footer/footer-banner.scss";
</style>