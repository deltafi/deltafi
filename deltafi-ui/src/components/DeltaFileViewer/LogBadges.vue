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
  <div class="log-badges">
    <Badge v-if="props.errorCount" v-tooltip.top="`${pluralize(props.errorCount, 'error log', 'error logs')}`" :value="props.errorCount" severity="danger" @click="emit('click', 'ERROR')" class="mr-1 cursor-pointer"></Badge>
    <Badge v-if="props.warningCount" v-tooltip.top="`${pluralize(props.warningCount, 'warning log', 'warning logs')}`" :value="props.warningCount" severity="warning" @click="emit('click', 'WARNING')" class="mr-1 cursor-pointer"></Badge>
    <Badge v-if="props.userCount" v-tooltip.top="`${pluralize(props.userCount, 'user note', 'user notes')}`" :value="props.userCount" severity="primary" @click="emit('click', 'USER')" class="cursor-pointer"></Badge>
  </div>
</template>

<script setup>
import Badge from "primevue/badge";

import useUtilFunctions from "@/composables/useUtilFunctions";

const { pluralize } = useUtilFunctions();

const props = defineProps({
  errorCount: {
    type: Number,
    required: true,
  },
  warningCount: {
    type: Number,
    required: true,
  },
  userCount: {
    type: Number,
    required: true,
  },
});

const emit = defineEmits(["click"]);
</script>
