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
  <Panel header="DeltaFiles by Cause" @contextmenu="onPanelRightClick">
    <ContextMenu ref="menu" :model="menuItems" />
    <template #icons>
      <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
        <span class="fas fa-bars" />
      </Button>
      <Menu ref="menu" :model="menuItems" :popup="true" />
      <Paginator v-if="filteredCause.length > 0" :rows="perPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalFilteredMessage" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage($event)"></Paginator>
    </template>
    <DataTable id="filteredSummaryTable" v-model:selection="selectedFilters" responsive-layout="scroll" selection-mode="multiple" data-key="dids" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="filteredCause" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalFilteredMessage" :row-hover="true" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
      <template #empty>No results to display.</template>
      <template #loading>Loading. Please wait...</template>
      <Column field="flow" header="Flow" :sortable="true" class="filename-column"> </Column>
      <Column field="type" header="Flow Type" :sortable="true"> </Column>
      <Column field="count" header="Count" :sortable="true" />
      <Column field="message" header="Cause" :sortable="true">
        <template #body="{ data }">
          <a class="monospace" @click="showAllTab(data.flow, data.type, data.message)">{{ data.message }}</a>
        </template>
      </Column>
    </DataTable>
  </Panel>
  <RetryResumeDialog ref="retryResumeDialog" :did="filterSelectedDids" @update="fetchDeltaFilesData()" />
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Button from "primevue/button";
import Panel from "primevue/panel";
import Menu from "primevue/menu";
import ContextMenu from "primevue/contextmenu";
import Paginator from "primevue/paginator";
import RetryResumeDialog from "@/components/MetadataDialogReplay.vue";
import useFiltered from "@/composables/useFiltered";
import { computed, defineEmits, defineExpose, defineProps, inject, nextTick, onMounted, ref, watch } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";
const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");

const retryResumeDialog = ref();
const loading = ref(true);
const menu = ref();
const filteredCause = ref([]);
const totalFilteredMessage = ref(0);
const offset = ref(0);
const perPage = ref();
const sortDirection = ref("DESC");
const selectedFilters = ref([]);
const emit = defineEmits(["refreshFilters", "showAllTab"]);
const props = defineProps({
  flow: {
    type: Object,
    required: false,
    default: undefined
  },
});

const menuItems = ref([
  {
    label: "Clear Selected",
    icon: "fas fa-times fa-fw",
    command: () => {
      selectedFilters.value = [];
    },
  },
  {
    label: "Select All Visible",
    icon: "fas fa-check-double fa-fw",
    command: () => {
      selectedFilters.value = filteredCause.value;
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
    disabled: computed(() => filterSelectedDids.value.length == 0),
  },
]);

onMounted(async () => {
  await getPersistedParams();
  fetchFilteredMessages();
  setupWatchers();
});

const { data: response, fetchFilteredSummaryByMessage } = useFiltered();

const showAllTab = (flowName, flowType, cause) => {
  emit("showAllTab", flowName, flowType, cause);
};

const filterSelectedDids = computed(() => {
  let dids = selectedFilters.value.map((selectedFiltered) => {
    return selectedFiltered.dids;
  });
  let allDids = [].concat.apply([], dids);

  return [...new Set(allDids)];
});

const fetchFilteredMessages = async () => {
  getPersistedParams();
  loading.value = true;
  await fetchFilteredSummaryByMessage(offset.value, perPage.value, sortDirection.value, props.flow?.name);
  filteredCause.value = response.value.countPerMessage;
  totalFilteredMessage.value = response.value.totalCount;
  loading.value = false;
};

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

const onRowContextMenu = (event) => {
  if (selectedFilters.value.length <= 0) {
    selectedFilters.value = [event.data];
  }
};

const onPanelRightClick = (event) => {
  menu.value.show(event);
};

defineExpose({
  fetchFilteredMessages,
});

const onSort = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchFilteredMessages();
};

const setupWatchers = () => {
  watch(
    () => props.flow,
    () => {
      fetchFilteredMessages();
    }
  );

  watch(
    () => props.acknowledged,
    () => {
      selectedFilters.value = [];
      fetchFilteredMessages();
    }
  );
};
const onPage = async (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  setPersistedParams();
  await nextTick();
  fetchFilteredMessages();
  emit("refreshFilters");
};

const getPersistedParams = async () => {
  let state = useStorage("filtered-page-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  perPage.value = state.value.perPage || 20;
};

const setPersistedParams = () => {
  let state = useStorage("filtered-page-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  state.value = {
    perPage: perPage.value,
  };
};
</script>

<style lang="scss"></style>