<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div>
    <PageHeader :heading="heading" />
    <p>{{ message }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import PageHeader from "@/components/PageHeader.vue";

const route = useRoute();
const service = computed(() => route.query.service);

const heading = computed(() => {
  return service.value
      ? `${ service.value } Unavailable`
      : 'Page Not Found';
});

const message = computed(() => {
  return service.value
      ? `The ${ service.value } service is currently offline or being updated. Please try again in a few moments.`
      : 'The page you are looking for cannot be found.';
});

</script>