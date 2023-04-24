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
  <div class="deltafile-search-page">
    <div>
      <PageHeader heading="DeltaFile Search">
        <div class="time-range btn-toolbar mb-2 mb-md-0">
          <Button class="p-button-text p-button-sm p-button-secondary" disabled>{{ shortTimezone() }}</Button>
          <Calendar v-model="startTimeDate" :show-time="true" :show-seconds="true" :manual-input="true" input-class="deltafi-input-field" @input="updateInputStartTime" />
          <span class="mt-2 ml-2">&mdash;</span>
          <Calendar v-model="endTimeDate" :show-time="true" :show-seconds="true" :manual-input="true" input-class="deltafi-input-field ml-2" @input="updateInputEndTime" />
          <Button class="p-button p-button-secondary p-button-outlined deltafi-input-field ml-3" icon="far fa-regular fa-calendar" label="Today" @click="setDateTimeToday()" />
          <Button class="p-button p-button-outlined deltafi-input-field ml-3" :icon="refreshButtonIcon" label="Refresh" @click="fetchDeltaFilesData()" />
        </div>
      </PageHeader>
    </div>
    <div class="row mb-3">
      <div class="col-12">
        <CollapsiblePanel header="Advanced Search Options" :collapsed="collapsedSearchOption">
          <template #icons>
            <Button class="p-panel-header-icon p-link p-mr-2" @click="optionMenuToggle">
              <span class="fas fa-cog" />
            </Button>
            <Menu id="config_menu" ref="optionMenu" :model="items" :popup="true" />
          </template>
          <div class="search-options-wrapper">
            <div class="flex-row">
              <div class="flex-column">
                <label for="fileNameId">Filename:</label>
                <InputText v-model.trim="fileName" class="p-inputtext input-area-height responsive-width" placeholder="Filename" />
                <label for="flowId" class="mt-2">Ingress Flow:</label>
                <MultiSelect id="flowId" v-model="flowOptionSelected" :options="flowOptions" placeholder="Select an Ingress Flow" class="deltafi-input-field responsive-width" />
                <label for="egressFlowId" class="mt-2">Egress Flow:</label>
                <MultiSelect id="egressFlowId" v-model="egressFlowOptionSelected" :options="egressFlowOptions" placeholder="Select an Egress Flow" class="deltafi-input-field responsive-width" />
                <label for="stageId" class="mt-2">Size:</label>
                <div class="size-container">
                  <Dropdown v-model="sizeTypeSelected" :options="sizeTypes" option-label="name" style="width: 8rem" class="deltafi-input-field mr-2" />
                  <InputNumber v-model="sizeMin" class="p-inputnumber input-area-height" :input-style="{ width: '6rem' }" placeholder="Min" /> -
                  <InputNumber v-model="sizeMax" class="p-inputnumber input-area-height" :input-style="{ width: '6rem' }" placeholder="Max" />
                  <Dropdown v-model="sizeUnitSelected" :options="sizeUnits" option-label="name" class="deltafi-input-field ml-2" />
                </div>
              </div>
              <div class="flex-column flex-column-small">
                <label for="testModeState">Test Mode:</label>
                <Dropdown id="testModeState" v-model="testModeOptionSelected" placeholder="Select if in Test Mode" :options="testModeOptions" option-label="name" :show-clear="true" class="deltafi-input-field min-width" />
                <label for="isReplayable" class="mt-2">Replayable:</label>
                <Dropdown id="isReplayable" v-model="isReplayableSelected" placeholder="Select if Replayable" :options="isReplayableOptions" option-label="name" :show-clear="true" class="deltafi-input-field min-width" />
                <label for="egressedState" class="mt-2">Egressed:</label>
                <Dropdown id="egressState" v-model="egressedOptionSelected" placeholder="Select if Egressed" :options="egressedOptions" option-label="name" :show-clear="true" class="deltafi-input-field min-width" />
                <label for="filteredState" class="mt-2">Filtered:</label>
                <Dropdown id="filteredState" v-model="filteredOptionSelected" placeholder="Select if Filtered" :options="filteredOptions" option-label="name" :show-clear="true" class="deltafi-input-field min-width" />
                <label for="filteredReasonId" class="mt-2">Filtered Cause:</label>
                <InputText v-model="filteredCause" class="p-inputtext input-area-height" placeholder="Filtered Cause" />
              </div>
              <div class="flex-column">
                <label for="requeueMinId">Requeue Count:</label>
                <InputNumber v-model="requeueMin" class="p-inputnumber input-area-height" :input-style="{ width: '6rem' }" placeholder="Min" />
                <label for="stageId" class="mt-2">Stage:</label>
                <Dropdown id="stageId" v-model="stageOptionSelected" placeholder="Select a Stage" :options="stageOptions" option-label="name" show-clear :editable="false" class="deltafi-input-field min-width" />
                <label for="filteredState" class="mt-2">Domain:</label>
                <Dropdown id="domain" v-model="domainOptionSelected" placeholder="Select a Domain" :options="domainOptions" option-label="name" show-clear :editable="false" class="deltafi-input-field min-width" />
                <label for="metadataState" class="mt-2">Indexed Metadata:</label>
                <div class="metadata-chips">
                  <Chip v-for="item in metadataArray" :key="item" v-tooltip.top="{ value: invalidMetadataTooltip(item.key), disabled: item.valid }" removable class="mr-2 mb-1" :class="{ 'invalid-chip': !item.valid, 'valid-chip': item.valid }" @remove="removeMetadataItem(item)"> {{ item.key }}: {{ item.value }} </Chip>
                  <Chip class="add-metadata-btn" @click="showIndexedMetadataOverlay">
                    &nbsp;
                    <i class="pi pi-plus"></i>
                    &nbsp;
                  </Chip>
                </div>
                <OverlayPanel ref="indexedMetadataOverlay">
                  <Dropdown v-model="newMetadataKey" placeholder="Key" :options="metadataKeysOptions" option-label="key" style="width: 15rem" @keyup.enter="addMetadataItemEvent" /> :
                  <InputText v-model="newMetadataValue" placeholder="Value" style="width: 15rem" @keyup.enter="addMetadataItemEvent" />
                </OverlayPanel>
              </div>
            </div>
          </div>
        </CollapsiblePanel>
      </div>
    </div>
    <Panel header="Results" class="table-panel results"  @contextmenu="onPanelRightClick">
      <ContextMenu ref="menu" :model="menuItems" />
      <template #icons>
        <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
          <span class="fas fa-bars" />
        </Button>
        <Menu ref="menu" :model="menuItems" :popup="true" />
        <Paginator v-if="results.length > 0" :rows="perPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" :current-page-report-template="pageReportTemplate" :total-records="totalRecords" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage($event)"></Paginator>
      </template>
      <DataTable v-model:selection="selectedDids" selection-mode="multiple" responsive-layout="scroll" class="p-datatable p-datatable-sm p-datatable-gridlines" striped-rows :value="results" :loading="loading" loading-icon="pi pi-spinner" :rows="perPage" :lazy="true" :total-records="totalRecords" :row-class="actionRowClass" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
        <template #empty>No DeltaFiles match the provided search criteria.</template>
        <template #loading>Loading results. Please wait.</template>
        <Column field="did" header="DID" class="did-column">
          <template #body="{ data }">
            <DidLink :did="data.did" />
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
        <Column field="totalBytes" header="Size" :sortable="true">
          <template #body="{ data }">
            <FormattedBytes :bytes="data.totalBytes" />
          </template>
        </Column>
        <Column field="elapsed" header="Elapsed" :sortable="false">
          <template #body="row">{{ row.data.elapsed }}</template>
        </Column>
      </DataTable>
    </Panel>
  </div>
  <MetadataDialog ref="metadataDialog" :did="filterSelectedDids" @update="fetchDeltaFilesData" />
  <AnnotateDialog ref="annotateDialog" :dids="filterSelectedDids" @refresh-page="fetchDeltaFilesData()"/>

