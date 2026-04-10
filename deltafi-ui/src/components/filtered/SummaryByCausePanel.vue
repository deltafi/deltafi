<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
      <Paginator v-if="filteredCause.length > 0" :rows="perPage" :first="getPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalFilteredMessage" :rows-per-page-options="[10, 20, 50, 100, 1000]" @page="onPage($event)" />
      <Button severity="secondary" variant="text" class="p-panel-header-icon mr-2" @click="toggleMenu">
        <span class="fas fa-bars" />
      </Button>
      <Menu ref="menu" :model="menuItems" :popup="true" />
    </template>
    <DataTable id="filteredSummaryTable" v-model:expanded-rows="expandedCauseGroups" v-model:selection="selectedCauseGroups" aria-label="Filtered summary by cause" data-key="message" selection-mode="multiple" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="groupCauseData" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalFilteredMessage" :row-hover="true" @row-contextmenu="onGroupContextMenu" @sort="onSort($event)" @row-select="onGroupRowSelect" @row-unselect="onGroupRowUnSelect">
      <template #empty> No results to display. </template>
      <template #loading> Loading. Please wait... </template>
      <Column v-if="groupCauseData.length > 0" expander class="expander-column">
        <template #body="{ data }">
          <div @click.stop="onExpandClicked(data)">
            <ChevronSwitch :expanded-rows="expandedCauseGroups" :row="data" class="text-muted" />
          </div>
        </template>
      </Column>
      <Column field="message" header="Cause" sortable>
        <template #body="{ data }">
          <a class="monospace" @click="showAllTab(null, null, data.message)">{{ data.message }}</a>
        </template>
      </Column>
      <Column field="count" header="Count" sortable />
      <template #expansion="slotProps">
        <div class="filtered-Subtable">
          <DataTable :value="slotProps.data.flows" v-model:selection="selectedFilters" aria-label="Filtered flows by cause" data-key="flow" selection-mode="multiple" class="p-datatable-sm p-datatable-gridlines" @row-select="onRowSelect" @row-unselect="onRowUnSelect" @row-contextmenu="onRowContextMenu">
            <Column field="flow" header="Flow" sortable class="filename-column" />
            <Column field="type" header="Flow Type" sortable />
            <Column field="count" header="Count" sortable />
            <Column field="message" header="Cause" sortable>
              <template #body="{ data }">
                <a class="monospace" @click="showAllTab(data.flow, data.type, data.message)">{{ data.message }}</a>
              </template>
            </Column>
          </DataTable>
        </div>
      </template>
    </DataTable>
  </Panel>
  <ReplayDialog ref="replayDialog" :did="filterSelectedDids" @refresh-page="fetchFilteredMessages()" />
</template>

<script setup>
import ChevronSwitch from "@/components/errors/ChevronSwitch.vue";
import ReplayDialog from "@/components/ReplayDialog.vue";
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

const replayDialog = ref();
const loading = ref(true);
const menu = ref();
const filteredCause = ref([]);
const totalFilteredMessage = ref(0);
const offset = ref(0);
const perPage = ref();
const page = ref(null);
const sortField = ref("NAME");
const sortDirection = ref("DESC");
const expandedCauseGroups = ref({});
const selectedCauseGroups = ref([]);
const selectedFilters = ref([]);
const emit = defineEmits(["refreshFilters", "showAllTab"]);
const props = defineProps({
  active: {
    type: Boolean,
    required: true,
  },
  flow: {
    type: Object,
    required: false,
    default: undefined,
  },
  queryParams: {
    type: Object,
    required: true,
  },
});

const unSelectAllRows = () => {
  selectedCauseGroups.value = [];
  selectedFilters.value = [];
};

