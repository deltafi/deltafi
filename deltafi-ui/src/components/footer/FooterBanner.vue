<template>
  <footer v-if="bannerEnabled" :style="{ backgroundColor: backgroundColor, color: textColor }" class="sticky-footer">
    <div class="container my-auto">
      <div class="text-center my-auto">
        <span>{{ bannerMessage }}</span>
      </div>
    </div>
  </footer>
</template>

<script>
import { mapState } from "vuex";

export default {
  name: "AppFooter",
  computed: {
    textColor() {
      return this.uiConfig.securityBanner.textColor;
    },
    backgroundColor() {
      return this.uiConfig.securityBanner.backgroundColor;
    },
    bannerMessage() {
      return this.uiConfig.securityBanner.text;
    },
    bannerEnabled() {
      return this.uiConfig.securityBanner.enabled;
    },
    ...mapState({
      uiConfig: (state) => state.uiConfig.uiConfig,
    }),
  },
};
</script>

<style lang="scss">
@import "@/styles/components/footer/footer-banner.scss";
</style>