</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DidLink from "@/components/DidLink.vue";
import FormattedBytes from "@/components/FormattedBytes.vue";
import PageHeader from "@/components/PageHeader.vue";
import Timestamp from "@/components/Timestamp.vue";
import useDeltaFilesQueryBuilder from "@/composables/useDeltaFilesQueryBuilder";
import useDomains from "@/composables/useDomains";
import useFlows from "@/composables/useFlows";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { ref, computed, watch, onMounted, nextTick, inject, onBeforeMount } from "vue";
import { useRoute } from "vue-router";
import { useStorage, StorageSerializers, useUrlSearchParams } from "@vueuse/core";
import _ from "lodash";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

import Button from "primevue/button";
import Calendar from "primevue/calendar";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dropdown from "primevue/dropdown";
import MultiSelect from "primevue/multiselect";
import Menu from "primevue/menu";
import Paginator from "primevue/paginator";
import Panel from "primevue/panel";
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import Chip from "primevue/chip";
import OverlayPanel from "primevue/overlaypanel";
import ContextMenu from "primevue/contextmenu";
import MetadataDialog from "@/components/MetadataDialog.vue";
import AnnotateDialog from "@/components/AnnotateDialog.vue";


dayjs.extend(utc);
const hasPermission = inject("hasPermission");
const params = useUrlSearchParams("history");
const { getDeltaFileSearchData, getEnumValuesByEnumType } = useDeltaFilesQueryBuilder();
const { duration, formatTimestamp, shortTimezone } = useUtilFunctions();
const { getDomains, getIndexedMetadataKeys } = useDomains();
const { ingressFlows: flowOptions, fetchIngressFlowNames, egressFlows: egressFlowOptions, fetchEgressFlowNames } = useFlows();
const route = useRoute();
const useURLSearch = ref(false);
const uiConfig = inject("uiConfig");
const optionMenu = ref();
const selectedDids = ref([]);
const menu = ref();
const metadataDialog = ref();
const annotateDialog = ref();

