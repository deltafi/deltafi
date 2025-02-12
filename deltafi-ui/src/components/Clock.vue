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
  <span class="clock">
    <Transition>
      <span v-if="copied" class="mr-1">Copied!</span>
    </Transition>
    <Tag v-tooltip.left="tooltip" icon="pi pi-clock" :value="time" @contextmenu.prevent="onRightClick" @click.prevent="onClick" />
  </span>
</template>

<script setup>
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, ref } from "vue";
import useUiConfig from "@/composables/useUiConfig";
import Tag from 'primevue/tag';
import { useClipboard } from '@vueuse/core'

const { shortTimezone, formatTimestamp } = useUtilFunctions();
const { uiConfig, setUiConfig } = useUiConfig();

const now = ref(new Date());
const localTimezone = new Date().toLocaleString('en', { timeZoneName: 'short' }).split(' ').pop()

const time = computed(() => {
  return formatTimestamp(now.value, `YYYY-MM-DD HH:mm [${shortTimezone()}]`)
});

const fullTimestamp = computed(() => {
  return formatTimestamp(now.value, `YYYY-MM-DD HH:mm:ss.SSS [${shortTimezone()}]`)
});

const { copy, copied } = useClipboard({ time });

const tooltip = computed(() => {
  return `${fullTimestamp.value}\n\nLeft-click to copy\n\nRight-click to toggle ${localTimezone}/UTC`
});

const toggleUTC = () => {
  setUiConfig({
    useUTC: !uiConfig.useUTC
  })
};

const onClick = () => {
  copy(fullTimestamp.value);
};

const onRightClick = () => {
  toggleUTC();
};

setInterval(() => {
  now.value = new Date();
}, 1000);
</script>

<style scoped>
.clock {
  color: var(--gray-300);

  .p-tag {
    cursor: pointer;
    background-color: var(--gray-700);
    font-weight: 700;
  }

  .v-enter-active,
  .v-leave-active {
    transition: opacity 0.3s ease;
  }

  .v-enter-from,
  .v-leave-to {
    opacity: 0;
  }
}
</style>
