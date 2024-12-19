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
  <div class="btn-group flow-control-buttons" role="group">
    <Button icon="pi pi-play" class="btn p-button-sm" :outlined="config.start.outlined" :severity="config.start.severity" @click="setState('RUNNING', $event)" />
    <Button icon="pi pi-pause" class="btn p-button-sm" :outlined="config.pause.outlined" :severity="config.pause.severity" @click="setState('PAUSED', $event)" />
    <Button icon="pi pi-stop" class="btn p-button-sm" :outlined="config.stop.outlined" :severity="config.stop.severity" @click="setState('STOPPED', $event)" />
  </div>
</template>

<script setup>
import Button from 'primevue/button'
import { computed } from "vue"

const model = defineModel({ type: String });
const emit = defineEmits(["start", "pause", "stop"])

const setState = (state, event) => {
  if (model.value !== state) {
    model.value = state;
    switch (state) {
      case "RUNNING":
        emit("start");
        break;
      case "PAUSED":
        emit("pause");
        break;
      case "STOPPED":
        emit("stop");
        break;
    }
  }

  if (event) {
    event.srcElement.parentElement.blur();
    event.srcElement.blur()
  }
}

const config = computed(() => {
  return {
    start: {
      outlined: model.value !== 'RUNNING',
      severity: model.value === 'RUNNING' ? "success" : "secondary"
    },
    pause: {
      outlined: model.value !== 'PAUSED',
      severity: model.value === 'PAUSED' ? "warning" : "secondary"
    },
    stop: {
      outlined: model.value !== 'STOPPED',
      severity: model.value === 'STOPPED' ? "danger" : "secondary"
    }
  }
});

</script>

<style lang="scss">
.flow-control-buttons {
  .p-button {
    border: 1px solid #b1b4b8 !important;
  }
}
</style>