const maxTotalRecords = 50000;
const pageReportTemplate = computed(() => {
  const total = totalRecords.value == maxTotalRecords ? "many" : "{totalRecords}";
  return `{first} - {last} of ${total}`;
});

// Dates
const defaultStartTimeDate = computed(() => {
  const date = dayjs().utc();
  return (uiConfig.useUTC ? date : date.local()).startOf("day");
});
const defaultEndTimeDate = computed(() => {
  const date = dayjs().utc();
  return (uiConfig.useUTC ? date : date.local()).endOf("day");
});
const startTimeDate = ref();
const startTimeDateIsDefault = computed(() => {
  return startTimeDate.value.getTime() === new Date(defaultStartTimeDate.value.format(timestampFormat)).getTime();
});
const endTimeDate = ref();
const endTimeDateIsDefault = computed(() => {
  return endTimeDate.value.getTime() === new Date(defaultEndTimeDate.value.format(timestampFormat)).getTime();
});
const setDateTimeToday = () => {
  startTimeDate.value = new Date(defaultStartTimeDate.value.format(timestampFormat));
  endTimeDate.value = new Date(defaultEndTimeDate.value.format(timestampFormat));
};
const startDateISOString = computed(() => {
  return dayjs(startTimeDate.value).utc(uiConfig.useUTC).toISOString();
});
const endDateISOString = computed(() => {
  return dayjs(endTimeDate.value).utc(uiConfig.useUTC).toISOString();
});