const menuItems = ref([
  {
    label: "Clear Selected",
    icon: "fas fa-times fa-fw",
    command: () => {
      unSelectAllRows();
    },
  },
  {
    label: "Select All Visible",
    icon: "fas fa-check-double fa-fw",
    command: () => {
      selectedCauseGroups.value = groupCauseData.value;
      selectedFilters.value = filteredCause.value;
    },
  },
  {
    separator: true,
    visible: computed(() => hasSomePermissions("DeltaFileReplay")),
  },
  {
    label: "Replay Selected",
    icon: "fas fa-sync fa-fw",
    command: () => {
      replayDialog.value.showConfirmDialog();
    },
    visible: computed(() => hasPermission("DeltaFileReplay")),
    disabled: computed(() => filterSelectedDids.value.length == 0),
  },
]);

onMounted(async () => {
  await getPersistedParams();
  if (props.active) {
    fetchFilteredMessages();
  }
  setupWatchers();
});

const { data: response, fetchFilteredSummaryByMessage } = useFiltered();

const showAllTab = (flowName, flowType, cause) => {
  emit("showAllTab", flowName, flowType, cause);
};

const filterSelectedDids = computed(() => {
  const dids = selectedFilters.value.map((selectedFiltered) => {
    return selectedFiltered.dids;
  });

  return _.flatten([...new Set(dids)]);
});

const fetchFilteredMessages = async () => {
  getPersistedParams();
  loading.value = true;
  await fetchFilteredSummaryByMessage(props.queryParams, offset.value, perPage.value, sortField.value, sortDirection.value, props.flow?.name);
  filteredCause.value = response.value.countPerMessage;
  totalFilteredMessage.value = response.value.totalCount;

  // If offset exceeds total count, reset to page 1 and re-fetch
  if (filteredCause.value.length === 0 && totalFilteredMessage.value > 0 && offset.value >= totalFilteredMessage.value) {
    offset.value = 0;
    page.value = 1;
    setPersistedParams();
    await fetchFilteredSummaryByMessage(props.queryParams, offset.value, perPage.value, sortField.value, sortDirection.value, props.flow?.name);
    filteredCause.value = response.value.countPerMessage;
    totalFilteredMessage.value = response.value.totalCount;
  }

  loading.value = false;
};

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

const onGroupContextMenu = (event) => {
  const newSelectedGroupFilters = [];
  const tmpSelectedCauseGroups = JSON.parse(JSON.stringify(selectedCauseGroups.value));
  const foundGroupCause = _.find(tmpSelectedCauseGroups, { message: event.data.message });

  if (!foundGroupCause) {
    newSelectedGroupFilters.push(event.data);
  }
  if (newSelectedGroupFilters.length > 0) {
    selectedCauseGroups.value = [...selectedCauseGroups.value, ...newSelectedGroupFilters];
  }

  onGroupRowSelect(event);
};

const onRowContextMenu = (event) => {
  const newSelectedFilters = [];
  const tmpSelectedFilters = JSON.parse(JSON.stringify(selectedFilters.value));
  const foundObject = _.find(tmpSelectedFilters, event.data);

  if (!foundObject) {
    newSelectedFilters.push(event.data);
  }
  if (newSelectedFilters.length > 0) {
    selectedFilters.value = [...selectedFilters.value, ...newSelectedFilters];
  }

  onRowSelect(event);
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
  sortField.value = event.sortField === "flow" ? "NAME" : event.sortField.toUpperCase();
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchFilteredMessages();
};

const groupCauseData = computed(() => {
  return filteredCause.value.map((item) => ({
    message: item.message,
    count: item.count,
    flows: (item.flows || []).map((flow) => ({
      ...flow,
      message: item.message,
    })),
  }));
});

const setupWatchers = () => {
  watch(
    () => props.flow,
    () => {
      if (props.active) {
        unSelectAllRows();
        fetchFilteredMessages();
      }
    }
  );
  watch(
    () => props.active,
    (active) => {
      if (active) {
        fetchFilteredMessages();
      }
    }
  );
};

const onPage = async (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  page.value = event.page + 1;
  setPersistedParams();
  await nextTick();
  fetchFilteredMessages();
  emit("refreshFilters");
};

