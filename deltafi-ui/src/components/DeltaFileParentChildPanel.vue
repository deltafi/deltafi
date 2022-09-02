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
  <div>
    <CollapsiblePanel header="Parent/Child DeltaFiles" class="table-panel">
      <DataTable v-model:expandedRowGroups="expandedRowGroups" :paginator="(didsList.length < 10 ? false : true)" :rows="10" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines" striped-rows :value="didsList" row-group-mode="subheader" group-rows-by="didType" :loading="loading && !loaded" :expandable-row-groups="true" :row-class="actionRowClass" paginator-template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" :rows-per-page-options="[10, 20, 50, 100, 500, 1000]" current-page-report-template="Showing {first} to {last} of {totalRecords}">
        <template #empty>No Parent/Child DeltaFiles found.</template>
        <template #loading>Loading Parent/Child DeltaFiles. Please wait.</template>
        <Column field="didType" header="DID Type" :hidden="false" :sortable="true" />
        <Column field="did" header="DID" class="col-4">
          <template #body="{ data }">
            <DidLink :did="data.did" />
          </template>
        </Column>
        <Column field="sourceInfo.filename" header="Filename" class="col-4" :sortable="true" />
        <Column field="stage" header="Stage" class="col-4" :sortable="true" />
        <template #groupheader="slotProps">
          <span>{{ slotProps.data.didType }}</span>
        </template>
        <template #paginatorstart>
          <Button type="button" icon="pi pi-refresh" class="p-button-text" />
        </template>
        <template #paginatorend>
          <Button type="button" icon="pi pi-cloud" class="p-button-text" />
        </template>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DidLink from "@/components/DidLink.vue";
import useDeltaFilesQueryBuilder from "@/composables/useDeltaFilesQueryBuilder";
import { defineProps, onMounted, reactive, ref, watch } from "vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import _ from "lodash";

const { getDeltaFilesByDIDs } = useDeltaFilesQueryBuilder();
const { pluralize } = useUtilFunctions();

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const expandedRowGroups = ref()
const didsList = ref([]);
const deltaFile = reactive(props.deltaFileData);
const loading = ref(true);
const loaded = ref(false);

onMounted(() => {
  fetchParentChildDidsArrayData();
});

const fetchParentChildDidsArrayData = async () => {
  const didTypes = ["parentDids", "childDids"];
  loading.value = true;
  let combinDidsList = []
  for (let didType of didTypes) {
    if (_.isEmpty(deltaFile[didType])) {
      continue;
    } else {
      let newDidsArrayData = [];
      let didsArrayData = await getDeltaFilesByDIDs(deltaFile[didType]);
      let deltaFilesObjectsArray = didsArrayData.data.deltaFiles.deltaFiles;
      for (let deltaFi of deltaFilesObjectsArray) {
        if (didType === "parentDids") {
          deltaFi["didType"] = pluralizeWithCount(deltaFilesObjectsArray.length, "Parent");
        } else {
          deltaFi["didType"] = pluralizeWithCount(deltaFilesObjectsArray.length, "Child", "Children");
        }
        newDidsArrayData.push(deltaFi);
      }
      combinDidsList = _.concat(combinDidsList, newDidsArrayData);
    }
  }
  loading.value = false;
  loaded.value = true;
  didsList.value = combinDidsList;
};

watch(
  () => deltaFile,
  fetchParentChildDidsArrayData,
  { deep: true }
)

const pluralizeWithCount = (count, singular, plural) => {
  let pluralized = pluralize(count, singular, plural, false);
  return (count > 1) ? `${pluralized} (${count})` : pluralized;
}

const actionRowClass = (data) => {
  return data.stage === "ERROR" ? "table-danger action-error" : null;
};

</script>

<style lang="scss">
@import "@/styles/components/deltafile-info-panel.scss";
</style>