const domainOptions = ref([]);
const domainOptionSelected = ref(null);
const metadataKeysOptions = ref([]);
const newMetadataKey = ref(null);
const newMetadataValue = ref(null);
const fileName = ref(null);
const filteredCause = ref(null);
const requeueMin = ref(null);
const flowOptionSelected = ref([]);
const egressFlowOptionSelected = ref([]);
const stageOptions = ref([]);
const stageOptionSelected = ref(null);
const egressedOptions = ref([
  { name: "True", value: true },
  { name: "False", value: false },
]);
const isReplayableOptions = ref([
  { name: "True", value: true },
  { name: "False", value: false },
]);
const egressedOptionSelected = ref(null);
const isReplayableSelected = ref(null);
const testModeOptions = ref([
  { name: "True", value: true },
  { name: "False", value: false },
]);

const filteredOptions = ref([
  { name: "True", value: true },
  { name: "False", value: false },
]);
const filteredOptionSelected = ref(null);
const testModeOptionSelected = ref(null);
const loading = ref(true);
const totalRecords = ref(0);
const collapsedSearchOption = ref(true);
const tableData = ref([]);
const offset = ref(0);
const perPage = ref();
const sortField = ref("modified");
const sortDirection = ref("DESC");
const timestampFormat = "YYYY-MM-DD HH:mm:ss";
const metadataArray = ref([]);
const sizeMin = ref();
const sizeMax = ref();
const sizeUnits = [
  { name: "B", multiplier: 1 },
  { name: "kB", multiplier: 1000 },
  { name: "MB", multiplier: 1000000 },
  { name: "GB", multiplier: 1000000000 },
];
const sizeTypes = [
  { name: "Ingress", ingress: true },
  { name: "Total", total: true },
];
const sizeTypeSelected = ref(sizeTypes[0]);
const sizeUnitSelected = ref(sizeUnits[0]);

const watchEnabled = ref(false);

const ingressBytesMin = computed(() => (sizeMin.value && sizeTypeSelected.value.ingress ? sizeMin.value * sizeUnitSelected.value.multiplier : null));
const ingressBytesMax = computed(() => (sizeMax.value && sizeTypeSelected.value.ingress ? sizeMax.value * sizeUnitSelected.value.multiplier : null));
const totalBytesMin = computed(() => (sizeMin.value && sizeTypeSelected.value.total ? sizeMin.value * sizeUnitSelected.value.multiplier : null));
const totalBytesMax = computed(() => (sizeMax.value && sizeTypeSelected.value.total ? sizeMax.value * sizeUnitSelected.value.multiplier : null));
const egressed = computed(() => (egressedOptionSelected.value ? egressedOptionSelected.value.value : null));
const filtered = computed(() => (filteredOptionSelected.value ? filteredOptionSelected.value.value : null));
const testMode = computed(() => (testModeOptionSelected.value ? testModeOptionSelected.value.value : null));
const stageName = computed(() => (stageOptionSelected.value ? stageOptionSelected.value.name : null));
const flowName = computed(() => (flowOptionSelected.value ? flowOptionSelected.value : null));
const egressFlowName = computed(() => (egressFlowOptionSelected.value ? egressFlowOptionSelected.value : null));
const replayable = computed(() => (isReplayableSelected.value ? isReplayableSelected.value.value : null));

const metadata = computed(() => {
  return metadataArray.value.map((i) => {
    return {
      key: i.key,
      value: i.value,
    };
  });
});

const selectedDomain = computed(() => {
  return domainOptionSelected.value ? domainOptionSelected.value.name : null;
});

const indexedMetadataOverlay = ref(null);
const showIndexedMetadataOverlay = (event) => {
  indexedMetadataOverlay.value.toggle(event);
};

const removeMetadataItem = (item) => {
  let index = metadataArray.value.indexOf(item);
  metadataArray.value.splice(index, 1);
};

const addMetadataItem = (key, value) => {
  metadataArray.value.push({ key: key, value: value, valid: true });
};

const addMetadataItemEvent = () => {
  if (newMetadataKey.value && newMetadataValue.value) {
    addMetadataItem(newMetadataKey.value.key, newMetadataValue.value);
    newMetadataKey.value = null;
    newMetadataValue.value = null;
    indexedMetadataOverlay.value.toggle();
  }
};

