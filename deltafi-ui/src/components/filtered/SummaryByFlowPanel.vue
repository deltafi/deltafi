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
  <Panel header="DeltaFiles by Flow" @contextmenu="onPanelRightClick">
    <ContextMenu ref="menu" :model="menuItems" />
    <template #icons>
      <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
        <span class="fas fa-bars" />
      </Button>
      <Menu ref="menu" :model="menuItems" :popup="true" />
      <Paginator v-if="filteredFlow.length > 0" :rows="perPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalFilteredFlow" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage($event)" />
    </template>
    <DataTable id="filteredSummaryTable" v-model:selection="selectedFiltered" responsive-layout="scroll" selection-mode="multiple" data-key="flow" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="filteredFlow" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalFilteredFlow" :row-hover="true" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
      <template #empty>
        No results to display.
      </template>
      <template #loading>
        Loading. Please wait...
      </template>
      <Column field="flow" header="Flow" :sortable="true" class="filename-column">
        <template #body="{ data }">
          <a class="monospace" @click="showAllTab(data.flow, data.type)">{{ data.flow }}</a>
        </template>
      </Column>
      <Column field="type" header="Flow Type" :sortable="true" />
      <Column field="count" header="Count" :sortable="true" />
    </DataTable>
  </Panel>
  <RetryResumeDialog ref="retryResumeDialog" :did="selectedDids" @update="fetchDeltaFilesData()" />
</template>

<script setup>
import RetryResumeDialog from "@/components/MetadataDialogReplay.vue";
import useFiltered from "@/composables/useFiltered";
import { computed, inject, nextTick, onMounted, ref, watch } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";

import _ from "lodash";

import Button from "primevue/button";
import Column from "primevue/column";
import ContextMenu from "primevue/contextmenu";
import DataTable from "primevue/datatable";
import Menu from "primevue/menu";
import Paginator from "primevue/paginator";
import Panel from "primevue/panel";

const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");

const emit = defineEmits(["refreshFilters", "showAllTab"]);
const { data: response, fetchFilteredSummaryByFlow } = useFiltered();
const retryResumeDialog = ref();
const loading = ref(true);
const menu = ref();
const filteredFlow = ref([]);
const totalFilteredFlow = ref(0);
const offset = ref(0);
const perPage = ref();
const sortField = ref("NAME");
const sortDirection = ref("DESC");
const selectedFiltered = ref([]);

const props = defineProps({
  flow: {
    type: Object,
    required: false,
    default: undefined,
  },
});

const menuItems = ref([
  {
    label: "Clear Selected",
    icon: "fas fa-times fa-fw",
    command: () => {
      selectedFiltered.value = [];
    },
  },
  {
    label: "Select All Visible",
    icon: "fas fa-check-double fa-fw",
    command: () => {
      selectedFiltered.value = filteredFlow.value;
    },
  },
  {
    separator: true,
    visible: computed(() => hasSomePermissions("DeltaFileAcknowledge", "DeltaFileResume")),
  },
  {
    label: "Replay Selected",
    icon: "fas fa-sync fa-fw",
    command: () => {
      retryResumeDialog.value.showConfirmDialog();
    },
    visible: computed(() => hasPermission("DeltaFileReplay")),
    disabled: computed(() => selectedDids.value.length == 0),
  },
]);

onMounted(async () => {
  await getPersistedParams();
  fetchFilteredFlow();
  setupWatchers();
});

const fetchFilteredFlow = async () => {
  getPersistedParams();
  loading.value = true;
  await fetchFilteredSummaryByFlow(offset.value, perPage.value, sortField.value, sortDirection.value, props.flow?.name);
  filteredFlow.value = response.value.countPerFlow;
  totalFilteredFlow.value = response.value.totalCount;
  loading.value = false;
};

const selectedDids = computed(() => {
  const dids = selectedFiltered.value.map((selectedFiltered) => {
    return selectedFiltered.dids;
  });

  return _.flatten([...new Set(dids)]);
});

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

const onRowContextMenu = (event) => {
  if (selectedFiltered.value.length <= 0) {
    selectedFiltered.value = [event.data];
  }
};

const onPanelRightClick = (event) => {
  menu.value.show(event);
};

defineExpose({
  fetchFilteredFlow,
});

const showAllTab = (flowName, flowType, cause) => {
  emit("showAllTab", flowName, flowType, cause);
};

const onSort = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  sortField.value = event.sortField === "flow" ? "NAME" : event.sortField.toUpperCase();
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchFilteredFlow();
};

const onPage = async (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  await nextTick();
  setPersistedParams();
  fetchFilteredFlow();
  emit("refreshFilters");
};
const setupWatchers = () => {
  watch(
    () => props.flow,
    () => {
      fetchFilteredFlow();
    }
  );
};
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

<style />
