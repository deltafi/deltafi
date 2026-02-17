<!--
   ABOUTME: Displays error count badge in the top bar that links to the errors page.
   ABOUTME: Shows a danger badge when errors exist, muted badge when zero.
-->
<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
  <router-link to="/errors" class="error-count-link">
    <Tag v-tooltip.bottom="tooltip" :class="tagClass" icon="pi pi-exclamation-circle" :severity="severity" :value="tagValue" />
  </router-link>
</template>

<script setup lang="ts">
import Tag from "primevue/tag";
import { computed } from "vue";
import useErrorCount from "@/composables/useErrorCount";
import useUtilFunctions from "@/composables/useUtilFunctions";

const { errorCount } = useErrorCount();
const { pluralize } = useUtilFunctions();

const tagValue = computed(() => {
  return String(errorCount.value);
});

const severity = computed(() => {
  return errorCount.value > 0 ? "danger" : undefined;
});

const tagClass = computed(() => {
  return [
    "error-count-badge",
    {
      "no-errors": errorCount.value === 0,
    },
  ];
});

const tooltip = computed(() => {
  if (errorCount.value === 0) {
    return "No unacknowledged errors";
  }
  return pluralize(errorCount.value, "unacknowledged error");
});
</script>

<style scoped>
.error-count-link {
  text-decoration: none;
}

.error-count-badge {
  cursor: pointer;
}

.error-count-badge.no-errors {
  background-color: var(--gray-700) !important;
}
</style>