const refreshButtonIcon = computed(() => {
  let classes = ["fa", "fa-sync-alt"];
  if (loading.value) classes.push("fa-spin");
  return classes.join(" ");
});

const items = ref([
  {
    label: "Options",
    items: [
      {
        label: "Clear Options",
        icon: "fas fa-times",
        command: () => {
          fileName.value = null;
          filteredCause.value = null;
          requeueMin.value = null;
          flowOptionSelected.value = [];
          egressFlowOptionSelected.value = [];
          stageOptionSelected.value = null;
          egressedOptionSelected.value = null;
          filteredOptionSelected.value = null;
          testModeOptionSelected.value = null;
          isReplayableSelected.value = null;
          sizeMax.value = null;
          sizeMin.value = null;
          domainOptionSelected.value = null;
          metadataArray.value = [];
          fetchDeltaFilesData();
        },
      },
    ],
  },
]);

watch(startTimeDate, () => {
  if (watchEnabled.value) fetchDeltaFilesData();
});

watch(endTimeDate, () => {
  if (watchEnabled.value) fetchDeltaFilesData();
});

watch(selectedDomain, async (value) => {
  fetchDeltaFilesData();
  await fetchIndexedMetadataKeys(value);
  validateMetadataArray();
});

watch(
  metadataArray,
  () => {
    if (watchEnabled.value) fetchDeltaFilesData();
  },
  { deep: true }
);

watch([sizeMin, sizeMax, flowOptionSelected, egressFlowOptionSelected, stageOptionSelected, egressedOptionSelected, filteredOptionSelected, testModeOptionSelected, requeueMin, isReplayableSelected], () => {
  if (watchEnabled.value) fetchDeltaFilesData();
});

watch(
  fileName,
  _.debounce(
    () => {
      if (watchEnabled.value) fetchDeltaFilesData();
    },
    500,
    { leading: false, trailing: true }
  )
);

watch(
  filteredCause,
  _.debounce(
    () => {
      if (watchEnabled.value) {
        if (filteredCause.value == "") {
          filteredCause.value = null;
        } else {
          fetchDeltaFilesData();
        }
      }
    },
    500,
    { leading: false, trailing: true }
  )
);

watch([sizeTypeSelected, sizeUnitSelected], () => {
  if (sizeMin.value || sizeMax.value) {
    fetchDeltaFilesData();
  }
  setPersistedParams();
});

const validateMetadataArray = () => {
  const validKeys = metadataKeysOptions.value.map((i) => i.key);
  for (const index of metadataArray.value.keys()) {
    const key = metadataArray.value[index].key;
    metadataArray.value[index].valid = validKeys.includes(key);
  }
};

const invalidMetadataTooltip = (key) => {
  if (domainOptionSelected.value) {
    return `${key} is not a valid indexed metadata key for the ${domainOptionSelected.value.name} domain.`;
  }
};

const updateInputStartTime = async (e) => {
  await nextTick();
  if (dayjs(e.target.value.trim()).isValid()) {
    startTimeDate.value = new Date(formatTimestamp(e.target.value.trim(), timestampFormat));
  } else {
    startTimeDate.value = new Date(defaultStartTimeDate.value.format(timestampFormat));
  }
};
const updateInputEndTime = async (e) => {
  await nextTick();
  if (dayjs(e.target.value.trim()).isValid()) {
    endTimeDate.value = new Date(formatTimestamp(e.target.value.trim(), timestampFormat));
  } else {
    endTimeDate.value = new Date(defaultEndTimeDate.value.format(timestampFormat));
  }
};
onBeforeMount(() => {
  useURLSearch.value = route.fullPath.includes("search?");
});

onMounted(async () => {
  setDateTimeToday();
  await fetchIngressFlowNames();
  await fetchEgressFlowNames();
  fetchStages();
  fetchDomains();
  await getPersistedParams();
  await nextTick();
  if (domainOptionSelected.value == null) fetchIndexedMetadataKeys();
  watchEnabled.value = true;
});

