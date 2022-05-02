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
  <div class="search-page">
    <div>
      <PageHeader heading="DeltaFile Search">
        <div class="time-range btn-toolbar mb-2 mb-md-0">
          <Button class="p-button-text p-button-sm p-button-secondary" disabled>{{ shortTimezone() }}</Button>
          <Calendar v-model="startTimeDate" :show-time="true" :show-seconds="true" :manual-input="true" input-class="deltafi-input-field" @input="updateInputStartTime" />
          <span class="mt-2 ml-2">&mdash;</span>
          <Calendar v-model="endTimeDate" :show-time="true" :show-seconds="true" :manual-input="true" input-class="deltafi-input-field ml-2" @input="updateInputEndTime" />
          <Button class="p-button-sm p-button p-button-outlined ml-3" @click="fetchDeltaFilesData()">Search</Button>
        </div>
      </PageHeader>
    </div>
    <div class="row mb-3">
      <div class="col-12">
        <CollapsiblePanel header="Search Options" :collapsed="collapsedSearchOption">
          <template #icons>
            <Button class="p-panel-header-icon p-link p-mr-2" @click="optionMenuToggle">
              <span class="fas fa-cog" />
            </Button>
            <Menu id="config_menu" ref="optionMenu" :model="items" :popup="true" />
          </template>
          <div class="row align-items-center py-2">
            <div class="col-1 text-right">
              <label for="fileNameId">Filename:</label>
            </div>
            <div class="col-md-auto">
              <!-- TODO: GitLab issue "Fix multi-select dropdown data bouncing" (https://gitlab.com/systolic/deltafi/deltafi-ui/-/issues/96). Placeholder hacky fix to stop the bouncing of data within the field. -->
              <Dropdown id="fileNameId" v-model="fileNameOptionSelected" :placeholder="fileNameOptionSelected ? fileNameOptionSelected.name + ' ' : 'Select a File Name'" :options="fileNameOptions" option-label="name" :filter="true" :show-clear="true" class="deltafi-input-field min-width" />
            </div>
          </div>
          <div class="row align-items-center py-2">
            <div class="col-1 text-right align-text-bottom">
              <label for="flowId">Ingress Flow:</label>
            </div>
            <div class="col-md-auto">
              <!-- TODO: GitLab issue "Fix multi-select dropdown data bouncing" (https://gitlab.com/systolic/deltafi/deltafi-ui/-/issues/96). Placeholder hacky fix to stop the bouncing of data within the field. -->
              <Dropdown id="flowId" v-model="flowOptionSelected" :placeholder="flowOptionSelected ? flowOptionSelected.name + ' ' : 'Select an Ingress Flow'" :options="flowOptions" option-label="name" show-clear :editable="false" class="deltafi-input-field min-width" />
            </div>
          </div>
          <div class="row align-items-center py-2">
            <div class="col-1 text-right">
              <label for="stageId">Stage:</label>
            </div>
            <div class="col-md-auto">
              <!-- TODO: GitLab issue "Fix multi-select dropdown data bouncing" (https://gitlab.com/systolic/deltafi/deltafi-ui/-/issues/96). Placeholder hacky fix to stop the bouncing of data within the field. -->
              <Dropdown id="stageId" v-model="stageOptionSelected" :placeholder="stageOptionSelected ? stageOptionSelected.name + ' ' : 'Select a Stage'" :options="stageOptions" option-label="name" show-clear :editable="false" class="deltafi-input-field min-width" />
            </div>
          </div>
          <div class="row align-items-center py-2">
            <div class="col-1 text-right pl-0 pr-3">
              <label for="actionTypeId">Action Type:</label>
            </div>
            <div class="col-md-auto">
              <!-- TODO: GitLab issue "Fix multi-select dropdown data bouncing" (https://gitlab.com/systolic/deltafi/deltafi-ui/-/issues/96). Placeholder hacky fix to stop the bouncing of data within the field. -->
              <Dropdown id="actionTypeId" v-model="actionTypeOptionSelected" :placeholder="actionTypeOptionSelected ? actionTypeOptionSelected.name + ' ' : 'Select an Action Type'" :options="actionTypeOptions" option-label="name" :filter="true" :show-clear="true" class="deltafi-input-field min-width" />
            </div>
          </div>
          <div class="row align-items-center py-2">
            <div class="col-1" />
            <div class="col-1">
              <span class="float-left">
                <i v-if="recordCount" v-badge="recordCount" class="pi align-top p-text-secondary float-right icon-index" style="font-size: 2rem" />
                <Button type="button" label="Search" class="p-button-sm p-button p-button-outlined float-right" @click="fetchDeltaFilesData()" />
              </span>
            </div>
          </div>
        </CollapsiblePanel>
      </div>
    </div>
    <Panel header="Results" class="table-panel results">
      <template #icons>
        <Paginator v-if="results.length > 0" :rows="10" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalRecords" :rows-per-page-options="[10, 20, 50, 100, 1000]" class="p-panel-header" style="float: left" @page="onPage($event)"></Paginator>
      </template>
      <DataTable responsive-layout="scroll" class="p-datatable p-datatable-sm p-datatable-gridlines" striped-rows :value="results" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalRecords" :row-class="actionRowClass" @sort="onSort($event)">
        <template #empty>No DeltaFiles in the selected time range.</template>
        <template #loading>Loading DeltaFiles. Please wait.</template>
        <Column field="did" header="DID (UUID)">
          <template #body="tData">
            <router-link class="monospace" :to="{ path: 'viewer/' + tData.data.did }">{{ tData.data.did }}</router-link>
          </template>
        </Column>
        <Column field="sourceInfo.filename" header="Filename" :sortable="true" class="filename-column" />
        <Column field="sourceInfo.flow" header="Ingress Flow" :sortable="true" />
        <Column field="stage" header="Stage" :sortable="true" />
        <Column field="created" header="Created" :sortable="true">
          <template #body="row">
            <Timestamp :timestamp="row.data.created" />
          </template>
        </Column>
        <Column field="modified" header="Modified" :sortable="true">
          <template #body="row">
            <Timestamp :timestamp="row.data.modified" />
          </template>
        </Column>
        <Column field="elapsed" header="Elapsed" :sortable="false">
          <template #body="row">{{ row.data.elapsed }}</template>
        </Column>
      </DataTable>
    </Panel>
  </div>
</template>

<script setup>
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import Button from "primevue/button";
import Calendar from "primevue/calendar";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dropdown from "primevue/dropdown";
import Menu from "primevue/menu";
import Panel from "primevue/panel";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import Paginator from "primevue/paginator";
import PageHeader from "@/components/PageHeader.vue";
import Timestamp from "@/components/Timestamp.vue";
import useDeltaFilesQueryBuilder from "@/composables/useDeltaFilesQueryBuilder";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { ref, computed, watch, onMounted, nextTick, inject } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";
import _ from "lodash";

dayjs.extend(utc);

const { getDeltaFileSearchData, getRecordCount, getDeltaFiFileNames, getEnumValuesByEnumType, getConfigByType } = useDeltaFilesQueryBuilder();
const { duration, formatTimestamp, shortTimezone, convertLocalDateToUTC } = useUtilFunctions();

const uiConfig = inject("uiConfig");

const optionMenu = ref();
const startTimeDate = ref(new Date());
const endTimeDate = ref(new Date());
startTimeDate.value.setHours(0, 0, 0, 0);
endTimeDate.value.setHours(23, 59, 59, 999);
const defaultStartTimeDate = startTimeDate.value;
const defaultEndTimeDate = endTimeDate.value;
const fileNameOptions = ref([]);
const fileNameOptionSelected = ref(null);
const actionTypeOptions = ref([]);
const actionTypeOptionSelected = ref(null);
const flowOptions = ref([]);
const flowOptionSelected = ref(null);
const stageOptions = ref([]);
const stageOptionSelected = ref(null);
const loading = ref(true);
const totalRecords = ref(0);
const recordCount = ref("");
const collapsedSearchOption = ref(true);
const tableData = ref([]);
const fileName = ref(null);
const stageName = ref(null);
const actionName = ref(null);
const flowName = ref(null);
const offset = ref(0);
const perPage = ref(10);
const sortField = ref("modified");
const sortDirection = ref("DESC");
const timestampFormat = "YYYY-MM-DD HH:mm:ss";

const startDateISOString = computed(() => {
  return uiConfig.useUTC ? convertLocalDateToUTC(startTimeDate.value).toISOString() : startTimeDate.value.toISOString();
});

const endDateISOString = computed(() => {
  return uiConfig.useUTC ? convertLocalDateToUTC(endTimeDate.value).toISOString() : endTimeDate.value.toISOString();
});

const items = ref([
  {
    label: "Options",
    items: [
      {
        label: "Clear Options",
        icon: "fas fa-times",
        command: () => {
          actionTypeOptionSelected.value = null;
          fileNameOptionSelected.value = null;
          flowOptionSelected.value = null;
          stageOptionSelected.value = null;
          fetchDeltaFilesData();
        },
      },
    ],
  },
]);

const fetchFileNames = async () => {
  let fileNameDataArray = [];
  let fetchFileNames = await getDeltaFiFileNames(startDateISOString.value, endDateISOString.value, fileName.value, stageName.value, actionName.value, flowName.value);
  let deltaFilesObjectsArray = fetchFileNames.data.deltaFiles.deltaFiles;
  for (const deltaFiObject of deltaFilesObjectsArray) {
    fileNameDataArray.push({ name: deltaFiObject.sourceInfo.filename });
  }

  fileNameOptions.value = _.uniqBy(fileNameDataArray, "name");
};

watch(startTimeDate, () => {
  fetchFileNames();
  fetchRecordCount();
});
watch(endTimeDate, () => {
  fetchFileNames();
  fetchRecordCount();
});
watch(fileNameOptionSelected, () => {
  fetchRecordCount();
});
watch(actionTypeOptionSelected, () => {
  fetchRecordCount();
});
watch(flowOptionSelected, () => {
  fetchRecordCount();
});
watch(stageOptionSelected, () => {
  fetchRecordCount();
});

const updateInputStartTime = async (e) => {
  await nextTick();
  if (dayjs(e.target.value.trim()).isValid()) {
    startTimeDate.value = new Date(formatTimestamp(e.target.value.trim(), timestampFormat));
  } else {
    startTimeDate.value = defaultStartTimeDate;
  }
};
const updateInputEndTime = async (e) => {
  await nextTick();
  if (dayjs(e.target.value.trim()).isValid()) {
    endTimeDate.value = new Date(formatTimestamp(e.target.value.trim(), timestampFormat));
  } else {
    endTimeDate.value = defaultEndTimeDate;
  }
};

onMounted(() => {
  getPersistedParams();
  fetchFileNames();
  fetchConfigTypes();
  fetchStages();
  fetchDeltaFilesData();
});

const optionMenuToggle = (event) => {
  optionMenu.value.toggle(event);
};

const fetchConfigTypes = async () => {
  const flowTypes = ["INGRESS_FLOW"];
  let enumsConfigTypes = await getEnumValuesByEnumType("ConfigType");
  let configTypeNames = enumsConfigTypes.data.__type.enumValues;
  let actionTypes = configTypeNames.map((a) => a.name).filter((name) => name.includes("ACTION"));

  fetchActions(actionTypes);
  fetchFlows(flowTypes);
};

const fetchActions = async (actionTypes) => {
  for (const actionType of actionTypes) {
    let actionData = await getConfigByType(actionType);
    let actionDataValues = actionData.data.deltaFiConfigs;
    actionTypeOptions.value = _.concat(actionTypeOptions.value, actionDataValues);
    actionTypeOptions.value = _.sortBy(actionTypeOptions.value, ["name"]);
  }
};

const fetchFlows = async (flowTypes) => {
  for (const flowType of flowTypes) {
    let flowData = await getConfigByType(flowType);
    let flowDataValues = flowData.data.deltaFiConfigs;
    flowOptions.value = _.concat(flowOptions.value, flowDataValues);
    flowOptions.value = _.uniqBy(flowOptions.value, "name");
    flowOptions.value = _.sortBy(flowOptions.value, ["name"]);
  }
};

const fetchStages = async () => {
  let enumsStageTypes = await getEnumValuesByEnumType("DeltaFileStage");
  stageOptions.value = enumsStageTypes.data.__type.enumValues;
};

const fetchDeltaFilesData = async () => {
  setQueryParams();
  setPersistedParams();

  loading.value = true;
  fetchRecordCount();
  let data = await getDeltaFileSearchData(startDateISOString.value, endDateISOString.value, offset.value, perPage.value, sortField.value, sortDirection.value, fileName.value, stageName.value, actionName.value, flowName.value);
  tableData.value = data.data.deltaFiles.deltaFiles;
  loading.value = false;
  totalRecords.value = data.data.deltaFiles.totalCount;
};

const fetchRecordCount = async () => {
  setQueryParams();

  let fetchRecordCount = await getRecordCount(startDateISOString.value, endDateISOString.value, fileName.value, stageName.value, actionName.value, flowName.value);
  recordCount.value = fetchRecordCount.data.deltaFiles.totalCount.toString();
};

const results = computed(() => {
  return tableData.value.map((row) => {
    const timeElapsed = new Date(row.modified) - new Date(row.created);
    return {
      ...row,
      elapsed: duration(timeElapsed),
    };
  });
});

const setQueryParams = () => {
  fileName.value = fileNameOptionSelected.value ? fileNameOptionSelected.value.name : null;
  stageName.value = stageOptionSelected.value ? stageOptionSelected.value.name : null;
  actionName.value = actionTypeOptionSelected.value ? actionTypeOptionSelected.value.name : null;
  flowName.value = flowOptionSelected.value ? flowOptionSelected.value.name : null;
};

const onSort = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  sortField.value = event.sortField;
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchDeltaFilesData();
};

const actionRowClass = (data) => {
  return data.stage === "ERROR" ? "table-danger action-error" : null;
};

const onPage = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  fetchDeltaFilesData();
};

const getPersistedParams = () => {
  startTimeDate.value = new Date(state.value.startTimeDateState ? state.value.startTimeDateState : startTimeDate.value);
  endTimeDate.value = new Date(state.value.endTimeDateState ? state.value.endTimeDateState : endTimeDate.value);
  fileNameOptionSelected.value = state.value.fileNameOptionState ? { name: state.value.fileNameOptionState } : null;
  stageOptionSelected.value = state.value.stageOptionState ? { name: state.value.stageOptionState } : null;
  actionTypeOptionSelected.value = state.value.actionTypeOptionState ? { name: state.value.actionTypeOptionState } : null;
  flowOptionSelected.value = state.value.flowOptionState ? { name: state.value.flowOptionState } : null;

  // If any of the fields are true it means we have persisted values. Don't collapse the search options panel so the user can see
  // what search options are being used.
  if (fileNameOptionSelected.value || stageOptionSelected.value || actionTypeOptionSelected.value || flowOptionSelected.value) {
    collapsedSearchOption.value = false;
  } else {
    collapsedSearchOption.value = true;
  }
};
const state = useStorage("advanced-search-options-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });

const setPersistedParams = () => {
  state.value = {
    startTimeDateState: startTimeDate.value ? startTimeDate.value : null,
    endTimeDateState: endTimeDate.value ? endTimeDate.value : null,
    fileNameOptionState: fileNameOptionSelected.value ? fileNameOptionSelected.value.name : null,
    stageOptionState: stageOptionSelected.value ? stageOptionSelected.value.name : null,
    actionTypeOptionState: actionTypeOptionSelected.value ? actionTypeOptionSelected.value.name : null,
    flowOptionState: flowOptionSelected.value ? flowOptionSelected.value.name : null,
  };
};
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-search-page.scss";
</style>
