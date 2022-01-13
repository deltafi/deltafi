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
    <Toast position="bottom-right" />
  </div>
</template>

<script>
import AppTopBar from "@/AppTopBar";
import AppMenu from "@/AppMenu";
import { mapState } from "vuex";
import Toast from "primevue/toast";

import { useStore } from '@/store';
import { UIConfigActionTypes } from '@/store/modules/uiConfig/action-types';

export default {
  name: "App",
  components: {
    AppTopBar,
    AppMenu,
    Toast
  },
  computed: {
    sidebarClasses() {
      return this.sidebarHidden ? "col sidebar hidden" : "col sidebar";
    },
    ...mapState({
      uiConfig: state => state.uiConfig.uiConfig,
      sidebarHidden: state => state.sidebarToggle.sidebarHidden
    })
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
    const store = useStore();
    store.dispatch(UIConfigActionTypes.FETCH_UI_CONFIG);
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