const optionMenuToggle = (event) => {
  optionMenu.value.toggle(event);
};

const fetchDomains = async () => {
  const domains = await getDomains();
  domainOptions.value = domains.map((domain) => {
    return { name: domain };
  });
};

const fetchIndexedMetadataKeys = async (domain) => {
  const keys = await getIndexedMetadataKeys(domain);
  metadataKeysOptions.value = keys.map((key) => {
    return { key: key };
  });
};

const fetchStages = async () => {
  let enumsStageTypes = await getEnumValuesByEnumType("DeltaFileStage");
  stageOptions.value = enumsStageTypes.data.__type.enumValues;
};

const fetchDeltaFilesDataNoDebounce = async () => {
  setPersistedParams();

  loading.value = true;
  let data = await getDeltaFileSearchData(startDateISOString.value, endDateISOString.value, offset.value, perPage.value, sortField.value, sortDirection.value, fileName.value, stageName.value, null, flowName.value, egressFlowName.value, egressed.value, filtered.value, selectedDomain.value, metadata.value, ingressBytesMin.value, ingressBytesMax.value, totalBytesMin.value, totalBytesMax.value, testMode.value, requeueMin.value, filteredCause.value, replayable.value);
  tableData.value = data.data.deltaFiles.deltaFiles;
  loading.value = false;
  totalRecords.value = data.data.deltaFiles.totalCount;
};

const fetchDeltaFilesData = _.debounce(
  async () => {
    fetchDeltaFilesDataNoDebounce();
  },
  500,
  { leading: true, trailing: false }
);

const results = computed(() => {
  return tableData.value.map((row) => {
    const timeElapsed = new Date(row.modified) - new Date(row.created);
    return {
      ...row,
      elapsed: duration(timeElapsed),
    };
  });
});

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
  fetchDeltaFilesDataNoDebounce();
};

const ISOStringToDate = (dateISOString) => {
  return uiConfig.useUTC ? dayjs(dateISOString).add(new Date().getTimezoneOffset(), "minute").toDate() : dayjs(dateISOString).toDate();
};

