<template>
  <Dialog v-bind="$attrs" :header="header" :style="{ width: '75vw' }" :maximizable="true" :modal="true" :dismissable-mask="true">
    <div class="error-viewer">
      <div class="error-row">
        <div class="error-col">
          <strong>Action Created</strong>
          <p>{{ action.created }}</p>
        </div>
        <div class="error-col">
          <strong>Action Modified</strong>
          <p>{{ action.modified }}</p>
        </div>
        <div class="error-col">
          <strong>Action State</strong>
          <p>{{ action.state }}</p>
        </div>
      </div>
      <div class="error-row">
        <div class="error-col">
          <strong>Error Cause</strong>
          <HighlightedCode :highlight="false" :code="action.errorCause" />
        </div>
      </div>
      <div class="error-row">
        <div class="error-col">
          <strong>Error Context</strong>
          <HighlightedCode :highlight="false" :code="action.errorContext" />
        </div>
      </div>
    </div>
  </Dialog>
</template>

<script>
import Dialog from "primevue/dialog";
import HighlightedCode from "@/components/HighlightedCode.vue";
import { computed } from "vue";

export default {
  name: "CollapsiblePanel",
  components: {
    Dialog,
    HighlightedCode,
  },
  props: {
    action: {
      type: Object,
      required: true,
    },
  },
  setup(props) {
    const header = computed(() => {
      return `${props.action.name} Error`;
    });

    return { header };
  },
};
</script>

<style lang="scss">
@import "@/styles/components/error-viewer.scss";
</style>