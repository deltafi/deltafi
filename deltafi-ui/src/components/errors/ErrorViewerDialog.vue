<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
  <Dialog v-bind="$attrs" :header="header" :style="{ width: '75vw' }" :maximizable="true" :modal="true" :dismissable-mask="true" :draggable="false">
    <div class="error-viewer">
      <div class="error-row">
        <div class="error-col">
          <strong>Action Created</strong>
          <p>
            <Timestamp :timestamp="action.created" />
          </p>
        </div>
        <div class="error-col">
          <strong>Action Modified</strong>
          <p>
            <Timestamp :timestamp="action.modified" />
          </p>
        </div>
        <div class="error-col">
          <strong>Action State</strong>
          <p>{{ action.state }}</p>
        </div>
      </div>
      <div v-if="cause" class="error-row">
        <div class="error-col">
          <strong>Cause</strong>
          <HighlightedCode :highlight="false" :code="cause" />
        </div>
      </div>
      <div v-if="context" class="error-row">
        <div class="error-col">
          <strong>Context</strong>
          <HighlightedCode :highlight="false" :code="context" />
        </div>
      </div>
    </div>
    <ScrollTop target="parent" :threshold="10" icon="pi pi-arrow-up" />
  </Dialog>
</template>

<script setup>
import Dialog from "primevue/dialog";
import HighlightedCode from "@/components/HighlightedCode.vue";
import { computed, defineProps } from "vue";
import Timestamp from "@/components/Timestamp.vue";
import ScrollTop from "primevue/scrolltop";

const props = defineProps({
  action: {
    type: Object,
    required: true,
  },
});

const header = computed(() => props.action.name);
const cause = computed(() => ["ERROR", "RETRIED"].includes(props.action.state) ? props.action.errorCause : props.action.filteredCause);
const context = computed(() => ["ERROR", "RETRIED"].includes(props.action.state) ? props.action.errorContext : props.action.filteredContext);
</script>

<style lang="scss">
@import "@/styles/components/errors/error-viewer-dialog.scss";
</style>
