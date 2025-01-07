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
  <Panel ref="panelRef" :toggleable="true" v-bind="$attrs" @click="panelHeaderToggle">
    <template v-for="(_, slot) of $slots" #[slot]="scope">
      <slot :name="slot" v-bind="scope" />
    </template>
    <slot />
  </Panel>
</template>

<script setup>
import Panel from "primevue/panel";
import { ref } from "vue";

const panelRef = ref(null);

const panelHeaderToggle = (event) => {
  let panelHeader = event.target.getAttribute("class");
  if (panelHeader == "p-panel-header") {
    panelRef.value.toggle(event);
  }
};
</script>
