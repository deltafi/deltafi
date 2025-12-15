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

<!-- ABOUTME: Search page for querying DeltaFiles with advanced filtering options. -->
<!-- ABOUTME: Supports pagination, sorting, and bulk operations on search results. -->

<template>
  <div class="deltafile-search-page">
    <div>
      <PageHeader heading="DeltaFile Search">
        <SearchDateToolbar
          ref="searchToolbarRef"
          v-model:date-type="model.queryDateTypeOptions"
          :date-type-options="queryDateTypeOptions"
          :start-time-date="model.startTimeDate"
          :end-time-date="model.endTimeDate"
          :reset-default="resetDefaultTimeDate"
          :loading="loading"
          @date-change="onDateChange"
          @refresh="refreshDeltaFilesData"
        />
      </PageHeader>
    </div>
    <div class="row mb-3">
      <div class="col-12">
        <DeltaFileSearchOptions
          v-model="model"
          :active-advanced-options="activeAdvancedOptions"
          :formatted-data-source-names="formattedDataSourceNames"
          :data-sink-options="dataSinkOptions"
          :transform-options="transformOptions"
          :topic-options="topicOptions"
          :stage-options="stageOptions"
          :annotation-keys-options="annotationsKeysOptions"
          :size-units-options-map="sizeUnitsOptionsMap"
          :size-types-options="sizeTypesOptions"
          :collapsed="collapsedSearchOption"
          :annotation-validation-fn="validAnnotation"
          @clear-options="clearOptionsAndRefresh"
          @add-annotation="addAnnotationItem"
          @remove-annotation="removeAnnotationItem"
        />
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
  <ReplayDialog ref="replayDialog" :did="filterSelectedDids" @refresh-page="fetchDeltaFilesData()" />
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
import DeltaFileSearchOptions from "@/components/DeltaFileSearchOptions.vue";
import SearchDateToolbar from "@/components/SearchDateToolbar.vue";
import DidLink from "@/components/DidLink.vue";
import FormattedBytes from "@/components/FormattedBytes.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";
import ReplayDialog from "@/components/ReplayDialog.vue";
import Timestamp from "@/components/Timestamp.vue";
import useAnnotate from "@/composables/useAnnotate";
import useDeltaFilesQueryBuilder from "@/composables/useDeltaFilesQueryBuilder";
import useDeltaFileSearchFilters from "@/composables/useDeltaFileSearchFilters";
import useDeltaFiles from "@/composables/useDeltaFiles";
import useFlows from "@/composables/useFlows";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import useTopics from "@/composables/useTopics";
import { computed, inject, nextTick, onBeforeMount, ref, watch } from "vue";
import { useRoute } from "vue-router";

import _ from "lodash";

import Button from "primevue/button";
import Column from "primevue/column";
import ConfirmDialog from "primevue/confirmdialog";
import ContextMenu from "primevue/contextmenu";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import Menu from "primevue/menu";
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
const hasPermission = inject("hasPermission");
const { getDeltaFileSearchData, getEnumValuesByEnumType } = useDeltaFilesQueryBuilder();
const { duration } = useUtilFunctions();
const { topicNames: topicOptions, getAllTopics } = useTopics();
const { allDataSourceFlowNames, fetchAllDataSourceFlowNames, dataSinks: dataSinkOptions, fetchDataSinkNames, transforms: transformOptions, fetchTransformNames } = useFlows();
const route = useRoute();
const useURLSearch = ref(false);
const selectedDids = ref([]);
const menu = ref();
const searchToolbarRef = ref(null);
const replayDialog = ref();
const annotateDialog = ref();
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
  return _.chain(selectedDids.value)
    .filter((selected) => selected.stage === "ERROR")
    .map("did")
    .value();
});

// Use shared search filter composable
const {
  model,
  queryParamsModel,
  defaultTemplate: defaultQueryParamsTemplate,
  resetDefaultTimeDate,
  activeAdvancedOptions,
  sizeUnitsMap: sizeUnitsOptionsMap,
  sizeTypes: sizeTypesOptions,
  queryDateTypes: queryDateTypeOptions,
  loadPersistedParams,
  savePersistedParams,
  buildFilter,
  clearOptions,
  updateDateRange,
  dateToISOString,
  setupFilterWatchers,
} = useDeltaFileSearchFilters({ storageKey: "search-page-persisted-params", includePagination: true });

