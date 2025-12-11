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
  <div class="annotations-panel">
    <CollapsiblePanel :header="headerWithCount" :collapsed="isEmpty" class="links-panel pl-0">
      <div v-if="_.isEmpty(_.get(deltaFile, 'annotations', null))" class="d-flex w-100 justify-content-between no-data-panel-content">
        <span v-if="!_.isEmpty(props.deltaFileData.pendingAnnotations)" class="p-2">No Annotations Available. Expected Read Receipts: {{ props.deltaFileData.pendingAnnotations }}</span>
        <span v-else class="p-2">No Annotations Available</span>
      </div>
      <div v-else class="list-group list-group-flush">
        <span class="list-group-item list-group-item-action remove-border">
          <DialogTemplate component-name="AnnotationsViewer" header="Annotations" dialog-width="50vw" :annotations="annotations" :pending-annotations="props.deltaFileData.pendingAnnotations">
            <div class="content-viewer-button">
              <div class="d-flex w-100 justify-content-between">
                <strong class="mb-0">View</strong>
                <i class="far fa-window-maximize" />
              </div>
              <small class="mb-1 text-muted d-flex w-100">
                <span>{{ pluralize(Object.keys(deltaFile.annotations).length, "key/value pair") }}</span>
              </small>
            </div>
          </DialogTemplate>
        </span>
      </div>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, reactive } from "vue";
import _ from "lodash";

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const { pluralize } = useUtilFunctions();
const deltaFile = reactive(props.deltaFileData);

const annotationCount = computed(() => Object.keys(_.get(deltaFile, 'annotations', {})).length);

const headerWithCount = computed(() => `Annotations (${annotationCount.value})`);

const isEmpty = computed(() => annotationCount.value === 0);

const annotations = computed(() => {
  if (_.isEmpty(deltaFile)) {
    return {};
  }

  const formattedAnnotations = {};
  const arrayOfNewObjects = [];
  for (const k in deltaFile.annotations) {
    const newKeyValueObj = {};
    newKeyValueObj["key"] = k;
    newKeyValueObj["value"] = deltaFile.annotations[k];
    arrayOfNewObjects.push(newKeyValueObj);
  }

  formattedAnnotations["annotations"] = arrayOfNewObjects;

  return formattedAnnotations;
});
</script>

<style>
.annotations-panel {
  .no-data-panel-content {
    padding: 0.5rem 1.25rem;
  }

  .list-group-item {
    padding: 0;
    cursor: pointer;

    .content-viewer-button {
      padding: 0.75rem 1.25rem;
    }
  }
}
</style>