const getPersistedParams = async () => {
  perPage.value = nonPanelState.value.perPage || 20;
  if (useURLSearch.value) {
    if (params.start) startTimeDate.value = ISOStringToDate(params.start);
    if (params.end) endTimeDate.value = ISOStringToDate(params.end);
    sizeUnitSelected.value = params.sizeUnit ? sizeUnits.find((i) => i.name == params.sizeUnit) : sizeUnits[0];
    sizeTypeSelected.value = params.sizeType ? sizeTypes.find((i) => i.name == params.sizeType) : sizeTypes[0];
    fileName.value = params.fileName != "" ? params.fileName : null;
    filteredCause.value = params.filteredCause != "" ? params.filteredCause : null;
    requeueMin.value = params.requeueMin != null ? Number(params.requeueMin) : null;
    stageOptionSelected.value = params.stage != null ? { name: params.stage } : null;
    flowOptionSelected.value = params.ingressFlow ? params.ingressFlow.split(",") : [];
    egressFlowOptionSelected.value = params.egressFlow ? params.egressFlow.split(",") : [];
    egressedOptionSelected.value = params.egressed ? egressedOptions.value.find((i) => i.name == params.egressed) : null;
    filteredOptionSelected.value = params.filtered ? filteredOptions.value.find((i) => i.name == params.filtered) : null;
    testModeOptionSelected.value = params.testMode ? testModeOptions.value.find((i) => i.name == params.testMode) : null;
    isReplayableSelected.value = params.replayable ? isReplayableOptions.value.find((i) => i.name == params.replayable) : null;
    domainOptionSelected.value = params.domain ? { name: params.domain } : null;
    sizeMin.value = params.sizeMin != null ? Number(params.sizeMin) : null;
    sizeMax.value = params.sizeMax != null ? Number(params.sizeMax) : null;
    if (params.metadata != null) {
      const metadataArrayVal = ref(getMetadataArray(params.metadata));
      metadataArray.value = metadataArrayVal.value || [];
    } else {
      metadataArray.value = [];
    }

    const panelSearchKeys = Object.keys(params).filter((key) => !["start", "end"].includes(key));
    collapsedSearchOption.value = panelSearchKeys.length == 0;
  } else {
    // Values that, if set, should not expand Advanced Search Options.
    if (nonPanelState.value.startTimeDateState) startTimeDate.value = ISOStringToDate(nonPanelState.value.startTimeDateState);
    if (nonPanelState.value.endTimeDateState) endTimeDate.value = ISOStringToDate(nonPanelState.value.endTimeDateState);
    sizeUnitSelected.value = nonPanelState.value.sizeUnitState ? sizeUnits.find((i) => i.name == nonPanelState.value.sizeUnitState) : sizeUnits[0];
    sizeTypeSelected.value = nonPanelState.value.sizeTypeState ? sizeTypes.find((i) => i.name == nonPanelState.value.sizeTypeState) : sizeTypes[0];

    // Values that, if set, should expand Advanced Search Options.
    fileName.value = panelState.value.fileName;
    filteredCause.value = panelState.value.filteredCause;
    requeueMin.value = panelState.value.requeueMin;
    stageOptionSelected.value = panelState.value.stageOptionState ? { name: panelState.value.stageOptionState } : null;
    flowOptionSelected.value = panelState.value.flowOptionState ? panelState.value.flowOptionState : [];
    egressFlowOptionSelected.value = panelState.value.egressFlowOptionState ? panelState.value.egressFlowOptionState : [];
    egressedOptionSelected.value = panelState.value.egressedOptionState ? egressedOptions.value.find((i) => i.name == panelState.value.egressedOptionState) : null;
    filteredOptionSelected.value = panelState.value.filteredOptionState ? filteredOptions.value.find((i) => i.name == panelState.value.filteredOptionState) : null;
    testModeOptionSelected.value = panelState.value.testModeOptionState ? testModeOptions.value.find((i) => i.name == panelState.value.testModeOptionState) : null;
    isReplayableSelected.value = panelState.value.replayableOptionState ? isReplayableOptions.value.find((i) => i.name == panelState.value.replayableOptionState) : null;
    domainOptionSelected.value = panelState.value.domainOptionState ? { name: panelState.value.domainOptionState } : null;
    sizeMin.value = panelState.value.sizeMinState;
    sizeMax.value = panelState.value.sizeMaxState;
    metadataArray.value = panelState.value.metadataArrayState || [];

    // If any of the fields are true it means we have persisted values. Don't collapse the search options panel so the user can see
    // what search options are being used.
    collapsedSearchOption.value = !_.some(Object.values(panelState.value), (i) => !(i == null || i.length == 0));
  }
};

const panelState = useStorage("panel-search-options", {}, sessionStorage, { serializer: StorageSerializers.object });
const nonPanelState = useStorage("non-panel-search-options", {}, sessionStorage, { serializer: StorageSerializers.object });

