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
  <div class="all-panel">
    <Panel header="DeltaFiles" @contextmenu="onPanelRightClick">
      <ContextMenu ref="menu" :model="menuItems" />
      <template #icons>
        <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
          <span class="fas fa-bars" />
        </Button>
        <Menu ref="menu" :model="menuItems" :popup="true" />
        <Paginator v-if="filtered.length > 0" :rows="perPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalFiltered" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage($event)" />
      </template>
      <DataTable id="filteredTable" v-model:expanded-rows="expandedRows" v-model:selection="selectedDids" responsive-layout="scroll" selection-mode="multiple" data-key="did" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="filtered" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalFiltered" :row-hover="true" filter-display="menu" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
        <template #empty> No results to display. </template>
        <template #loading> Loading. Please wait... </template>
        <Column class="expander-column" :expander="true" />
        <Column field="did" header="DID" class="did-column">
          <template #body="{ data }">
            <DidLink :did="data.did" />
          </template>
        </Column>
        <Column field="name" header="Filename" :sortable="true" class="filename-column">
          <template #body="{ data }">
            <div v-if="data.name.length > 28" v-tooltip.top="data.name" class="truncate">
              {{ data.name }}
            </div>
            <div v-else>
              {{ data.name }}
            </div>
          </template>
        </Column>
        <Column field="dataSource" header="Data Source" :sortable="true" class="flow-column" />
        <Column field="filtered_cause" header="Last Filtered Cause">
          <template #body="{ data }">
            {{ lastFilteredCauseFlow(data.flows) }}
          </template>
        </Column>
        <Column field="created" header="Created" :sortable="true" class="timestamp-column">
          <template #body="row">
            <Timestamp :timestamp="row.data.created" />
          </template>
        </Column>
        <Column field="modified" header="Modified" :sortable="true" class="timestamp-column">
          <template #body="row">
            <Timestamp :timestamp="row.data.modified" />
          </template>
        </Column>
        <template #expansion="filter">
          <div class="filtered-Subtable">
            <DataTable v-model:expanded-rows="expandedRows" data-key="name" responsive-layout="scroll" :value="filter.data.flows" :row-hover="false" striped-rows class="p-datatable-sm p-datatable-gridlines" :row-class="flowRowClass">
              <Column class="expander-column" :expander="true" />
              <Column field="name" header="Name" />
              <Column field="state" header="State" />
              <Column field="filtered_cause" header="Filtered Cause">
                <template #body="{ data }">
                  {{ lastFilteredCauseActions(data.actions) }}
                </template>
              </Column>
              <Column field="created" header="Created">
                <template #body="row">
                  <Timestamp :timestamp="row.data.created" />
                </template>
              </Column>
              <Column field="modified" header="Modified">
                <template #body="row">
                  <Timestamp :timestamp="row.data.modified" />
                </template>
              </Column>
              <template #expansion="actions">
                <div class="filtered-Subtable">
                  <DataTable responsive-layout="scroll" data-key="name" class="p-datatable-sm p-datatable-gridlines" striped-rows :value="actions.data.actions" :row-class="actionRowClass">
                    <Column field="name" header="Action" :sortable="true" />
                    <Column field="state" header="State" class="state-column" :sortable="true" />
                    <Column field="created" header="Created" class="timestamp-column" :sortable="true">
                      <template #body="row">
                        <Timestamp :timestamp="row.data.created" />
                      </template>
                    </Column>
                    <Column field="modified" header="Modified" class="timestamp-column" :sortable="true">
                      <template #body="row">
                        <Timestamp :timestamp="row.data.modified" />
                      </template>
                    </Column>
                    <Column field="filteredCause" header="Filtered Cause" :sortable="true" />
                  </DataTable>
                </div>
              </template>
            </DataTable>
          </div>
        </template>
      </DataTable>
    </Panel>
    <ReplayDialog ref="replayDialog" :did="filterSelectedDids" @refresh-page="fetchDeltaFilesData()" />
  </div>
</template>

<script setup>
import DidLink from "@/components/DidLink.vue";
import ReplayDialog from "@/components/ReplayDialog.vue";
import Timestamp from "@/components/Timestamp.vue";
import useFiltered from "@/composables/useFiltered";
import { computed, inject, nextTick, onMounted, ref, watch } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";

import _ from "lodash";

import Button from "primevue/button";
import ContextMenu from "primevue/contextmenu";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Menu from "primevue/menu";
import Paginator from "primevue/paginator";
import Panel from "primevue/panel";

