<template>
  <i v-tooltip.right="tooltip" :class="classes" />
</template>

<script>
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
      default: "far fa-check-circle"
    }
  },
  computed: {
    classes() {
      return `ack-icon ${this.icon}`;
    },
    tooltip() {
      return `Error Acknowledged: ${this.timestamp}\n\n${this.reason}`;
    }
  }
};
</script>

<style scoped>
  .ack-icon {
    color: #dc3545;
  }
</style>