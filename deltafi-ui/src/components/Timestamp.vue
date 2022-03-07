<template>
  <span>{{ output }}</span>
</template>

<script setup>
import useUiConfig from "@/composables/useUiConfig";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, defineProps, toRef } from "vue";

const { formatTimestamp } = useUtilFunctions();
const { uiConfig } = useUiConfig();

const props = defineProps({
  timestamp: {
    type: [Date, String],
    required: true,
  },
  format: {
    type: String,
    required: false,
    default: "YYYY-MM-DD HH:mm:ss.SSS"
  }
});

const timestamp = toRef(props, 'timestamp');
const format = toRef(props, 'format');

const output = computed(() => {
  let adjustFormat = (uiConfig.value.useUTC) ? format.value + '[Z]' : format.value
  return formatTimestamp(timestamp.value, adjustFormat)
});
</script>