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
  <span v-tooltip.top="tooltip" :class="classes">{{ props.role.name }}</span>
</template>

<script setup>
import { computed, defineProps } from 'vue';

const props = defineProps({
  role: {
    type: Object,
    required: true,
  },
  enabled: {
    type: Boolean,
    required: false,
    default: true
  }
});

const tooltip = computed(() => {
  return props.role.permissions.join(', ')
})

const classes = computed(() => {
  const classes = ["badge", "badge-pill"];
  classes.push(props.enabled ? "badge-info" : "badge-disabled");
  return classes.join(" ");
})
</script>

<style lang="scss">
.badge-disabled {
  background-color: #919ca6;
  color: #FFFFFF;
}
</style>
