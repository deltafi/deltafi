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
                <InputText v-model.trim="fileName" class="p-inputtext input-area-height" placeholder="Filename" />
                <label for="flowId" class="mt-2">Ingress Flow:</label>
                <Dropdown id="flowId" v-model="flowOptionSelected" :placeholder="flowOptionSelected ? flowOptionSelected.name + ' ' : 'Select an Ingress Flow'" :options="flowOptions" option-label="name" show-clear :editable="false" class="deltafi-input-field min-width" />
                <label for="stageId" class="mt-2">Size:</label>
                <div class="size-container">
                  <Dropdown v-model="sizeTypeSelected" :options="sizeTypes" option-label="name" style="width: 8rem" class="deltafi-input-field mr-2" />
                  <InputNumber v-model="sizeMin" class="p-inputnumber input-area-height" input-style="width: 6rem" placeholder="Min" /> -
                  <InputNumber v-model="sizeMax" class="p-inputnumber input-area-height" input-style="width: 6rem" placeholder="Max" />
                  <Dropdown v-model="sizeUnitSelected" :options="sizeUnits" option-label="name" class="deltafi-input-field ml-2" />
                </div>
              </div>
              <div class="flex-column flex-column-small">
                <label for="stageId">Stage:</label>
                <!-- TODO: GitLab issue "Fix multi-select dropdown data bouncing" (https://gitlab.com/systolic/deltafi/deltafi-ui/-/issues/96). Placeholder hacky fix to stop the bouncing of data within the field. -->
                <Dropdown id="stageId" v-model="stageOptionSelected" :placeholder="stageOptionSelected ? stageOptionSelected.name + ' ' : 'Select a Stage'" :options="stageOptions" option-label="name" show-clear :editable="false" class="deltafi-input-field min-width" />
                <label for="egressedState" class="mt-2">Egressed:</label>
                <!-- TODO: GitLab issue "Fix multi-select dropdown data bouncing" (https://gitlab.com/systolic/deltafi/deltafi-ui/-/issues/96). Placeholder hacky fix to stop the bouncing of data within the field. -->
                <Dropdown id="egressState" v-model="egressedOptionSelected" :placeholder="egressedOptionSelected ? egressedOptionSelected.name + ' ' : 'Select if Egressed'" :options="egressedOptions" option-label="name" :show-clear="true" class="deltafi-input-field min-width" />
                <label for="filteredState" class="mt-2">Filtered:</label>
                <!-- TODO: GitLab issue "Fix multi-select dropdown data bouncing" (https://gitlab.com/systolic/deltafi/deltafi-ui/-/issues/96). Placeholder hacky fix to stop the bouncing of data within the field. -->
                <Dropdown id="filteredState" v-model="filteredOptionSelected" :placeholder="filteredOptionSelected ? filteredOptionSelected.name + ' ' : 'Select if Filtered'" :options="filteredOptions" option-label="name" :show-clear="true" class="deltafi-input-field min-width" />
              </div>
              <div class="flex-column">
                <label for="filteredState">Domain:</label>
                <!-- TODO: GitLab issue "Fix multi-select dropdown data bouncing" (https://gitlab.com/systolic/deltafi/deltafi-ui/-/issues/96). Placeholder hacky fix to stop the bouncing of data within the field. -->
                <Dropdown id="domain" v-model="domainOptionSelected" :placeholder="domainOptionSelected ? domainOptionSelected.name + ' ' : 'Select a Domain'" :options="domainOptions" option-label="name" show-clear :editable="false" class="deltafi-input-field min-width" />
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
    <Panel header="Results" class="table-panel results">
      <template #icons>
        <Paginator v-if="results.length > 0" :rows="10" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalRecords" :rows-per-page-options="[10, 20, 50, 100, 1000]" class="p-panel-header" style="float: left" @page="onPage($event)"></Paginator>
      </template>
      <DataTable responsive-layout="scroll" class="p-datatable p-datatable-sm p-datatable-gridlines" striped-rows :value="results" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalRecords" :row-class="actionRowClass" @sort="onSort($event)">
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
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import Chip from "primevue/chip";
import OverlayPanel from "primevue/overlaypanel";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import FormattedBytes from "@/components/FormattedBytes.vue";
import Paginator from "primevue/paginator";
import PageHeader from "@/components/PageHeader.vue";
import Timestamp from "@/components/Timestamp.vue";
import DidLink from "@/components/DidLink.vue";
import useDeltaFilesQueryBuilder from "@/composables/useDeltaFilesQueryBuilder";
import useUtilFunctions from "@/composables/useUtilFunctions";
import useDomains from "@/composables/useDomains";
import { ref, computed, watch, onMounted, nextTick, inject } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";
import _ from "lodash";

dayjs.extend(utc);

const { getDeltaFileSearchData, getEnumValuesByEnumType, getConfigByType } = useDeltaFilesQueryBuilder();
const { duration, formatTimestamp, shortTimezone, convertLocalDateToUTC } = useUtilFunctions();
const { getDomains, getIndexedMetadataKeys } = useDomains();

const uiConfig = inject("uiConfig");

const optionMenu = ref();
const startTimeDate = ref(new Date());
const endTimeDate = ref(new Date());
startTimeDate.value.setHours(0, 0, 0, 0);
endTimeDate.value.setHours(23, 59, 59, 999);
const defaultStartTimeDate = startTimeDate.value;
const defaultEndTimeDate = endTimeDate.value;
const domainOptions = ref([]);
const domainOptionSelected = ref(null);
const metadataKeysOptions = ref([]);
const newMetadataKey = ref(null);
const newMetadataValue = ref(null);
const fileName = ref(null);
const flowOptions = ref([]);
const flowOptionSelected = ref(null);
const stageOptions = ref([]);
const stageOptionSelected = ref(null);
const egressedOptions = ref([
  { name: "True", value: true },
  { name: "False", value: false },
]);
const egressedOptionSelected = ref(null);
const filteredOptions = ref([
  { name: "True", value: true },
  { name: "False", value: false },
]);
const filteredOptionSelected = ref(null);
const loading = ref(true);
const totalRecords = ref(0);
const collapsedSearchOption = ref(true);
const tableData = ref([]);
const offset = ref(0);
const perPage = ref(10);
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
const stageName = computed(() => (stageOptionSelected.value ? stageOptionSelected.value.name : null));
const flowName = computed(() => (flowOptionSelected.value ? flowOptionSelected.value.name : null));

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
          fileName.value = null;
          flowOptionSelected.value = null;
          stageOptionSelected.value = null;
          egressedOptionSelected.value = null;
          filteredOptionSelected.value = null;
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

watch([sizeMin, sizeMax, flowOptionSelected, stageOptionSelected, egressedOptionSelected, filteredOptionSelected], () => {
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

onMounted(async () => {
  fetchConfigTypes();
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

const fetchConfigTypes = async () => {
  const flowTypes = ["INGRESS_FLOW"];
  fetchFlows(flowTypes);
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

const fetchDeltaFilesData = _.debounce(
  async () => {
    setPersistedParams();

    loading.value = true;
    let data = await getDeltaFileSearchData(startDateISOString.value, endDateISOString.value, offset.value, perPage.value, sortField.value, sortDirection.value, fileName.value, stageName.value, null, flowName.value, egressed.value, filtered.value, selectedDomain.value, metadata.value, ingressBytesMin.value, ingressBytesMax.value, totalBytesMin.value, totalBytesMax.value);
    tableData.value = data.data.deltaFiles.deltaFiles;
    loading.value = false;
    totalRecords.value = data.data.deltaFiles.totalCount;
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
  fetchDeltaFilesData();
};

const getPersistedParams = async () => {
  startTimeDate.value = new Date(state.value.startTimeDateState ? state.value.startTimeDateState : startTimeDate.value);
  endTimeDate.value = new Date(state.value.endTimeDateState ? state.value.endTimeDateState : endTimeDate.value);
  sizeUnitSelected.value = state.value.sizeUnitState ? sizeUnits.find((i) => i.name == state.value.sizeUnitState) : sizeUnits[0];
  sizeTypeSelected.value = state.value.sizeTypeState ? sizeTypes.find((i) => i.name == state.value.sizeTypeState) : sizeTypes[0];

  // Values that, if set, should expand Advanced Search Options.
  fileName.value = state.value.fileName;
  stageOptionSelected.value = state.value.stageOptionState ? { name: state.value.stageOptionState } : null;
  flowOptionSelected.value = state.value.flowOptionState ? { name: state.value.flowOptionState } : null;
  egressedOptionSelected.value = state.value.egressedOptionState ? egressedOptions.value.find((i) => i.name == state.value.egressedOptionState) : null;
  filteredOptionSelected.value = state.value.filteredOptionState ? filteredOptions.value.find((i) => i.name == state.value.filteredOptionState) : null;
  domainOptionSelected.value = state.value.domainOptionState ? { name: state.value.domainOptionState } : null;
  sizeMin.value = state.value.sizeMinState;
  sizeMax.value = state.value.sizeMaxState;
  metadataArray.value = state.value.metadataArrayState || [];

  // If any of the fields are true it means we have persisted values. Don't collapse the search options panel so the user can see
  // what search options are being used.
  collapsedSearchOption.value = !_.some(Object.values(state.value).slice(4), (i) => !(i == null || i.length == 0));
};
const state = useStorage("advanced-search-options-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });

const setPersistedParams = () => {
  state.value = {
    startTimeDateState: startTimeDate.value ? startTimeDate.value : null,
    endTimeDateState: endTimeDate.value ? endTimeDate.value : null,
    sizeUnitState: sizeUnitSelected.value ? sizeUnitSelected.value.name : null,
    sizeTypeState: sizeTypeSelected.value ? sizeTypeSelected.value.name : null,

    // Values that, if set, should expand Advanced Search Options.
    fileName: fileName.value,
    stageOptionState: stageOptionSelected.value ? stageOptionSelected.value.name : null,
    flowOptionState: flowOptionSelected.value ? flowOptionSelected.value.name : null,
    egressedOptionState: egressedOptionSelected.value ? egressedOptionSelected.value.name : null,
    filteredOptionState: filteredOptionSelected.value ? filteredOptionSelected.value.name : null,
    domainOptionState: domainOptionSelected.value ? domainOptionSelected.value.name : null,
    sizeMinState: sizeMin.value,
    sizeMaxState: sizeMax.value,
    metadataArrayState: metadataArray.value,
  };
};
</script>

<style lang="scss">
@import "@/styles/pages/deltafile-search-page.scss";
</style>
