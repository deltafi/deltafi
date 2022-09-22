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
  <div class="enrichment-panel">
    <CollapsiblePanel header="Enrichment" class="links-panel pl-0">
      <div v-if="!enrichmentContentReferences.length" class="d-flex w-100 justify-content-between no-data-panel-content">
        <span class="p-2">No Enrichment Data Available</span>
      </div>
      <div v-else class="list-group list-group-flush">
        <div v-for="item in enrichmentContentReferences" :key="item" class="list-group-item list-group-item-action">
          <ContentDialog :content="[item]" :header="`Enrichment: ${item.name}`">
            <div class="content-viewer-button">
              <div class="d-flex w-100 justify-content-between">
                <strong class="mb-0">{{ item.name }}</strong>
                <i class="far fa-window-maximize" />
              </div>
              <small class="mb-1 text-muted d-flex w-100 justify-content-between">
                <span>{{ item.contentReference.mediaType }}</span>
                <span>{{ formattedBytes(item.contentReference.size) }}</span>
              </small>
            </div>
          </ContentDialog>
        </div>
      </div>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import { computed, reactive, defineProps } from "vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ContentDialog from "@/components/ContentDialog.vue";

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const { formattedBytes } = useUtilFunctions();
const deltaFile = reactive(props.deltaFileData);

const enrichmentContentReferences = computed(() => {
  return deltaFile.enrichment.map((enrichment) => {
    const content = enrichment.value || "";
    return {
      name: enrichment.name,
      contentReference: {
        did: deltaFile.did,
        content: content,
        filename: `${deltaFile.did}-enrichment-${enrichment.name}`,
        size: content.length,
        mediaType: enrichment.mediaType
      }
    };
  });
});
</script>

<style lang="scss">
@import "@/styles/components/deltafile-enrichment-panel.scss";
</style>