const setPersistedParams = () => {
  panelState.value = {
    // Values that, if set, should expand Advanced Search Options.
    fileName: fileName.value,
    filteredCause: filteredCause.value,
    requeueMin: requeueMin.value,
    stageOptionState: stageOptionSelected.value ? stageOptionSelected.value.name : null,
    flowOptionState: flowOptionSelected.value ? flowOptionSelected.value : null,
    egressFlowOptionState: egressFlowOptionSelected.value ? egressFlowOptionSelected.value : null,
    egressedOptionState: egressedOptionSelected.value ? egressedOptionSelected.value.name : null,
    filteredOptionState: filteredOptionSelected.value ? filteredOptionSelected.value.name : null,
    testModeOptionState: testModeOptionSelected.value ? testModeOptionSelected.value.name : null,
    replayableOptionState: isReplayableSelected.value ? isReplayableSelected.value.name : null,
    domainOptionState: domainOptionSelected.value ? domainOptionSelected.value.name : null,
    sizeMinState: sizeMin.value,
    sizeMaxState: sizeMax.value,
    metadataArrayState: metadataArray.value,
  };

  nonPanelState.value = {
    // Values that, if set, should not expand Advanced Search Options.
    startTimeDateState: startTimeDateIsDefault.value ? null : startDateISOString.value,
    endTimeDateState: startTimeDateIsDefault.value ? null : endDateISOString.value,
    sizeUnitState: sizeUnitSelected.value ? sizeUnitSelected.value.name : null,
    sizeTypeState: sizeTypeSelected.value ? sizeTypeSelected.value.name : null,
    perPage: perPage.value,
  };

  params.start = startTimeDateIsDefault.value ? null : startDateISOString.value;
  params.end = endTimeDateIsDefault.value ? null : endDateISOString.value;
  params.sizeUnit = sizeMin.value != null || sizeMax.value != null ? sizeUnitSelected.value.name : null;
  params.sizeType = sizeMin.value != null || sizeMax.value != null ? sizeTypeSelected.value.name : null;
  params.fileName = fileName.value != "" ? fileName.value : null;
  params.filteredCause = filteredCause.value != "" ? filteredCause.value : null;
  params.requeueMin = requeueMin.value != null ? requeueMin.value : null;
  params.stage = stageOptionSelected.value ? stageOptionSelected.value.name : null;
  params.ingressFlow = flowOptionSelected.value.length > 0 ? String(flowOptionSelected.value) : null;
  params.egressFlow = egressFlowOptionSelected.value.length > 0 ? String(egressFlowOptionSelected.value) : null;
  params.egressed = egressedOptionSelected.value ? egressedOptionSelected.value.name : null;
  params.filtered = filteredOptionSelected.value ? filteredOptionSelected.value.name : null;
  params.testMode = testModeOptionSelected.value ? testModeOptionSelected.value.name : null;
  params.replayable = isReplayableSelected.value ? isReplayableSelected.value.name : null;
  params.domain = domainOptionSelected.value ? domainOptionSelected.value.name : null;
  params.sizeMin = sizeMin.value != null ? sizeMin.value : null;
  params.sizeMax = sizeMax.value != null ? sizeMax.value : null;
  params.metadata = metadataArray.value.length > 0 ? getMetadataString(metadataArray.value) : null;
};

const getMetadataString = (arrayData) => {
  const metadataString = arrayData
    .map((pair) => {
      return pair.key.concat(":", pair.value);
    })
    .join(",");
  return metadataString;
};

const getMetadataArray = (stringData) => {
  const metadataArray = stringData.split(",");
  return metadataArray.map((pair) => {
    const keyValuePair = pair.split(":");
    return {
      key: keyValuePair[0],
      value: keyValuePair[1],
      valid: true,
    };
  });
};

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

const onPanelRightClick = (event) => {
  menu.value.show(event);
};

const onRowContextMenu = (event) => {
  if (selectedDids.value.length <= 0) {
    selectedDids.value = [event.data];
  }
};

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
      selectedDids.value = results.value;
    },
  },
  {
    separator: true,
    visible: computed(() => hasPermission("DeltaFileReplay")),
  },
  {
    label: "Replay Selected",
    icon: "fas fa-sync fa-fw",
    command: () => {
      metadataDialog.value.showConfirmDialog("Replay");
    },
    visible: computed(() => hasPermission("DeltaFileReplay")),
    disabled: computed(() => selectedDids.value.length == 0),
  },
  {
    label: "Annotate",
    icon: "fa-solid fa-asterisk fa-fw",
    visible: computed(() =>  hasPermission("DeltaFileAnnotate")),
    command: () => {
      annotateDialog.value.showDialog();
    },
  },
]);

const filterSelectedDids = computed(() => {
  let dids = selectedDids.value.map((selectedDID) => {
    return selectedDID.did;
  });
  return dids;
});
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-search-page.scss";
</style>
