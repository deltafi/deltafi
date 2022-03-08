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

<script setup>
import HeaderBanner from "@/components/header/HeaderBanner";
import AppTopBar from "@/components/header/AppTopBar";
import AppMenu from "@/components/sidebar/AppMenu";
import FooterBanner from "@/components/footer/FooterBanner";
import Toast from "primevue/toast";
import { useRoute } from "vue-router";
import useUiConfig from "@/composables/useUiConfig";
import useServerSentEvents from "@/composables/useServerSentEvents";
import useNotifications from "@/composables/useNotifications";
import { computed, onBeforeMount, watch, nextTick, onMounted, onBeforeUnmount, provide, ref } from "vue";
import { useTitle } from "@vueuse/core";
import { usePrimeVue } from "primevue/config";

const primevue = usePrimeVue();
const route = useRoute();
const title = useTitle("DeltaFi");
const notify = useNotifications();
const { serverSentEvents } = useServerSentEvents();
const { uiConfig, fetchUiConfig } = useUiConfig();

const sidebarHidden = ref(false);
provide('sidebarHidden', sidebarHidden);

const sidebarClasses = computed(() => {
  return sidebarHidden.value ? "col sidebar hidden" : "col sidebar";
});

onBeforeMount(() => {
  fetchUiConfig();
  provide('uiConfig', uiConfig);
});

onMounted(async () => {
  primevue.config.locale.dateFormat = 'yy-mm-dd';
  await nextTick();
  window.addEventListener("resize", onResize);
  onResize();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", onResize);
});

const onResize = () => {
  var windowWidth = window.innerWidth;

  if (!sidebarHidden.value && windowWidth < 1024) {
    sidebarHidden.value = true
  }
};

const setPageTitle = () => {
  title.value = [route.name, uiConfig.title].filter((n) => n).join(" - ");
};

watch(
  () => route.name,
  () => {
    setPageTitle();
  }
);
watch(
  () => uiConfig.title,
  () => {
    setPageTitle();
  }
);

serverSentEvents.addEventListener('announcement', (event) => {
  notify.info("System Announcement", event.data, 0);
});
</script>

<style scoped lang="scss">
@import "@/styles/app.scss";
</style>