<template>
  <div>
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        Flow Configuration
      </h1>
    </div>
    <HighlightedCode language="yaml" :code="flowConfigData" />
  </div>
</template>

<script>
import GraphQLService from "@/service/GraphQLService";
import HighlightedCode from "@/components/HighlightedCode.vue";

export default {
  name: "FlowConfigurationPage",
  components: {
    HighlightedCode,
  },
  data() {
    return {
      flowConfigData: "",
    };
  },
  created() {
    this.graphQLService = new GraphQLService();
    this.loadFlowConfig();
  },
  methods: {
    async loadFlowConfig() {
      let response = await this.graphQLService.getFlowConfigYaml();
      this.flowConfigData = response.data.exportConfigAsYaml;
    },
  },
};
</script>