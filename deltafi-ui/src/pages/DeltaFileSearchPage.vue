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
  <div class="deltafile-search-page">
    <div>
      <PageHeader heading="DeltaFile Search">
        <div class="time-range btn-toolbar mb-2 mb-md-0">
          <Button class="p-button-text p-button-sm p-button-secondary mr-0" disabled>
            {{ shortTimezone() }}
          </Button>
          <CustomCalendar ref="customCalendarRef" :start-time-date="model.startTimeDate" :end-time-date="model.endTimeDate" :reset-default="resetDefaultTimeDate" class="ml-0 mr-1" @update:start-time-date:end-time-date="updateInputDateTime" />
          <Button class="p-button p-button-outlined deltafi-input-field ml-1" icon="fa fa-sync-alt" :loading="loading" label="Refresh" @click="refreshDeltaFilesData()" />
        </div>
      </PageHeader>
    </div>
    <div class="row mb-3">
      <div class="col-12">
        <CollapsiblePanel :collapsed="collapsedSearchOption">
          <template #header>
            <span class="align-advanced-options-header-title">
              <span class="d-flex">
                <span class="p-panel-title align-advanced-options-header">Advanced Search Options</span>
                <span>
                  <Button v-tooltip.right="{ value: `Clear Options`, disabled: !activeAdvancedOptions }" rounded :class="`ml-2 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${activeAdvancedOptions ? 'p-column-filter-menu-button-active' : null}`" :disabled="!activeAdvancedOptions" @click="clearOptions()">
                    <i class="pi pi-filter" style="font-size: 1rem" />
                  </Button>
                </span>
              </span>
            </span>
          </template>
          <div class="search-options-wrapper">
            <div class="flex-row">
              <div v-for="columnNumber in 3" :key="columnNumber" :class="`flex-column ${_.isEqual(columnNumber, 2) ? 'flex-column-small' : ''}`">
                <template v-for="[i, formInfo] of _.orderBy(_.filter(advanceOptionsPanelInfo, ['column', columnNumber]), ['order'], ['asc']).entries()" :key="formInfo">
                  <label :for="`${formInfo.field}` + 'Id'" :class="!_.isEqual(i, 0) ? 'mt-2' : ''">{{ formInfo.label }}</label>
                  <InputText v-if="_.isEqual(formInfo.componentType, 'InputText')" :id="`${formInfo.field}` + 'Id'" v-model.trim="model[formInfo.field]" :placeholder="formInfo.placeholder" :class="formInfo.class" />
                  <MultiSelect v-if="_.isEqual(formInfo.componentType, 'MultiSelect')" :id="`${formInfo.field}` + 'Id'" v-model="model[formInfo.field]" :options="formInfo.options" :placeholder="formInfo.placeholder" :option-group-label="formInfo.optionGroupLabel" :option-group-children="formInfo.optionGroupChildren" :option-label="formInfo.optionLabel" :option-value="formInfo.optionValue" :filter="formInfo.filter" :class="formInfo.class" display="chip" />
                  <div v-if="_.isEqual(formInfo.componentType, 'SizeUnit')" class="size-container">
                    <Dropdown v-model="model.sizeType" :options="sizeTypesOptions" style="width: 8rem" class="deltafi-input-field mr-2" />
                    <InputNumber v-model="model.sizeMin" class="p-inputnumber input-area-height" :input-style="{ width: '6rem' }" placeholder="Min" /> -
                    <InputNumber v-model="model.sizeMax" class="p-inputnumber input-area-height" :input-style="{ width: '6rem' }" placeholder="Max" />
                    <Dropdown v-model="model.sizeUnit" :options="[...sizeUnitsOptionsMap.keys()]" class="deltafi-input-field ml-2" />
                  </div>
                  <Dropdown v-if="_.isEqual(formInfo.componentType, 'Dropdown')" :id="`${formInfo.field}` + 'Id'" v-model="model[formInfo.field]" :placeholder="formInfo.placeholder" :options="formInfo.options" :show-clear="true" :class="formInfo.class">
                    <template #value="slotProps">
                      <div v-if="slotProps.value != null" class="flex align-items-center">
                        <div>{{ formatOption(formInfo.formatOptions, slotProps.value) }}</div>
                      </div>
                      <span v-else>
                        {{ slotProps.placeholder }}
                      </span>
                    </template>
                    <template #option="slotProps">
                      <div class="flex align-items-center">
                        <div>{{ formatOption(formInfo.formatOptions, slotProps.option) }}</div>
                      </div>
                    </template>
                  </Dropdown>
                  <InputNumber v-if="_.isEqual(formInfo.componentType, 'InputNumber')" :id="`${formInfo.field}` + 'Id'" v-model="model[formInfo.field]" :input-style="{ width: '6rem' }" :placeholder="formInfo.placeholder" :class="formInfo.class" />
                  <div v-if="_.isEqual(formInfo.componentType, 'Annotations')" :id="`${formInfo.field}` + 'Id'" class="annotations-chips">
                    <Chip v-for="item in model.validatedAnnotations" :key="item" v-tooltip.top="{ value: invalidAnnotationTooltip(item.key), disabled: item.valid }" removable class="mr-2 mb-1" :class="{ 'invalid-chip': !item.valid, 'valid-chip': item.valid }" @remove="removeAnnotationItem(item)"> {{ item.key }}: {{ item.value }} </Chip>
                    <Chip class="add-annotations-btn" @click="showAnnotationsOverlay">
                      &nbsp;
                      <i class="pi pi-plus" />
                      &nbsp;
                    </Chip>
                  </div>
                </template>
              </div>
              <OverlayPanel ref="annotationsOverlay">
                <Dropdown v-model="newAnnotationKey" placeholder="Key" :options="annotationsKeysOptions" option-label="key" style="width: 15rem" @keyup.enter="addAnnotationItemEvent" /> :
                <InputText v-model="newAnnotationValue" placeholder="Value" style="width: 15rem" @keyup.enter="addAnnotationItemEvent" />
              </OverlayPanel>
            </div>
          </div>
        </CollapsiblePanel>
      </div>
    </div>
    <Panel header="Results" class="table-panel results" @contextmenu="onPanelRightClick">
      <ContextMenu ref="menu" :model="menuItems" />
      <template #icons>
        <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
          <span class="fas fa-bars" />
        </Button>
        <Menu ref="menu" :model="menuItems" :popup="true" />
        <Paginator v-if="results.length > 0" :rows="model.perPage" :first="getPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" :current-page-report-template="pageReportTemplate" :total-records="totalRecords" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage($event)" />
      </template>
      <DataTable v-model:selection="selectedDids" :value="results" data-key="did" selection-mode="multiple" responsive-layout="scroll" class="p-datatable p-datatable-sm p-datatable-gridlines" striped-rows :loading="loading" loading-icon="pi pi-spinner" :rows="model.perPage" :lazy="true" :total-records="totalRecords" :row-class="actionRowClass" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
        <template #empty> No DeltaFiles match the provided search criteria. </template>
        <template #loading> Loading results. Please wait. </template>
        <Column field="did" header="DID" class="did-column">
          <template #body="{ data }">
            <DidLink :did="data.did" />
          </template>
        </Column>
        <Column field="name" header="Filename" :sortable="true" class="filename-column" />
        <Column field="dataSource" header="Data Source" :sortable="true" />
        <Column field="stage" header="Stage" :sortable="true">
          <template #body="{ data }">
            {{ data.stage }}
            <i v-if="data.paused" v-tooltip="'Paused'" style="color: #888888" class="fa-solid fa-circle-pause" />
          </template>
        </Column>
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
          <template #body="row">
            {{ row.data.elapsed }}
          </template>
        </Column>
      </DataTable>
      <ScrollTop target="window" :threshold="150" icon="pi pi-arrow-up" />
    </Panel>
  </div>
  <ConfirmDialog />
  <RetryResumeDialog ref="retryResumeDialog" :did="filterSelectedDids" @update="fetchDeltaFilesData()" />
  <Dialog v-model:visible="displayCancelBatchingDialog" :breakpoints="{ '960px': '75vw', '940px': '90vw' }" :style="{ width: '30vw' }" :modal="true" :closable="false" :close-on-escape="false" :draggable="false" header="Canceling">
    <div>
      <p>Cancel in progress. Please do not refresh the page!</p>
      <ProgressBar :value="batchCompleteValue" />
    </div>
  </Dialog>
  <AnnotateDialog ref="annotateDialog" :dids="filterSelectedDids" @refresh-page="fetchDeltaFilesData()" />
  <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="ackErrorsDialog.dids" @acknowledged="onAcknowledged" />
