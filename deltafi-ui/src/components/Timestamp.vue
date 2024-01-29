<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
  <span>
    <span v-if="timestamp === null || timestamp === undefined">-</span>
    <span v-else-if="showTimeAgo" v-tooltip.top="time">{{ timeAgo }}</span>
    <span v-else v-tooltip.top="timeAgo">{{ time }}</span>
  </span>
</template>

<script setup>
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, defineProps, toRef, inject } from "vue";
import { useTimeAgo } from '@vueuse/core'

const { formatTimestamp } = useUtilFunctions();
const uiConfig = inject('uiConfig');

const props = defineProps({
  timestamp: {
    type: [Date, String, Number, null],
    required: true,
  },
  format: {
    type: String,
    required: false,
    default: "YYYY-MM-DD HH:mm:ss.SSS"
  },
  showTimeAgo: {
    type: Boolean,
    required: false,
    default: false
  }
});

const timestamp = toRef(props, 'timestamp');
const format = toRef(props, 'format');
const showTimeAgo = toRef(props, 'showTimeAgo');

const time = computed(() => {
  let adjustFormat = (uiConfig.useUTC) ? format.value + '[Z]' : format.value
  return formatTimestamp(timestamp.value, adjustFormat)
});

const timeAgo = computed(() => {
  return useTimeAgo(timestamp.value).value
})
</script>