const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");
const emit = defineEmits(["refreshFilters", "filterCauseChanged:filteredCause"]);
const loading = ref(true);
const menu = ref();
const filtered = ref([]);
const replayDialog = ref();
const expandedRows = ref([]);

const totalFiltered = ref(0);
const offset = ref(0);
const perPage = ref();
const sortField = ref("modified");
const sortDirection = ref("DESC");
const selectedDids = ref([]);

const props = defineProps({
  flow: {
    type: Object,
    required: false,
    default: undefined,
  },
  cause: {
    type: String,
    required: false,
    default: undefined,
  },
  queryParams: {
    type: Object,
    required: true,
  },
});

const menuItems = ref([
  {
    label: "Clear Selected",
    icon: "fas fa-times fa-fw",
    command: () => {
      selectedDids.value = [];
    },
  },
  {
    label: "Select All Visible",
    icon: "fas fa-check-double fa-fw",
    command: () => {
      selectedDids.value = filtered.value;
    },
  },
  {
    separator: true,
    visible: computed(() => hasSomePermissions("DeltaFileAcknowledge", "DeltaFileResume", "ResumePolicyCreate")),
  },
  {
    label: "Replay Selected",
    icon: "fas fa-sync fa-fw",
    command: () => {
      replayDialog.value.showConfirmDialog();
    },
    visible: computed(() => hasPermission("DeltaFileReplay")),
    disabled: computed(() => selectedDids.value.length == 0),
  },
]);

const { data: response, fetchAllFiltered: getFiltered } = useFiltered();

const actionRowClass = (action) => {
  if (action.state === "ERROR") return "table-danger action-error";
  if (action.state === "FILTERED") return "table-warning action-filtered";
  if (action.state === "RETRIED") return "table-warning action-error";
};

const flowRowClass = (action) => {
  if (action.state === "ERROR") return "table-danger action-error";
  if (action.state === "FILTERED") return "table-warning action-filtered";
  if (action.state === "RETRIED") return "table-warning action-error";
};

const fetchFiltered = async () => {
  await getPersistedParams();
  const flowName = props.flow?.name != null ? props.flow?.name : null;
  const flowType = props.flow?.type != null ? props.flow?.type : null;
  loading.value = true;
  await getFiltered(props.queryParams, offset.value, perPage.value, sortField.value, sortDirection.value, flowName, flowType, props.cause);
  filtered.value = response.value.deltaFiles.deltaFiles;
  totalFiltered.value = response.value.deltaFiles.totalCount;
  loading.value = false;
};

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

const onRowContextMenu = (event) => {
  if (selectedDids.value.length <= 0) {
    selectedDids.value = [event.data];
  }
};

const onPanelRightClick = (event) => {
  menu.value.show(event);
};

const filterSelectedDids = computed(() => {
  const dids = selectedDids.value.map((selectedDids) => {
    return selectedDids.did;
  });
  return dids;
});

const lastFilteredCauseActions = (actions) => {
  return _.chain(actions)
    .filter((action) => action.state === "FILTERED")
    .sortBy(["modified"])
    .reverse()
    .value()[0]?.filteredCause;
};

const lastFilteredCauseFlow = (flows) => {
  const actions = _.chain(flows)
    .map((flow) => flow.actions)
    .flatten()
    .value();
  return lastFilteredCauseActions(actions);
};

const onPage = async (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  setPersistedParams();
  await nextTick();
  fetchFiltered();
  emit("refreshFilters");
};

const onSort = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  sortField.value = event.sortField;
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchFiltered();
};

defineExpose({
  fetchFiltered,
});

const setupWatchers = () => {
  watch(
    () => [props.flow, props.cause],
    () => {
      fetchFiltered();
    }
  );
};

onMounted(async () => {
  await getPersistedParams();
  await fetchFiltered();
  setupWatchers();
});

const getPersistedParams = async () => {
  const state = useStorage("filtered-page-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  perPage.value = state.value.perPage || 20;
};

const setPersistedParams = () => {
  const state = useStorage("filtered-page-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  state.value = {
    perPage: perPage.value,
  };
};
</script>

<style>
.all-panel {
  td.last-filter-column {
    width: auto;
  }

  td.did-column {
    width: 8rem;
  }

  td.filename-column {
    width: 16rem;
    max-width: 16rem;

    div.truncate {
      white-space: nowrap;
      text-overflow: ellipsis;
      overflow: hidden;
    }
  }

  td.flow-column {
    width: 1rem;
    white-space: nowrap;
  }

  td.timestamp-column {
    width: 15rem !important;
  }

  .p-column-filter-overlay {
    margin-left: -18px;
    max-width: 300px;
  }
}
</style>
