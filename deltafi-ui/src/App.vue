<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

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
    <CustomToast position="bottom-right" />
    <Dialog v-model:visible="idle" header="Are you still there?" :modal="true" :style="{ width: '25vw' }">
      <p>All background network activity has been paused due to user inactivity. Move the mouse to resume.</p>
    </Dialog>
  </div>
</template>

<script setup>
import HeaderBanner from "@/components/header/HeaderBanner.vue";
import AppTopBar from "@/components/header/AppTopBar.vue";
import AppMenu from "@/components/sidebar/AppMenu.vue";
import FooterBanner from "@/components/footer/FooterBanner.vue";
import CustomToast from "@/components/CustomToast.vue";
import Dialog from "primevue/dialog";
import { useRoute } from "vue-router";
import useUiConfig from "@/composables/useUiConfig";
import useCurrentUser from "@/composables/useCurrentUser";
import useVersion from '@/composables/useVersion';
import useServerSentEvents from "@/composables/useServerSentEvents";
import useNotifications from "@/composables/useNotifications";
import { computed, onBeforeMount, watch, nextTick, onBeforeUnmount, provide, ref, onMounted } from "vue";
import { useTitle, useIdle } from "@vueuse/core";
import { usePrimeVue } from "primevue/config";

const primevue = usePrimeVue();
const route = useRoute();
const title = useTitle("DeltaFi");
const notify = useNotifications();
const { serverSentEvents } = useServerSentEvents();
const { uiConfig } = useUiConfig();
const { fetchCurrentUser } = useCurrentUser();
const { fetchVersion } = useVersion();

const idleTimeOut = (15 * 60 * 1000); // 15 min
const { idle } = useIdle(idleTimeOut);
const sidebarHidden = ref(false);
provide("sidebarHidden", sidebarHidden);
provide("isIdle", idle);

const sidebarClasses = computed(() => {
  return sidebarHidden.value ? "col sidebar hidden" : "col sidebar";
});

onBeforeMount(async () => {
  fetchVersion();
  provide("uiConfig", uiConfig);
  await fetchCurrentUser();
});

onMounted(async () => {
  primevue.config.locale.dateFormat = "yy-mm-dd";

  // Resize related
  await nextTick()
  window.addEventListener("resize", onResize);
  onResize();
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", onResize);
});

const onResize = () => {
  const windowWidth = window.innerWidth;

  if (!sidebarHidden.value && windowWidth < 1024) {
    sidebarHidden.value = true;
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
serverSentEvents.addEventListener("announcement", (event) => {
  notify.info("System Announcement", event.data, 0);
});
</script>

<style scoped lang="scss">
@use "@/styles/app.scss";
</style>
