<template>
  <span>
    <span v-if="showTimeAgo" v-tooltip.top="time">{{ timeAgo }}</span>
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
    type: [Date, String],
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