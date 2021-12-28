<template>
  <div>
    <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3 border-bottom">
      <h1 class="h2">
        Flow Configuration
      </h1>
    </div>
    <highlightjs :code="flowConfigData" language="yaml" />
  </div>
</template>

<script>
import GraphQLService from "@/service/GraphQLService";
import hljsVuePlugin from "@highlightjs/vue-plugin";
import "highlight.js/lib/common";
import "highlight.js/styles/lioshi.css";

export default {
  name: "FlowConfigurationPage",
  components: {
    highlightjs: hljsVuePlugin.component,
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