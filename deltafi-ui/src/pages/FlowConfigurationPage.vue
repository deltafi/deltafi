<template>
  <div>
    <PageHeader heading="Flow Configuration" />
    <span v-if="hasErrors">
      <Message v-for="error in errors" :key="error" :closable="false" severity="error">{{ error }}</Message>
    </span>
    <HighlightedCode v-else-if="flowConfigData" language="yaml" :code="flowConfigData" />
    <ProgressBar v-else mode="indeterminate" style="height: 0.5em" />
  </div>
</template>

<script setup>
import ProgressBar from "primevue/progressbar";
import Message from "primevue/message";
import PageHeader from "@/components/PageHeader.vue";
import HighlightedCode from "@/components/HighlightedCode.vue";
import useFlowConfiguration from "@/composables/useFlowConfiguration";
import { onMounted, computed } from "vue";

const { data: flowConfigData, fetch: fetchFlowConfiguration, errors } = useFlowConfiguration();

const hasErrors = computed(() => {
  return errors.value.length > 0;
});

onMounted(() => {
  fetchFlowConfiguration();
});
</script>