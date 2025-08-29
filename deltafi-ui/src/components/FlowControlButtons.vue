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
  <div class="btn-group btn-group-sm flow-control-buttons" role="group">
    <button type="button" :class="`btn btn-${config.start.severity}`" @click="setState('RUNNING', $event)">
      <i class="pi pi-play" />
    </button>
    <button type="button" :class="`btn btn-${config.pause.severity}`" @click="setState('PAUSED', $event)">
      <i class="pi pi-pause" />
    </button>
    <button type="button" :class="`btn btn-${config.stop.severity}`" @click="setState('STOPPED', $event)">
      <i class="pi pi-stop" />
    </button>
  </div>
</template>

<script setup>
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
      severity: model.value === 'RUNNING' ? "success" : "outline-secondary"
    },
    pause: {
      outlined: model.value !== 'PAUSED',
      severity: model.value === 'PAUSED' ? "warning" : "outline-secondary"
    },
    stop: {
      outlined: model.value !== 'STOPPED',
      severity: model.value === 'STOPPED' ? "secondary" : "outline-secondary"
    }
  }
});

</script>

<style>
.flow-control-buttons {
  .btn {
    border: 1px solid #b1b4b8 !important;

    .pi {
      padding: 0.3rem 0.1rem 0.3rem 0.1rem;
    }
  }
}
</style>