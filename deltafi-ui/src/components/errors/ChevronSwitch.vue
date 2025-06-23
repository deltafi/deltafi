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
  <ChevronDownIcon v-if="isExpanded" class="text-muted" />
  <ChevronRightIcon v-else class="text-muted" />
</template>

<script setup>
import { computed } from "vue";

import ChevronDownIcon from "primevue/icons/chevrondown";
import ChevronRightIcon from "primevue/icons/chevronright";

import _ from "lodash";

const props = defineProps({
  expandedRows: {
    type: Object,
    required: true,
  },
  row: {
    type: Object,
    required: true,
  },
});

const isExpanded = computed(() => {
  return _.has(props.expandedRows, props.row.message);
});
</script>
