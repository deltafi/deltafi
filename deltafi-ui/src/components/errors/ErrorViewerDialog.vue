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
  <Dialog v-bind="$attrs" :header="header" :style="{ width: '75vw' }" :maximizable="true" :modal="true" :dismissable-mask="true" :draggable="false" :position="modelPosition">
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
    </div>
    <ScrollTop target="parent" :threshold="10" icon="pi pi-arrow-up" />
    <TabView v-model:activeIndex="activeTab">
      <TabPanel v-if="cause" header="Cause">
        <div class="error-row">
          <div class="error-col">
            <strong>Cause</strong>
            <HighlightedCode :highlight="false" :code="cause" />
          </div>
        </div>
      </TabPanel>
      <TabPanel v-else header="Cause" :disabled="true" />
      <TabPanel v-if="context" header="Context">
        <div class="error-row">
          <div class="error-col">
            <strong>Context</strong>
            <HighlightedCode :highlight="false" :code="context" />
          </div>
        </div>
      </TabPanel>
      <TabPanel v-else header="Context" :disabled="true" />
      <TabPanel v-if="content" header="Content">
        <div class="error-row">
          <div class="error-col">
            <ContentSelector :content="content" @content-selected="content" />
          </div>
        </div>
      </TabPanel>
      <TabPanel v-else header="Content" :disabled="true" />
    </TabView>
  </Dialog>
</template>

<script setup>
import Dialog from "primevue/dialog";
import HighlightedCode from "@/components/HighlightedCode.vue";
import { computed, defineProps, ref, useAttrs } from "vue";
import Timestamp from "@/components/Timestamp.vue";
import ScrollTop from "primevue/scrolltop";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";
import ContentSelector from "@/components/ContentSelector.vue";
const activeTab = ref(0);
const attrs = useAttrs();
const props = defineProps({
  action: {
    type: Object,
    required: true,
  },
});

const modelPosition = computed(() => {
  return _.get(attrs, "model-position", "top");
});
const header = computed(() => props.action.name);
const cause = computed(() => (["ERROR", "RETRIED"].includes(props.action.state) ? props.action.errorCause : props.action.filteredCause));
const context = computed(() => (["ERROR", "RETRIED"].includes(props.action.state) ? props.action.errorContext : props.action.filteredContext));
const content = computed(() => (props.action.content.length > 0 ? props.action.content : false));
</script>

<style lang="scss">
@import "@/styles/components/errors/error-viewer-dialog.scss";
</style>
