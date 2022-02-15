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

<script>
import PropertySet from "@/components/PropertySet.vue";
import ProgressBar from "primevue/progressbar";
import usePropertySets from "@/composables/usePropertySets";
import { onBeforeMount } from "vue";

export default {
  name: "SystemPropertiesPage",
  components: {
    PropertySet,
    ProgressBar,
  },
  setup() {
    const { loading, loaded, data: propertySets, fetch: fetchPropertySets } = usePropertySets();

    onBeforeMount(() => {
      fetchPropertySets();
    });

    return {
      loaded,
      loading,
      propertySets,
      fetchPropertySets,
    };
  },
};
</script>

<style lang="scss">
@import "@/styles/pages/system-properties-page.scss";
</style>