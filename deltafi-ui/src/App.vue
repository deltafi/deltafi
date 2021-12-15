<template>
  <div>
    <app-top-bar />
    <div class="container-fluid">
      <div class="row">
        <div :class="sidebarClasses">
          <app-menu />
        </div>
        <div role="main" class="col">
          <router-view />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import AppTopBar from "@/AppTopBar";
import AppMenu from "@/AppMenu";
import { mapState } from "vuex";

export default {
  name: "App",
  components: {
    AppTopBar: AppTopBar,
    AppMenu: AppMenu,
  },
  computed: {
    sidebarClasses() {
      return this.sidebarHidden ? "col sidebar hidden" : "col sidebar";
    },
    ...mapState(["uiConfig", "sidebarHidden"]),
  },
  watch: {
    $route: {
      immediate: true,
      handler(to) {
        this.setPageTitle(to.name);
      },
    },
    uiConfig: {
      deep: true,
      handler() {
        this.setPageTitle(this.$route.name);
      },
    },
  },
  beforeCreate() {
    this.$store.dispatch("fetchUIConfig");
  },
  methods: {
    setPageTitle(prefix) {
      const pageTitle = [prefix, this.uiConfig.title]
        .filter((n) => n)
        .join(" - ");
      document.title = pageTitle;
    },
  },
};
</script>

<style scoped lang="scss">
  @import "@/styles/app.scss";
</style>