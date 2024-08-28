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
  <div class="deltafile-parent-child-panel">
    <CollapsiblePanel :header="header" class="table-panel">
      <DataTable :paginator="didsList.length < 10 ? false : true" :rows="10" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines parent-child-table" striped-rows :value="didsList" :loading="loading && !loaded" :row-class="actionRowClass" paginator-template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" :rows-per-page-options="[10, 20, 50, 100, 500, 1000]" current-page-report-template="Showing {first} to {last} of {totalRecords}" data-key="did">
        <template v-if="!loading" #empty>{{ `No ${header} found.` }}</template>
        <template #loading>Loading {{ header }}. Please wait.</template>
        <Column field="did" header="DID" class="did-col">
          <template #body="{ data }">
            <DidLink :did="data.did" />
          </template>
        </Column>
        <Column field="name" header="Filename" :sortable="true" />
        <Column field="dataSource" header="Data Source" :sortable="true" />
        <Column field="stage" header="Stage" class="stage-col" :sortable="true" />
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>
<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DidLink from "@/components/DidLink.vue";
import useDeltaFilesQueryBuilder from "@/composables/useDeltaFilesQueryBuilder";
import { computed, defineProps, onMounted, reactive, ref, watch } from "vue";
import _ from "lodash";

const { getDeltaFilesByDIDs } = useDeltaFilesQueryBuilder();

const props = defineProps({
  field: {
    type: String,
    required: true,
  },
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const didsList = ref([]);
const deltaFile = reactive(props.deltaFileData);
const loading = ref(true);
const loaded = ref(false);

onMounted(() => {
  fetchDidsArrayData();
});

const header = computed(() => {
  const relationship = props.field === "parentDids" ? "Parent" : "Child";
  return `${relationship} DeltaFiles`;
});

const fetchDidsArrayData = async () => {
  let didLists = [];
  if (!_.isEmpty(deltaFile[props.field])) {
    let didsArrayData = await getDeltaFilesByDIDs(deltaFile[props.field]);
    let deltaFilesObjectsArray = didsArrayData;
    for (let deltaFi of deltaFilesObjectsArray) {
      didLists.push(deltaFi);
    }
  }
  didsList.value = didLists;
  loading.value = false;
  loaded.value = true;
};

watch(() => deltaFile, fetchDidsArrayData, { deep: true });

const actionRowClass = (data) => {
  return data.stage === "ERROR" ? "table-danger action-error" : null;
};
</script>
<style lang="scss">
@import "@/styles/components/deltafile-parent-child-panel.scss";
</style>
