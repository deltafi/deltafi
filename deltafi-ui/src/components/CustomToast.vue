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
  <Toast>
    <template #message="slotProps">
      <span :class="iconClass(slotProps.message.severity)" />
      <div class="p-toast-message-text">
        <span class="p-toast-summary">{{ slotProps.message.summary }}</span>
        <div class="p-toast-detail">
          <div v-html="formatString(slotProps.message.detail)" />
        </div>
      </div>
    </template>
  </Toast>
</template>

<script setup>
import Toast from "primevue/toast";
import { useToast } from "primevue/usetoast";
import { onMounted, watch } from "vue";
import useNotifications from "@/composables/useNotifications";

const { queue, queueSize } = useNotifications();
const toast = useToast();

watch(queueSize, (newValue) => {
  if (newValue > 0) toast.add(queue.value.pop());
});

onMounted(() => {
  if (queueSize.value > 0) toast.add(queue.value.pop());
});

const iconClass = (messageSeverity) => {
  return [
    "p-toast-message-icon pi",
    {
      "pi-info-circle": messageSeverity === "info",
      "pi-exclamation-triangle": messageSeverity === "warn",
      "pi-times": messageSeverity === "error",
      "pi-check": messageSeverity === "success",
    },
  ];
};

const formatString = (details) => {
  return details.toString().replace("\n", "<br/>");
};
</script>
