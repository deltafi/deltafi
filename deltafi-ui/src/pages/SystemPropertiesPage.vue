<template>
  <div class="SystemProperties">
    <PageHeader heading="System Properties" />
    <span v-if="hasErrors">
      <Message v-for="error in errors" :key="error" :closable="false" severity="error">{{ error }}</Message>
    </span>
    <span v-else-if="propertySets">
      <span v-for="propertySet in propertySets" :key="propertySet.id">
        <PropertySet :prop-set="propertySet" @updated="fetchPropertySets" />
      </span>
    </span>
    <ProgressBar v-else mode="indeterminate" style="height: 0.5em" />
  </div>
</template> 
 
<script setup>
import ProgressBar from "primevue/progressbar";
import Message from "primevue/message";
import PageHeader from "@/components/PageHeader.vue";
import PropertySet from "@/components/PropertySet.vue";
import usePropertySets from "@/composables/usePropertySets";
import { computed, onBeforeMount } from "vue";

const { data: propertySets, fetch: fetchPropertySets, errors } = usePropertySets();

const hasErrors = computed(() => {
  return errors.value.length > 0;
});

onBeforeMount(() => {
  fetchPropertySets();
});
</script>

<style lang="scss">
@import "@/styles/pages/system-properties-page.scss";
</style>