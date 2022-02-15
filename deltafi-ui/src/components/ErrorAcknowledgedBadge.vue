<template>
  <i v-tooltip.right="tooltip" :class="classes" />
</template>

<script>
import { computed } from "vue";
export default {
  name: "ErrorAcknowledgedBadge",
  components: {},
  props: {
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
  },
  setup(props) {
    const classes = computed(() => {
      return `ack-icon ${props.icon}`;
    });

    const tooltip = computed(() => {
      return `Error Acknowledged: ${props.timestamp}\n\n${props.reason}`;
    });

    return { classes, tooltip };
  },
};
</script>

<style scoped>
.ack-icon {
  color: var(--red);
}
</style>