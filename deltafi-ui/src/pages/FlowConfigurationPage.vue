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

<script>
import HighlightedCode from "@/components/HighlightedCode.vue";
import ProgressBar from "primevue/progressbar";
import Message from "primevue/message";
import useFlowConfiguration from "@/composables/useFlowConfiguration";
import { onMounted, computed } from "vue";

export default {
  name: "FlowConfigurationPage",
  components: {
    HighlightedCode,
    ProgressBar,
    Message,
  },
  setup() {
    const { data: flowConfigData, fetch: fetchFlowConfiguration, errors } = useFlowConfiguration();

    const hasErrors = computed(() => {
      return errors.value.length > 0;
    });

    onMounted(() => {
      fetchFlowConfiguration();
    });

    return { flowConfigData, hasErrors, errors };
  },
};
</script>