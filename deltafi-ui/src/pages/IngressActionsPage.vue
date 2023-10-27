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
  <div>
    <PageHeader heading="Ingress Actions" />
    <TimedIngressActionsPanel ref="timedIngressActionsPanel" />
  </div>
</template>

<script setup>
import { ref, inject, onMounted, onUnmounted } from "vue";
import PageHeader from "@/components/PageHeader.vue";
import TimedIngressActionsPanel from "@/components/ingressActions/TimedIngressActionsPanel.vue";

const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const timedIngressActionsPanel = ref(null)
let autoRefresh;

const refresh = async () => {
  timedIngressActionsPanel.value.refresh();
}

onMounted(() => {
  autoRefresh = setInterval(() => {
    if (!isIdle.value) {
      refresh();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});
</script>
