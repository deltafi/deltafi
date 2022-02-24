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
      <div v-if="action.errorCause" class="error-row">
        <div class="error-col">
          <strong>Cause</strong>
          <HighlightedCode :highlight="false" :code="action.errorCause" />
        </div>
      </div>
      <div v-if="action.errorContext" class="error-row">
        <div class="error-col">
          <strong>Context</strong>
          <HighlightedCode :highlight="false" :code="action.errorContext" />
        </div>
      </div>
    </div>
  </Dialog>
</template>

<script setup>
import Dialog from "primevue/dialog";
import HighlightedCode from "@/components/HighlightedCode.vue";
import { computed, defineProps } from "vue";

const props = defineProps({
  action: {
    type: Object,
    required: true,
  },
});

const header = computed(() => {
  return props.action.name;
});
</script>

<style lang="scss">
@import "@/styles/components/error-viewer.scss";
</style>