// Dropdown options
const annotationsKeysOptions = ref([]);
const stageOptions = ref([]);

const setupWatchers = () => {
  setupFilterWatchers(fetchDeltaFilesData);

  // Additional watcher for sortField based on queryDateTypeOptions (specific to this page)
  watch(
    () => model.value.queryDateTypeOptions,
    () => {
      if (_.isEqual(model.value.queryDateTypeOptions, "Created")) {
        model.value.sortField = "created";
      } else {
        model.value.sortField = "modified";
      }
    }
  );
};

onBeforeMount(async () => {
  queryParamsModel.value = _.cloneDeep(defaultQueryParamsTemplate);
  useURLSearch.value = route.fullPath.includes("search?");
  await fetchAnnotationKeys();
  loadPersistedParams();
  fetchDropdownOptions();
  await nextTick();
  await fetchDeltaFilesDataNoDebounce();
  collapsedSearchOption.value = !activeAdvancedOptions.value;
  setupWatchers();
});

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
  if (!_.isEmpty(allDataSourceFlowNames.value.onErrorDataSource)) {
    formattedDataSourceNames.value.push({ group: "On-Error Data Sources", sources: allDataSourceFlowNames.value.onErrorDataSource.map((s) => ({ label: s })) });
  }
};

const fetchStages = async () => {
  const enumsStageTypes = await getEnumValuesByEnumType("DeltaFileStage");
  stageOptions.value = _.uniq(_.map(enumsStageTypes.data.__type.enumValues, "name"));
};

const getPage = computed(() => {
  return model.value.page === null || model.value.page === undefined ? 0 : (model.value.page - 1) * model.value.perPage;
});

const pageReportTemplate = computed(() => {
  const total = totalRecords.value == 10000 ? "many" : "{totalRecords}";
  return `{first} - {last} of ${total}`;
});

const validAnnotation = (key) => {
  return annotationKeys.value.includes(key);
};

const fetchAnnotationKeys = async () => {
  annotationKeys.value = await getAnnotationKeys();
  annotationsKeysOptions.value = annotationKeys.value.map((key) => {
    return { key: key };
  });
};

const clearOptionsAndRefresh = () => {
  clearOptions();
  model.value.sortField = "modified";
};

const addAnnotationItem = (key, value) => {
  model.value.validatedAnnotations.push({ key, value, valid: validAnnotation(key) });
};

const removeAnnotationItem = (item) => {
  const index = model.value.validatedAnnotations.indexOf(item);
  model.value.validatedAnnotations.splice(index, 1);
  model.value.annotations.splice(index, 1);
};

const fetchDeltaFilesDataNoDebounce = async () => {
  savePersistedParams();
  loading.value = true;
  const filter = buildFilter();
  const data = await getDeltaFileSearchData(dateToISOString(model.value.startTimeDate), dateToISOString(model.value.endTimeDate), { ...model.value, ...filter });
  tableData.value = data.data.deltaFiles.deltaFiles;
  loading.value = false;
  totalRecords.value = data.data.deltaFiles.totalCount;
};

const onDateChange = (startDate, endDate) => {
  updateDateRange(startDate, endDate);
};

const refreshDeltaFilesData = async () => {
  await searchToolbarRef.value.refreshUpdateDateTime();
  fetchDeltaFilesData();
  fetchAnnotationKeys();
};

const fetchDeltaFilesData = async () => {
  savePersistedParams();
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
      replayDialog.value.showConfirmDialog();
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
    disabled: computed(() => selectedErrorDids.value.length == 0),
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
    reject: () => {},
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
  label {
    font-weight: 500;
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

  .p-datatable.p-datatable-striped .p-datatable-tbody > tr.p-highlight {
    color: #ffffff;

    a,
    button {
      color: #eeeeee;
    }
  }
}
</style>
