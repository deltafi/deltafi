<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
  <CollapsiblePanel header="Info" style="min-width: 85%">
    <div class="row">
      <div class="col-6">
        <p><b>Name: </b>{{ props.info.displayName }}</p>
      </div>
      <div class="col-6">
        <p><b>Version: </b>{{ props.info.pluginCoordinates.version }}</p>
      </div>
    </div>
    <div class="row">
      <div class="col">
        <p><b>Description: </b>{{ props.info.description }}</p>
      </div>
      <div class="col">
        <p><b>Action Kit Version: </b>{{ props.info.actionKitVersion }}</p>
      </div>
    </div>
  </CollapsiblePanel>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel";
import { defineProps } from "vue";

const props = defineProps({
  info: {
    type: Object,
    required: true,
  },
});
</script>
