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
import Toast from "primevue/toast";
import { useRoute } from "vue-router";
import useUiConfig from "@/composables/useUiConfig";
import useSidebarToggle from "@/composables/useSidebarToggle";
import { computed, onBeforeMount, watch, nextTick, onMounted, onBeforeUnmount } from "vue";

export default {
  name: "App",
  components: {
    HeaderBanner,
    AppTopBar,
    AppMenu,
    FooterBanner,
    Toast,
  },
  setup() {
    const route = useRoute();
    const { uiConfig, fetchUiConfig } = useUiConfig();
    const { sidebarHidden, toggleSidebarHidden } = useSidebarToggle();

    const sidebarClasses = computed(() => {
      return sidebarHidden.value ? "col sidebar hidden" : "col sidebar";
    });

    onBeforeMount(() => {
      fetchUiConfig();
    });

    onMounted(async () => {
      await nextTick();
      window.addEventListener("resize", onResize);
    });

    onBeforeUnmount(() => {
      window.removeEventListener("resize", onResize);
    });

    const onResize = () => {
      var windowWidth = window.innerWidth;

      if (!sidebarHidden.value && windowWidth < 768) {
        toggleSidebarHidden();
      }
    };

    const setPageTitle = () => {
      const pageTitle = [route.name, uiConfig.value.title].filter((n) => n).join(" - ");
      document.title = pageTitle;
    };

    watch(
      () => route.name,
      () => {
        setPageTitle();
      }
    );
    watch(
      () => uiConfig.value.title,
      () => {
        setPageTitle();
      }
    );

    return {
      uiConfig,
      sidebarHidden,
      toggleSidebarHidden,
      sidebarClasses,
    };
  },
};
</script>

<style scoped lang="scss">
@import "@/styles/app.scss";
</style>