</template>

<script setup>
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import AnnotateDialog from "@/components/AnnotateDialog.vue";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import CustomCalendar from "@/components/CustomCalendar.vue";
import DidLink from "@/components/DidLink.vue";
import FormattedBytes from "@/components/FormattedBytes.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import RetryResumeDialog from "@/components/MetadataDialogReplay.vue";
import Timestamp from "@/components/Timestamp.vue";
import useAnnotate from "@/composables/useAnnotate";
import useDeltaFilesQueryBuilder from "@/composables/useDeltaFilesQueryBuilder";
import useDeltaFiles from "@/composables/useDeltaFiles";
import useFlows from "@/composables/useFlows";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import useTopics from "@/composables/useTopics";
import { computed, inject, nextTick, onBeforeMount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { useStorage, StorageSerializers, useUrlSearchParams } from "@vueuse/core";

import _ from "lodash";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

import Button from "primevue/button";
import Chip from "primevue/chip";
import Column from "primevue/column";
import ConfirmDialog from "primevue/confirmdialog";
import ContextMenu from "primevue/contextmenu";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import Dropdown from "primevue/dropdown";
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import Menu from "primevue/menu";
import MultiSelect from "primevue/multiselect";
import OverlayPanel from "primevue/overlaypanel";
import Paginator from "primevue/paginator";
import Panel from "primevue/panel";
import ScrollTop from "primevue/scrolltop";
import { useConfirm } from "primevue/useconfirm";

const { getAnnotationKeys } = useAnnotate();
const { pluralize } = useUtilFunctions();
const notify = useNotifications();
const { cancelDeltaFile } = useDeltaFiles();
const displayCancelBatchingDialog = ref(false);
const batchSize = 500;
const maxToastDidDisplay = 10;
const confirm = useConfirm();
dayjs.extend(utc);
const hasPermission = inject("hasPermission");
const params = useUrlSearchParams("history");
const { getDeltaFileSearchData, getEnumValuesByEnumType } = useDeltaFilesQueryBuilder();
const { duration, shortTimezone } = useUtilFunctions();
const { topicNames: topicOptions, getAllTopics } = useTopics();
const { allDataSourceFlowNames, fetchAllDataSourceFlowNames, dataSinks: dataSinkOptions, fetchDataSinkNames, transforms: transformOptions, fetchTransformNames } = useFlows();
const route = useRoute();
const useURLSearch = ref(false);
const uiConfig = inject("uiConfig");
const selectedDids = ref([]);
const menu = ref();
const customCalendarRef = ref(null);
const retryResumeDialog = ref();
const annotateDialog = ref();
const annotationsOverlay = ref(null);
const newAnnotationKey = ref(null);
const newAnnotationValue = ref(null);
const loading = ref(true);
const totalRecords = ref(0);
const collapsedSearchOption = ref(true);
const tableData = ref([]);
const formattedDataSourceNames = ref([]);
const annotationKeys = ref([]);
const batchCompleteValue = ref(0);
const ackErrorsDialog = ref({
  dids: [],
  visible: false,
});
const selectedErrorDids = computed(() => {
  return _
    .chain(selectedDids.value)
    .filter((selected) => selected.stage === "ERROR")
    .map('did')
    .value();
});

// Advanced Options Dropdown Variables
const annotationsKeysOptions = ref([]);
const stageOptions = ref([]);
const booleanOptions = ref([true, false]);
const sizeUnitsOptionsMap = ref(
  new Map([
    ["B", { multiplier: 1 }],
    ["kB", { multiplier: 1000 }],
    ["MB", { multiplier: 1000000 }],
    ["GB", { multiplier: 1000000000 }],
  ])
);
const sizeTypesOptions = ref(["Ingress", "Total"]);

const setupWatchers = () => {
  watch(
    () => [model.value.startTimeDate, model.value.endTimeDate],
    () => {
      fetchDeltaFilesData();
    }
  );

  watch(
    () => model.value.validatedAnnotations,
    () => {
      fetchDeltaFilesData();
    },
    { deep: true }
  );

  watch(
    () => [model.value.sizeMin, model.value.sizeMax, model.value.stage, model.value.egressed, model.value.filtered, model.value.testMode, model.value.requeueMin, model.value.replayable, model.value.terminalStage, model.value.pendingAnnotations, model.value.paused, model.value.pinned],
    () => {
      fetchDeltaFilesData();
    }
  );

  watch(
    () => [model.value.dataSinks, model.value.dataSources, model.value.transforms, model.value.topics],
    _.debounce(
      () => {
        fetchDeltaFilesData();
      },
      500,
      { leading: false, trailing: true }
    )
  );

  watch(
    () => model.value.fileName,
    _.debounce(
      () => {
        fetchDeltaFilesData();
      },
      500,
      { leading: false, trailing: true }
    )
  );

  watch(
    () => model.value.filteredCause,
    _.debounce(
      () => {
        {
          if (model.value.filteredCause == "") {
            model.value.filteredCause = null;
          } else {
            fetchDeltaFilesData();
          }
        }
      },
      500,
      { leading: false, trailing: true }
    ),
    { deep: true }
  );

  watch(
    () => [model.value.sizeType, model.value.sizeUnit],
    () => {
      if (model.value.sizeMin || model.value.sizeMax) {
        fetchDeltaFilesData();
      }
      setPersistedParams();
    }
  );
};

onBeforeMount(async () => {
  queryParamsModel.value = _.cloneDeep(defaultQueryParamsTemplate);
  useURLSearch.value = route.fullPath.includes("search?");
  await fetchAnnotationKeys();
  getPersistedParams();
  fetchDropdownOptions();
  await nextTick();
  await fetchDeltaFilesDataNoDebounce();
  setupWatchers();
});

// Fetches all the options used in the dropdown
const fetchDropdownOptions = async () => {
  await fetchAllDataSourceFlowNames();
  formatDataSourceNames();
  fetchDataSinkNames();
  fetchStages();
  fetchTransformNames();
  getAllTopics();
};

const formatDataSourceNames = () => {
  if (!_.isEmpty(allDataSourceFlowNames.value.restDataSource)) {
    formattedDataSourceNames.value.push({ group: "Rest Data Sources", sources: allDataSourceFlowNames.value.restDataSource.map((s) => ({ label: s })) });
  }
  if (!_.isEmpty(allDataSourceFlowNames.value.timedDataSource)) {
    formattedDataSourceNames.value.push({ group: "Timed Data Sources", sources: allDataSourceFlowNames.value.timedDataSource.map((s) => ({ label: s })) });
  }
};

const fetchStages = async () => {
  const enumsStageTypes = await getEnumValuesByEnumType("DeltaFileStage");
  stageOptions.value = _.uniq(_.map(enumsStageTypes.data.__type.enumValues, "name"));
};

const timestampFormat = "YYYY-MM-DD HH:mm:ss";
const defaultStartTimeDate = computed(() => {
  const date = dayjs().utc();
  return (uiConfig.useUTC ? date : date.local()).startOf("day");
});
const defaultEndTimeDate = computed(() => {
  const date = dayjs().utc();
  return (uiConfig.useUTC ? date : date.local()).endOf("day");
});

const resetDefaultTimeDate = computed(() => {
  return [new Date(defaultStartTimeDate.value.format(timestampFormat)), new Date(defaultEndTimeDate.value.format(timestampFormat))];
});

const getPage = computed(() => {
  return model.value.page === null || model.value.page === undefined ? 0 : (model.value.page - 1) * model.value.perPage;
});

const updateInputDateTime = async (startDate, endDate) => {
  model.value.startTimeDate = startDate;
  model.value.endTimeDate = endDate;
};

const dateToISOString = (dateData) => {
  return dayjs(dateData).utc(uiConfig.useUTC).toISOString();
};

const isoStringToDate = (isoStringData) => {
  return uiConfig.useUTC ? dayjs(isoStringData).add(new Date().getTimezoneOffset(), "minute").toDate() : dayjs(isoStringData).toDate();
};

// Variable containing default values of query options used for searching
const defaultQueryParamsTemplate = {
  // Paginator query options
  offset: 0,
  perPage: 20,
  sortDirection: "DESC",
  sortField: "modified",
  // Advanced Options query options
  startTimeDate: new Date(defaultStartTimeDate.value.format(timestampFormat)),
  endTimeDate: new Date(defaultEndTimeDate.value.format(timestampFormat)),
  fileName: null,
  pendingAnnotations: null,
  validatedAnnotations: [],
  annotations: [],
  dataSources: [],
  dataSinks: [],
  transforms: [],
  topics: [],
  filteredCause: null,
  requeueMin: null,
  stage: null,
  egressed: null,
  filtered: null,
  testMode: null,
  terminalStage: null,
  replayable: null,
  paused: null,
  sizeMin: null,
  sizeMax: null,
  sizeType: sizeTypesOptions.value[0],
  sizeUnit: [...sizeUnitsOptionsMap.value.keys()][0],
  ingressBytesMin: null,
  ingressBytesMax: null,
  totalBytesMin: null,
  totalBytesMax: null,
  pinned: null,
};

const queryParamsModel = ref({});

const model = computed({
  get() {
    return new Proxy(queryParamsModel.value, {
      set(obj, key, value) {
        model.value = { ...obj, [key]: value ?? defaultQueryParamsTemplate[key] };
        return true;
      },
    });
  },
  set(newValue) {
    Object.assign(
      queryParamsModel.value,
      _.mapValues(newValue, (v) => (v === "" ? null : v))
    );
  },
});

const pageReportTemplate = computed(() => {
  const total = totalRecords.value == 10000 ? "many" : "{totalRecords}";
  return `{first} - {last} of ${total}`;
});

// Variable containing all the info needed to dynamically build the Advanced Options Panel.
const advanceOptionsPanelInfo = computed(() => {
  return [
    // The Advanced Options fields are broken up into three columns. The fields are sorted in ascending order in each column by the 'order' field.
    // First Column fields
    { field: "fileName", column: 1, order: 1, componentType: "InputText", label: "Filename:", placeholder: "Filename", class: "p-inputtext input-area-height responsive-width" },
    { field: "dataSources", column: 1, order: 2, componentType: "MultiSelect", label: "Data Sources:", placeholder: "Select a Data Source", options: formattedDataSourceNames.value, optionGroupLabel: "group", optionGroupChildren: "sources", optionLabel: "label", optionValue: "label", filter: true, class: "deltafi-input-field responsive-width" },
    { field: "transforms", column: 1, order: 3, componentType: "MultiSelect", label: "Transforms:", placeholder: "Select a Transform", options: transformOptions.value, class: "deltafi-input-field responsive-width" },
    { field: "dataSinks", column: 1, order: 4, componentType: "MultiSelect", label: "Data Sinks:", placeholder: "Select a Data Sink", options: dataSinkOptions.value, class: "deltafi-input-field responsive-width" },
    { field: "topics", column: 1, order: 4, componentType: "MultiSelect", label: "Topics:", placeholder: "Select a Topic", options: topicOptions.value, class: "deltafi-input-field responsive-width" },
    { field: "size", column: 1, order: 5, componentType: "SizeUnit", label: "Size:" },
    // 2nd Column fields
    { field: "testMode", column: 2, order: 1, componentType: "Dropdown", label: "Test Mode:", placeholder: "Select if in Test Mode", options: booleanOptions.value, formatOptions: true, class: "deltafi-input-field min-width" },
    { field: "replayable", column: 2, order: 2, componentType: "Dropdown", label: "Replayable:", placeholder: "Select if Replayable", options: booleanOptions.value, formatOptions: true, class: "deltafi-input-field min-width" },
    { field: "terminalStage", column: 2, order: 3, componentType: "Dropdown", label: "Terminal Stage:", placeholder: "Select if Terminal Stage", options: booleanOptions.value, formatOptions: true, class: "deltafi-input-field min-width" },
    { field: "egressed", column: 2, order: 4, componentType: "Dropdown", label: "Egressed:", placeholder: "Select if Egressed", options: booleanOptions.value, formatOptions: true, class: "deltafi-input-field min-width" },
    { field: "paused", column: 2, order: 5, componentType: "Dropdown", label: "Paused:", placeholder: "Select if Paused", options: booleanOptions.value, formatOptions: true, class: "deltafi-input-field min-width" },
    { field: "filtered", column: 2, order: 5, componentType: "Dropdown", label: "Filtered:", placeholder: "Select if Filtered", options: booleanOptions.value, formatOptions: true, class: "deltafi-input-field min-width" },
    // 3nd Column fields
    { field: "filteredCause", column: 3, order: 1, componentType: "InputText", label: "Filtered Cause:", placeholder: "Filtered Cause", class: "deltafi-input-field min-width" },
    { field: "requeueMin", column: 3, order: 2, componentType: "InputNumber", label: "Requeue Count:", placeholder: "Min", class: "p-inputnumber input-area-height" },
    { field: "stage", column: 3, order: 3, componentType: "Dropdown", label: "Stage:", placeholder: "Select a Stage", options: stageOptions.value, formatOptions: false, class: "deltafi-input-field min-width" },
    { field: "pinned", column: 3, order: 4, componentType: "Dropdown", label: "Pinned:", placeholder: "Select if DeltaFile is pinned", options: booleanOptions.value, formatOptions: true, class: "deltafi-input-field min-width" },
    { field: "pendingAnnotations", column: 3, order: 5, componentType: "Dropdown", label: "Pending Annotations:", placeholder: "Select if Pending Annotations", options: booleanOptions.value, formatOptions: true, class: "deltafi-input-field min-width" },
    { field: "annotations", column: 3, order: 6, componentType: "Annotations", label: "Annotations:" },
  ];
});

const calculatedAggregateParams = computed(() => {
  const multiplier = sizeUnitsOptionsMap.value.get(model.value.sizeUnit).multiplier;
  const reformatAnnotations = [...model.value.validatedAnnotations];
  // Reformat the annotations by removing the 'valid' key before sending to the backend
  if (reformatAnnotations.length > 0) {
    reformatAnnotations.forEach((e, index) => (reformatAnnotations[index] = _.omit(e, ["valid"])));
  }
  return {
    ingressBytesMin: model.value.sizeMin && _.isEqual(model.value.sizeType, "Ingress") ? model.value.sizeMin * multiplier : null,
    ingressBytesMax: model.value.sizeMax && _.isEqual(model.value.sizeType, "Ingress") ? model.value.sizeMax * multiplier : null,
    totalBytesMin: model.value.sizeMin && _.isEqual(model.value.sizeType, "Total") ? model.value.sizeMin * multiplier : null,
    totalBytesMax: model.value.sizeMax && _.isEqual(model.value.sizeType, "Total") ? model.value.sizeMax * multiplier : null,
    annotations: reformatAnnotations,
  };
});

const validAnnotation = (key) => {
  return annotationKeys.value.includes(key);
};

const invalidAnnotationTooltip = (key) => {
  return `An annotation with the key '${key}' does not currently exist in the system.`;
};

const fetchAnnotationKeys = async () => {
  annotationKeys.value = await getAnnotationKeys();
  annotationsKeysOptions.value = annotationKeys.value.map((key) => {
    return { key: key };
  });
};

const clearOptions = () => {
  model.value = JSON.parse(JSON.stringify(_.pick(defaultQueryParamsTemplate, openPanel)));
  setPersistedParams();
};

const showAnnotationsOverlay = (event) => {
  annotationsOverlay.value.toggle(event);
};

const removeAnnotationItem = (item) => {
  const index = model.value.validatedAnnotations.indexOf(item);
  model.value.validatedAnnotations.splice(index, 1);
  model.value.annotations.splice(index, 1);
};

const addAnnotationItem = (key, value) => {
  model.value.validatedAnnotations.push({ key: key, value: value, valid: validAnnotation(key) });
};

const addAnnotationItemEvent = () => {
  if (newAnnotationKey.value && newAnnotationValue.value) {
    addAnnotationItem(newAnnotationKey.value.key, newAnnotationValue.value);
    newAnnotationKey.value = null;
    newAnnotationValue.value = null;
    annotationsOverlay.value.toggle();
  }
};

const fetchDeltaFilesDataNoDebounce = async () => {
  model.value = _.merge(model.value, calculatedAggregateParams.value);
  setPersistedParams();
  loading.value = true;
  const data = await getDeltaFileSearchData(dateToISOString(model.value.startTimeDate), dateToISOString(model.value.endTimeDate), model.value);
  tableData.value = data.data.deltaFiles.deltaFiles;
  loading.value = false;
  totalRecords.value = data.data.deltaFiles.totalCount;
};

const refreshDeltaFilesData = async () => {
  await customCalendarRef.value.refreshUpdateDateTime();
  fetchDeltaFilesData();
  fetchAnnotationKeys();
};

const fetchDeltaFilesData = async () => {
  setPersistedParams();
  fetchDeltaFilesDataDebounce();
};

const fetchDeltaFilesDataDebounce = _.debounce(
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
  model.value.offset = event.first;
  model.value.perPage = event.rows;
  model.value.sortField = event.sortField;
  model.value.sortDirection = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchDeltaFilesData();
};

const actionRowClass = (data) => {
  return data.stage === "ERROR" ? "table-danger action-error" : null;
};

const onPage = (event) => {
  model.value.page = event.page + 1;
  model.value.offset = event.first;
  model.value.perPage = event.rows;
  fetchDeltaFilesDataNoDebounce();
};

const openPanel = ["fileName", "filteredCause", "requeueMin", "stage", "dataSources", "dataSinks", "transforms", "topics", "egressed", "filtered", "testMode", "replayable", "terminalStage", "sizeMin", "sizeMax", "validatedAnnotations", "pendingAnnotations", "annotations", "sizeUnit", "sizeType", "paused", "pinned"];

const decodePersistedParams = (obj) =>
  _.transform(obj, (r, v, k) => {
    if (["startTimeDate", "endTimeDate"].includes(k)) {
      r[k] = isoStringToDate(v);
    } else if (["egressed", "filtered", "testMode", "replayable", "terminalStage", "pendingAnnotations", "paused", "pinned"].includes(k)) {
      r[k] = JSON.parse(v);
    } else if (["requeueMin", "sizeMin", "sizeMax", "perPage", "page"].includes(k)) {
      r[k] = Number(v);
    } else if (["dataSources", "dataSinks", "transforms", "topics"].includes(k)) {
      r[k] = v.split(",");
    } else if (["annotations"].includes(k)) {
      const annotationsArrayVal = getAnnotationsArray(v);
      r["validatedAnnotations"] = annotationsArrayVal || [];
    } else {
      r[k] = v;
    }
  });

const getPersistedParams = () => {
  let getPersistedState = {};
  if (useURLSearch.value) {
    getPersistedState = _.cloneDeepWith(params, decodePersistedParams);
  } else {
    getPersistedState = _.cloneDeepWith(queryState.value, decodePersistedParams);
  }
  model.value = _.merge(model.value, getPersistedState);

  collapsedSearchOption.value = !activeAdvancedOptions.value;
  model.value.offset = getPage.value;
};

const queryState = useStorage("search-page-persisted-params", {}, sessionStorage, { serializer: StorageSerializers.object });

const encodePersistedParams = (obj) =>
  _.transform(obj, (r, v, k) => {
    if (["startTimeDate", "endTimeDate"].includes(k)) {
      r[k] = dateToISOString(v);
    } else if (["egressed", "filtered", "testMode", "replayable", "terminalStage", "pendingAnnotations", "paused", "pinned"].includes(k)) {
      r[k] = Boolean(v);
    } else if (["requeueMin", "sizeMin", "sizeMax", "perPage", "page"].includes(k)) {
      r[k] = Number(v);
    } else if (["dataSources", "dataSinks", "transforms", "topics"].includes(k)) {
      r[k] = String(v);
    } else if (["annotations"].includes(k)) {
      r[k] = getAnnotationsString(v);
    } else {
      r[k] = v;
    }
  });

const setPersistedParams = async () => {
  let persistedQueryState = _.cloneDeep(model.value);
  // Remove any value that have not changed from the original defaultQueryParamsTemplate value it was set at
  persistedQueryState = _.omitBy(persistedQueryState, function (v, k) {
    return JSON.stringify(defaultQueryParamsTemplate[k]) === JSON.stringify(v);
  });
  // Remove values that we dont want to persist(e.g. paginator values and computed)
  persistedQueryState = _.omit(persistedQueryState, ["offset", "sortDirection", "sortField", "ingressBytesMin", "ingressBytesMax", "totalBytesMin", "totalBytesMax", "validatedAnnotations"]);
  persistedQueryState = _.cloneDeepWith(persistedQueryState, encodePersistedParams);
  queryState.value = persistedQueryState;

  // Set url search params
  //null out param keys before setting new ones
  Object.keys(params).forEach((i) => (params[i] = null));
  for (const key in persistedQueryState) {
    params[key] = persistedQueryState[key];
  }

  // Set the browser history to make the back button work
  await nextTick();
  window.history.state.current = `/deltafile/search${window.location.search}`;
  await customCalendarRef.value.setDateTimeToday();
};

const getAnnotationsString = (arrayData) => {
  const annotationsString = arrayData
    .map((pair) => {
      return pair.key.concat(":", pair.value);
    })
    .join(",");
  return annotationsString;
};

const getAnnotationsArray = (stringData) => {
  const annotationsArray = stringData.split(",");
  return annotationsArray.map((pair) => {
    const keyValuePair = pair.split(":");
    return {
      key: keyValuePair[0],
      value: keyValuePair[1],
      valid: validAnnotation(keyValuePair[0]),
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

const formatOption = (formatOption, dropdownOption) => {
  if (formatOption) {
    return _.capitalize(dropdownOption);
  } else {
    return dropdownOption;
  }
};

const menuItems = ref([
  {
    label: "Clear Selected",
    icon: "fas fa-times fa-fw",
    command: () => {
      selectedDids.value = [];
    },
    disabled: computed(() => selectedDids.value.length == 0),
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
      retryResumeDialog.value.showConfirmDialog();
    },
    visible: computed(() => hasPermission("DeltaFileReplay")),
    disabled: computed(() => selectedDids.value.length == 0),
  },
  {
    label: "Annotate Selected",
    icon: "fa-solid fa-tags fa-fw",
    visible: computed(() => hasPermission("DeltaFileAnnotate")),
    command: () => {
      annotateDialog.value.showDialog();
    },
    disabled: computed(() => selectedDids.value.length == 0),
  },
  {
    label: "Acknowledge Selected",
    icon: "fas fa-check-circle fa-fw",
    command: () => {
      acknowledgeClickConfirm();
    },
    visible: computed(() => hasPermission("DeltaFileAcknowledge")),
    disabled: computed(() => selectedErrorDids.value.length == 0 ),
  },
  {
    label: "Cancel Selected",
    icon: "fas fa-power-off fa-fw",
    command: () => {
      onCancelClick();
    },
    visible: computed(() => hasPermission("DeltaFileCancel")),
    disabled: computed(() => selectedDids.value.length == 0),
  },
]);

const filterSelectedDids = computed(() => {
  const dids = selectedDids.value.map((selectedDID) => {
    return selectedDID.did;
  });
  return dids;
});

const activeAdvancedOptions = computed(() => {
  let advancedOptionsState = _.cloneDeep(_.pick(model.value, openPanel));
  advancedOptionsState = _.omitBy(advancedOptionsState, function (v, k) {
    return JSON.stringify(defaultQueryParamsTemplate[k]) === JSON.stringify(v);
  });
  return !_.isEmpty(advancedOptionsState);
});

const onCancelClick = () => {
  const pluralized = pluralize(selectedDids.value.length, "DeltaFile");
  confirm.require({
    message: `Are you sure you want to cancel ${pluralized}?`,
    header: "Confirm Cancel",
    icon: "pi pi-exclamation-triangle",
    acceptLabel: "Yes",
    rejectLabel: "Cancel",
    accept: () => {
      onCancel();
    },
    reject: () => { },
  });
};

const onCancel = async () => {
  const batchedDids = getBatchDids(filterSelectedDids.value);
  displayCancelBatchingDialog.value = selectedDids.value.length > batchSize ? true : false;
  batchCompleteValue.value = 0;
  let completedBatches = 0;
  let cancelResponses = [];
  for (const dids of batchedDids) {
    const cancelResponse = await cancelDeltaFile(dids);
    cancelResponses = cancelResponses.concat(cancelResponse);
    completedBatches += dids.length;
    batchCompleteValue.value = Math.round((completedBatches / selectedDids.value.length) * 100);
  }
  displayCancelBatchingDialog.value = false;
  const results = Object.groupBy(cancelResponses, ({ success }) => success);
  if (results.true) {
    const theDids = results.true.map(({ did }) => did);
    const pluralized = pluralize(theDids.length, "DeltaFile");
    const links = theDids.slice(0, maxToastDidDisplay).map((did) => `<a href="/deltafile/viewer/${did}" class="monospace">${did}</a>`);
    notify.success(`Successfully Cancelled ${pluralized}`, links.join(", "));
  }
  if (results.false) {
    const theDids = results.false.map(({ did }) => did);
    const pluralized = pluralize(theDids.length, "DeltaFile");
    const links = theDids.slice(0, maxToastDidDisplay).map((did) => `<a href="/deltafile/viewer/${did}" class="monospace">${did}</a>`);
    notify.error(`Failed to Cancel ${pluralized}`, links.join(", "));
  }
};

const getBatchDids = (allDids) => {
  const res = [];
  for (let i = 0; i < allDids.length; i += batchSize) {
    const chunk = allDids.slice(i, i + batchSize);
    res.push(chunk);
  }
  return res;
};

const acknowledgeClickConfirm = () => {
  ackErrorsDialog.value.dids = selectedErrorDids.value;
  ackErrorsDialog.value.visible = true;
};

const onAcknowledged = (dids, reason) => {
  selectedDids.value = [];
  ackErrorsDialog.value.dids = [];
  ackErrorsDialog.value.visible = false;
  const pluralized = pluralize(dids.length, "Error");
  notify.success(`Successfully acknowledged ${pluralized}`, reason);
  fetchDeltaFilesData();
};
</script>

<style>
.deltafile-search-page {
  .align-advanced-options-header-title {
    align-content: flex-start;
  }

  .align-advanced-options-header {
    align-self: center;
  }

  label {
    font-weight: 500;
  }

  .p-multiselect.p-multiselect-chip .p-multiselect-token {
    padding-bottom: 1px;
    padding-top: 1px;
  }

  .input-area-height {
    height: 32px;
  }

  .input-area-width {
    width: 335px;
  }

  .vdpr-datepicker__calendar-dialog {
    margin-left: -275px;
  }

  .vdpr-datepicker__button-reset {
    color: white;
    background-color: #dc3545;
    border-color: #d00f27;
  }

  .vdpr-datepicker__switch {
    margin-top: 6px;
  }

  .vdpr-datepicker {
    position: relative;
  }

  .p-panel-content {
    padding: 1.25rem 0.75rem 1.5rem !important;
  }

  .search-options-wrapper {
    display: flex;
  }

  .flex-row {
    display: flex;
    flex-direction: row;
    flex-wrap: wrap;
    width: 100%;
  }

  .flex-column {
    display: flex;
    flex-direction: column;
    flex-basis: 100%;
    flex: 2;
    margin: 0 0.75rem;
  }

  .flex-column-small {
    flex: 1;
  }

  .advanced-search-button-container {
    margin-top: 1rem;
    display: flex;
    align-items: center;
    justify-content: center;

    button {
      width: 10rem;
    }
  }

  .invalid-chip {
    background: var(--warning);
    color: var(--black);
  }

  .valid-chip {
    background: var(--info);
    color: var(--white);
  }

  .add-annotations-btn {
    cursor: pointer;
    background: var(--secondary);
    color: var(--primary-color-text);
    padding: 0 0.25rem;
  }

  .size-container {
    >* {
      vertical-align: middle !important;
    }
  }

  .icon-index {
    z-index: 1;
  }

  td.filename-column {
    overflow-wrap: anywhere;
  }

  td.did-column {
    width: 8rem;
  }

  .results {
    .p-paginator-current {
      margin-right: 0.75rem;
      font-weight: 500;
    }

    .p-panel-header {
      padding: 0 1.25rem;

      .p-panel-title {
        padding: 1rem 0;
      }

      .p-panel-header-icon {
        margin-top: 0.25rem;
        margin-right: 0;
      }
    }

    .p-panel-content {
      padding: 0 !important;
      border: none;

      td.filename-column {
        overflow-wrap: anywhere;
      }
    }
  }

  .p-paginator {
    background: inherit !important;
    color: inherit !important;
    border: none !important;
    padding: 0 !important;
    font-size: inherit !important;

    .p-paginator-current {
      background: unset;
      color: unset;
      border: unset;
    }
  }

  .p-datatable.p-datatable-striped .p-datatable-tbody>tr.p-highlight {
    color: #ffffff;

    a,
    button {
      color: #eeeeee;
    }
  }

  /* This order matters */
  @media (min-width: 1024px) {
    .responsive-width {
      min-width: 32rem;
      max-width: 32rem;
    }
  }

  @media (min-width: 1200px) {
    .responsive-width {
      min-width: 36rem;
      max-width: 36rem;
    }
  }

  @media (min-width: 1600px) {
    .responsive-width {
      min-width: 42rem;
      max-width: 42rem;
    }
  }

  @media (min-width: 1900px) {
    .responsive-width {
      min-width: 44rem;
      max-width: 44rem;
    }
  }
}
</style>
