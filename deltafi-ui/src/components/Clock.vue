<template>
  <span class="clock">
    <Transition>
      <span v-if="copied" class="mr-1">Copied!</span>
    </Transition>
    <Tag v-tooltip.left="tooltip" icon="pi pi-clock" :value="time" @contextmenu.prevent="onRightClick" @click.prevent="onClick"></Tag>
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

<style scoped lang="scss">
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