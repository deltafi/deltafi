<template>
  <nav v-if="bannerEnabled" :style="{ backgroundColor: backgroundColor, color: textColor }" class="navbar p-0 banner-height">
    <div class="container navbar-content-center">
      <div class="text-center">
        <span>{{ bannerMessage }}</span>
      </div>
    </div>
  </nav>
</template>

<script>
import { mapState } from "vuex";

export default {
  name: "HeaderBanner",
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

<style scoped lang="scss">
@import "@/styles/components/header/header-banner.scss";
</style>