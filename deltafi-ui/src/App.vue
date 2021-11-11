<template>
  <div class="app">
    <AppTopBar />
    <div class="container-fluid">
      <div class="row">
        <div id="sidebarMenu">
          <AppMenu />
        </div>
        <main role="main" class="col-md-9 ml-sm-auto col-lg-10 px-md-4">
          <router-view />
        </main>
      </div>
    </div>
  </div>
</template>

<script>
import AppTopBar from "./AppTopBar.vue";
import AppMenu from "./AppMenu.vue";
import { mapState } from "vuex";

export default {
  name: "App",
  components: {
    AppTopBar: AppTopBar,
    AppMenu: AppMenu,
  },
  computed: mapState(["uiConfig"]),
  watch: {
    $route: {
      immediate: true,
      handler(to) {
        this.setPageTitle(to.meta.title);
      },
    },
    uiConfig: {
      deep: true,
      handler() {
        this.setPageTitle(this.$route.meta.title);
      }
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
