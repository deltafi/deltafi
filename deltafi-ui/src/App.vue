<template>
  <div>
    <nav class="sticky-top">
      <HeaderBanner />
      <AppTopBar />
    </nav>
    <div class="wrapper">
      <div :class="sidebarClasses">
        <AppMenu />
      </div>
      <div role="main" class="content-wrapper">
        <router-view />
      </div>
    </div>
    <FooterBanner />
    <Toast position="bottom-right" />
  </div>
</template>

<script>
import HeaderBanner from "@/components/header/HeaderBanner";
import AppTopBar from "@/components/header/AppTopBar";
import AppMenu from "@/components/sidebar/AppMenu";
import FooterBanner from "@/components/footer/FooterBanner";
import { mapState } from "vuex";
import Toast from "primevue/toast";

import { useStore } from '@/store';
import { UIConfigActionTypes } from '@/store/modules/uiConfig/action-types';
import { SidebarToggleActionTypes } from '@/store/modules/sidebarToggle/action-types';

export default {
  name: "App",
  components: {
    HeaderBanner,
    AppTopBar,
    AppMenu,
    FooterBanner,
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
  mounted() {
    this.$nextTick(() => {
      window.addEventListener('resize', this.onResize);
    })
  },
  beforeCreate() {
    const store = useStore();
    store.dispatch(UIConfigActionTypes.FETCH_UI_CONFIG);
  },
  beforeUnmount() { 
    window.removeEventListener('resize', this.onResize); 
  },
  methods: {
    setPageTitle(prefix) {
      const pageTitle = [prefix, this.uiConfig.title]
        .filter((n) => n)
        .join(" - ");
      document.title = pageTitle;
    },
    onResize() {
      var windowWidth = window.innerWidth

      if (!this.sidebarHidden && windowWidth < 768) {
        const store = useStore();
        store.dispatch(SidebarToggleActionTypes.TOGGLE_SIDEBAR);
      }
    }
  },
};
</script>

<style scoped lang="scss">
  @import "@/styles/app.scss";
</style>