const getPage = computed(() => {
  return page.value === null || page.value === undefined ? 0 : (page.value - 1) * perPage.value;
});

const getPersistedParams = async () => {
  const state = useStorage("filtered-page-by-cause-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  perPage.value = state.value.perPage || 20;
  page.value = state.value.page || 1;
  offset.value = getPage.value;
};

const setPersistedParams = () => {
  const state = useStorage("filtered-page-by-cause-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  state.value = {
    perPage: perPage.value,
    page: page.value,
  };
};

// When a group row is selected, add all its flows to selectedFilters
const onGroupRowSelect = (event) => {
  const newSelectedFilters = [];
  for (const flow of event.data.flows) {
    const tmpSelectedFilters = JSON.parse(JSON.stringify(selectedFilters.value));
    const foundObject = _.find(tmpSelectedFilters, flow);

    if (!foundObject) {
      newSelectedFilters.push(flow);
    }
  }
  if (newSelectedFilters.length > 0) {
    selectedFilters.value = [...selectedFilters.value, ...newSelectedFilters];
  }
};

// When a group row is unselected, remove all its flows from selectedFilters
const onGroupRowUnSelect = (event) => {
  for (const flow of event.data.flows) {
    let tmpSelectedFilters = JSON.parse(JSON.stringify(selectedFilters.value));
    tmpSelectedFilters = _.filter(tmpSelectedFilters, function (o) {
      return !_.isEqual(JSON.stringify(o), JSON.stringify(flow));
    });
    selectedFilters.value = tmpSelectedFilters;
  }
};

// When a flow row is selected and all flows in its group are selected, select the group too
const onRowSelect = (event) => {
  const filteredSelectedFlows = _.filter(selectedFilters.value, { message: event.data.message });
  const foundGroupCause = _.find(groupCauseData.value, { message: event.data.message });

  const tmpSelectedCauseGroups = JSON.parse(JSON.stringify(selectedCauseGroups.value));
  const foundObject = _.find(selectedCauseGroups.value, { message: event.data.message });
  const countOfFilteredSelected = _.sumBy(filteredSelectedFlows, function (o) {
    return o.count;
  });
  if (_.isEqual(foundGroupCause.count, countOfFilteredSelected)) {
    if (_.isEmpty(foundObject)) {
      tmpSelectedCauseGroups.push(foundGroupCause);
      selectedCauseGroups.value = tmpSelectedCauseGroups;
    }
  }
};

// When a flow row is unselected, remove its group from selectedCauseGroups
const onRowUnSelect = (event) => {
  const filteredSelectedFlows = _.filter(selectedFilters.value, { message: event.data.message });
  const foundGroupCause = _.find(groupCauseData.value, { message: event.data.message });

  let tmpSelectedCauseGroups = JSON.parse(JSON.stringify(selectedCauseGroups.value));
  const foundObject = _.find(selectedCauseGroups.value, { message: event.data.message });
  const countOfFilteredSelected = _.sumBy(filteredSelectedFlows, function (o) {
    return o.count;
  });
  if (!_.isEqual(foundGroupCause.count, countOfFilteredSelected)) {
    if (!_.isEmpty(foundObject)) {
      tmpSelectedCauseGroups = _.filter(tmpSelectedCauseGroups, function (o) {
        return !_.isEqual(JSON.stringify(o), JSON.stringify(foundGroupCause));
      });
      selectedCauseGroups.value = tmpSelectedCauseGroups;
    }
  }
};

const isExpanded = (data) => {
  return _.has(expandedCauseGroups.value, data.message);
};

const onExpandClicked = (data) => {
  const tmpExpandedGroups = JSON.parse(JSON.stringify(expandedCauseGroups.value));
  if (isExpanded(data)) {
    delete tmpExpandedGroups[data.message.toString()];
    expandedCauseGroups.value = tmpExpandedGroups;
  } else {
    tmpExpandedGroups[data.message.toString()] = true;
    expandedCauseGroups.value = tmpExpandedGroups;
  }
};
</script>

<style />
