<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

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
    <ScrollTop target="window" :threshold="10" icon="pi pi-arrow-up" />
  </div>
</template>

<script setup>
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar";
import Message from "primevue/message";
import PageHeader from "@/components/PageHeader.vue";
import PropertySet from "@/components/PropertySet.vue";
import usePropertySets from "@/composables/usePropertySets";
import { computed, onBeforeMount } from "vue";
import ScrollTop from "primevue/scrolltop";

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
