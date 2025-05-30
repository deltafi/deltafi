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
  <div class="deltafile-content-tags-panel">
    <CollapsiblePanel header="Content Tags" class="table-panel">
      <DataTable responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines parent-child-table" striped-rows :value="contentTags" paginator-template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" :rows-per-page-options="[10, 20, 50, 100, 500, 1000]" current-page-report-template="Showing {first} to {last} of {totalRecords}" data-key="did">
        <template #empty>
          No Content Tags
        </template>
        <Column header="Tag" field="tag">
          <template #body="{ data }">
            <ContentTag :value="data.tag" class="ml-2" />
          </template>
        </Column>
        <Column header="Content" class="content-column">
          <template #body="{ data }">
            <ContentDialog :content="[data.content]" action="">
              <Button icon="far fa-window-maximize" label="View" class="content-button p-button-link" />
            </ContentDialog>
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import { computed, reactive } from "vue";
import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ContentTag from "@/components/ContentTag.vue";
import ContentDialog from "@/components/ContentDialog.vue";
import _ from "lodash";

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});
const deltaFile = reactive(props.deltaFileData);

const contentTags = computed(() => {
  const contentWithTags = _.chain(deltaFile.flows)
    .flatMap('actions')
    .flatMap('content')
    .filter((item) => _.get(item, 'tags.length', 0) > 0)
    .value();
  const results = [];
  contentWithTags.forEach((content) => {
    content.tags.forEach((tag) => {
      results.push({
        tag: tag,
        content: content
      })
    })
  })
  return _.chain(results)
    .uniqBy((item) => JSON.stringify([item.tag, _.flatMap(item.content.segments, 'uuid')]))
    .sortBy((item) => item.tag)
    .value();
})

</script>

<style>
.deltafile-content-tags-panel {
  .content-column {
    width: 1%;
    padding: 0 0.5rem !important;

    .content-button {
      cursor: pointer !important;
      padding: 0.1rem 0.4rem;
      margin: 0;
      color: #333333;
    }

    .content-button:hover {
      color: #666666 !important;

      .p-button-label {
        text-decoration: none !important;
      }
    }

    .content-button:focus {
      outline: none !important;
      box-shadow: none !important;
    }
  }
}
</style>
