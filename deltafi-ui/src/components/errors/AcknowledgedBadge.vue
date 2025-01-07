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
  <i v-tooltip.right="tooltip" :class="classes" />
</template>

<script setup>
import { computed, defineProps } from "vue";

const props = defineProps({
  reason: {
    type: String,
    required: true,
  },
  timestamp: {
    type: String,
    required: true,
  },
  icon: {
    type: String,
    required: false,
    default: "far fa-check-circle",
  },
});

const classes = computed(() => {
  return `ack-icon ${props.icon}`;
});

const tooltip = computed(() => {
  return `Error Acknowledged: ${props.timestamp}\n\n${props.reason}`;
});
</script>

<style scoped>
.ack-icon {
  color: var(--red);
}
</style>
