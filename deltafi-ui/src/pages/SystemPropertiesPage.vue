<template>
  <div class="SystemProperties">
    <PageHeader heading="System Properties" />
    <ProgressBar v-if="!loaded" mode="indeterminate" style="height: 0.5em" />
    <span v-else>
      <span v-for="propertySet in propertySets" :key="propertySet.id">
        <PropertySet :prop-set="propertySet" @updated="fetchPropertySets" />
      </span>
    </span>
  </div>
</template> 
 
<script setup>
import ProgressBar from "primevue/progressbar";
import PageHeader from "@/components/PageHeader.vue";
import PropertySet from "@/components/PropertySet.vue";
import usePropertySets from "@/composables/usePropertySets";
import { onBeforeMount } from "vue";

const { loaded, data: propertySets, fetch: fetchPropertySets } = usePropertySets();

onBeforeMount(() => {
  fetchPropertySets();
});
</script>

<style lang="scss">
@import "@/styles/pages/system-properties-page.scss";
</style>