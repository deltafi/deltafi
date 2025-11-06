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
  <div class="lookup-tables-page">
    <PageHeader heading="Lookup Tables">
      <div class="btn-toolbar">
        <LookupTablePageHeaderButtonGroup :export-lookup-tables="lookupTablesExport" @reload-lookup-tables="refresh" />
      </div>
    </PageHeader>
    <div class="lookup-tables-page">
      <Panel header="Lookup Table" class="table-panel">
        <DataTable :value="lookupTables" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines lookup-tables-data-table">
          <template #empty> No Lookup Tables found </template>
          <Column field="name" header="Name" :sortable="true">
            <template #body="{ data }">
              <div class="d-flex justify-content-between align-items-center">
                <router-link :to="{ path: `/admin/lookup-table/${data.name}` }">
                  {{ data.name }}
                </router-link>
                <span>
                  <span class="d-flex align-items-center">
                    <LookupTableNameColumnButtonGroup :key="Math.random()" :row-data-prop="data" @reload-lookup-tables="refresh" />
                  </span>
                </span>
              </div>
            </template>
          </Column>
          <Column field="keyColumns" header="Key Columns">
            <template #body="{ data }">
              <div v-for="item in data.keyColumns" :key="item" class="list-item">{{ item }}</div>
            </template>
          </Column>
          <Column field="columns" header="Columns">
            <template #body="{ data }">
              <div>{{ data.columns.length }}</div>
            </template>
          </Column>
          <Column field="totalRows" header="Total Rows" />
          <Column field="backingServiceActive" header="Backing Service Active" class="switch-column">
            <template #body="{ data }">
              <LookupTableBackingServiceActiveInputSwitch :row-data-prop="data" />
            </template>
          </Column>
          <Column field="refreshDuration" header="Refresh Duration" :style="{ width: '10%' }"></Column>
          <Column field="serviceBacked" header="Service Backed" :style="{ width: '10%' }"></Column>
          <Column field="pullThrough" header="Pull Through" :style="{ width: '10%' }"></Column>
          <Column field="lastRefresh" header="Last Refresh" :style="{ width: '10%' }"></Column>
        </DataTable>
      </Panel>
    </div>
  </div>
</template>

<script setup>
import LookupTableBackingServiceActiveInputSwitch from "@/components/lookupTable/LookupTableBackingServiceActiveInputSwitch.vue";
import LookupTablePageHeaderButtonGroup from "@/components/lookupTable/LookupTablePageHeaderButtonGroup.vue";
import LookupTableNameColumnButtonGroup from "@/components/lookupTable/LookupTableNameColumnButtonGroup.vue";
import PageHeader from "@/components/PageHeader.vue";
import useLookupTopics from "@/composables/useLookupTable";
import { computed, onMounted, ref } from "vue";

import _ from "lodash";

import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Panel from "primevue/panel";

const { getLookupTables } = useLookupTopics();

const lookupTables = ref([]);

const refresh = async () => {
  fetchLookupTables();
};

onMounted(async () => {
  fetchLookupTables();
});

const fetchLookupTables = async () => {
  const response = await getLookupTables();
  if (response.errors) {
    return;
  }

  lookupTables.value = response.data.getLookupTables;
};

const lookupTablesExport = computed(() => {
  const lookupTableObject = {};
  lookupTableObject["lookupTables"] = lookupTables.value;
  return lookupTableObject;
});
</script>

<style>
.lookup-tables-page {
  .table-panel {
    .p-panel-header {
      padding: 0 1.25rem;

      .p-panel-title {
        padding: 1rem 0;
      }
    }

    .lookup-tables-data-table {
      td.switch-column {
        padding: 0 !important;
        width: 13rem;

        .p-inputswitch {
          padding: 0.25rem !important;
          margin: 0.25rem 0 0 0.25rem !important;
        }

        .p-button {
          padding: 0.25rem !important;
          margin: 0 0 0 0.25rem !important;
        }
      }

      .list-item::before {
        content: "•";
        margin-right: 0.25rem;
        font-weight: bold;
      }
    }
  }
}
</style>
