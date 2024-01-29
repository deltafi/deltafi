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
    <PageHeader heading="Ingress Actions">
      <div class="mb-2">
          <DialogTemplate component-name="ingressActions/IngressActionConfigurationDialog" header="Add New Ingress Action" dialog-width="50vw" @reload-ingress-action="refresh">
            <Button v-has-permission:DeletePolicyCreate label="Add Action" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
          </DialogTemplate>
      </div>
    </PageHeader>
    <TimedIngressActionsPanel ref="timedIngressActionsPanel" @ingress-actions-list="exportableIngressActions"/>
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import TimedIngressActionsPanel from "@/components/ingressActions/TimedIngressActionsPanel.vue";
import { ref, inject, onMounted, provide, onUnmounted } from "vue";

import Button from "primevue/button";

const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const timedIngressActionsPanel = ref(null);
const editing = ref(false);
provide("isEditing", editing );
let autoRefresh;

const refresh = async () => {
  timedIngressActionsPanel.value.refresh();
};

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

const ingressActionsList = ref(null);

const exportableIngressActions = (value) => {
  ingressActionsList.value = value;
};
</script>
