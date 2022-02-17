<template>
  <i v-tooltip.right="tooltip" :class="classes" />
</template>

<script setup>
import { computed, defineProps } from "vue";

const props = defineProps({
  reason: {
    type: String,
    required: true,
  },
  timestamp: {
    type: String,
    required: true,
  },
  icon: {
    type: String,
    required: false,
    default: "far fa-check-circle",
  },
});

const classes = computed(() => {
  return `ack-icon ${props.icon}`;
});

const tooltip = computed(() => {
  return `Error Acknowledged: ${props.timestamp}\n\n${props.reason}`;
});
</script>

<style scoped>
.ack-icon {
  color: var(--red);
}
</style>