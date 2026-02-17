<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
      <ContextualNavBar />
    </nav>
    <div role="main" class="content-wrapper-full">
      <router-view />
    </div>
    <FooterBanner />
    <CustomToast position="bottom-right" />
    <Dialog v-model:visible="idle" header="Are you still there?" :modal="true" :style="{ width: '25vw' }">
      <p>All background network activity has been paused due to user inactivity. Move the mouse to resume.</p>
    </Dialog>
    <Dialog v-model:visible="showMotd" header="Message of the Day" :modal="true" :closable="false" :style="{ width: '30vw', minWidth: '300px' }">
      <div v-html="renderMarkdown(uiConfig.messageOfTheDay || '')" />
      <template #footer>
        <Button label="Acknowledge" @click="dismissMotd" />
      </template>
    </Dialog>
    <CommandPalette />
  </div>
</template>

<script setup>
import HeaderBanner from "@/components/header/HeaderBanner.vue";
import AppTopBar from "@/components/header/AppTopBar.vue";
import ContextualNavBar from "@/components/header/ContextualNavBar.vue";
import CommandPalette from "@/components/header/CommandPalette.vue";
import FooterBanner from "@/components/footer/FooterBanner.vue";
import CustomToast from "@/components/CustomToast.vue";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import useCommandPalette from "@/composables/useCommandPalette";
import { useRoute, useRouter } from "vue-router";
import useUiConfig from "@/composables/useUiConfig";
import useMotd from "@/composables/useMotd";
import useCurrentUser from "@/composables/useCurrentUser";
import useVersion from '@/composables/useVersion';
import useServerSentEvents from "@/composables/useServerSentEvents";
import useNotifications from "@/composables/useNotifications";
import { computed, onBeforeMount, watch, provide, onMounted, onUnmounted } from "vue";
import { useTitle, useIdle } from "@vueuse/core";
import { usePrimeVue } from "primevue/config";
import MarkdownIt from "markdown-it";

const markdownIt = new MarkdownIt();
const renderMarkdown = (source) => markdownIt.render(source);

const primevue = usePrimeVue();
const route = useRoute();
const router = useRouter();
const title = useTitle("DeltaFi");
const notify = useNotifications();
const { serverSentEvents } = useServerSentEvents();
const { uiConfig } = useUiConfig();
const messageOfTheDay = computed(() => uiConfig.messageOfTheDay);
const { showMotd, dismiss: dismissMotd } = useMotd(messageOfTheDay);
const { fetchCurrentUser } = useCurrentUser();
const { fetchVersion } = useVersion();

const idleTimeOut = (15 * 60 * 1000); // 15 min
const { idle } = useIdle(idleTimeOut);
provide("isIdle", idle);

const { toggle: toggleCommandPalette, open: openCommandPalette, visible: commandPaletteVisible } = useCommandPalette();

// Global keyboard shortcuts (Cmd on Mac, Ctrl on Windows/Linux)
const globalShortcuts = {
  k: () => toggleCommandPalette(),
  d: () => router.push("/"),
  s: () => router.push("/deltafile/search"),
  e: () => router.push("/errors"),
  u: () => router.push("/deltafile/upload/"),
  m: () => router.push("/system-map"),
  p: () => router.push("/config/plugins"),
  t: () => router.push("/config/transforms"),
};

const handleGlobalKeydown = (e) => {
  const target = e.target;
  const isTyping = target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT" || target.isContentEditable;

  // "/" opens command palette (no modifier needed, but skip if typing)
  if (e.key === "/" && !isTyping && !commandPaletteVisible.value) {
    e.preventDefault();
    openCommandPalette();
    return;
  }

  // Cmd/Ctrl + key combinations
  if (!e.metaKey && !e.ctrlKey) return;

  const handler = globalShortcuts[e.key.toLowerCase()];
  if (!handler) return;

  // Skip Cmd+K if palette is already open (let the palette handle it)
  if (e.key.toLowerCase() === "k" && commandPaletteVisible.value) {
    return;
  }

  // Skip if user is typing in an input field (except for command palette which should always work)
  if (isTyping && e.key.toLowerCase() !== "k") {
    return;
  }

  e.preventDefault();
  handler();
};

onBeforeMount(async () => {
  fetchVersion();
  provide("uiConfig", uiConfig);
  await fetchCurrentUser();
});

onMounted(async () => {
  primevue.config.locale.dateFormat = "yy-mm-dd";
  document.addEventListener("keydown", handleGlobalKeydown);
});

onUnmounted(() => {
  document.removeEventListener("keydown", handleGlobalKeydown);
});

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
