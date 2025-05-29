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
        <Paginator v-if="errors.length > 0" :rows="perPage" :first="getPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalErrors" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage($event)" />
      </template>
      <DataTable id="errorsTable" v-model:expanded-rows="expandedRows" v-model:selection="selectedErrors" v-model:filters="filters" responsive-layout="scroll" selection-mode="multiple" data-key="did" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="errors" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalErrors" :row-hover="true" filter-display="menu" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
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
            <div v-if="data.name > 28" v-tooltip.top="data.name" class="truncate">
              {{ data.name }}
            </div>
            <div v-else>
              {{ data.name }}
            </div>
          </template>
        </Column>
        <Column field="dataSource" header="Data Source" :sortable="true" />
        <Column field="last_error_cause" header="Last Error" filter-field="last_error_cause" :show-filter-menu="false" :show-filter-match-modes="false" :show-apply-button="false" :show-clear-button="false" class="last-error-column">
          <template #body="{ data: deltaFile }">
            <ErrorAcknowledgedBadge v-if="deltaFile.lastErroredFlow.errorAcknowledged" :reason="deltaFile.lastErroredFlow.errorAcknowledgedReason" :timestamp="deltaFile.lastErroredFlow.errorAcknowledged" class="mr-1" />
            <AutoResumeBadge v-if="deltaFile.lastErroredAction.nextAutoResume !== null" :timestamp="deltaFile.lastErroredAction.nextAutoResume" :reason="deltaFile.lastErroredAction.nextAutoResumeReason" />
            {{ deltaFile.lastErroredAction.errorCause }}
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
        <template #expansion="error">
          <div class="errors-Subtable">
            <DataTable v-model:expanded-rows="expandedRows" data-key="name" responsive-layout="scroll" :value="error.data.flows" :row-hover="false" striped-rows class="p-datatable-sm p-datatable-gridlines" :row-class="actionRowClass" @row-click="actionRowClick">
              <Column class="expander-column" :expander="true" />
              <Column field="name" header="Name" />
              <Column field="state" header="State" />
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
              <template #expansion="slotProps">
                <div class="errors-Subtable">
                  <DataTable responsive-layout="scroll" data-key="name" class="p-datatable-sm p-datatable-gridlines" striped-rows :value="slotProps.data.actions" :row-class="actionRowClass" @row-click="actionRowClick">
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
                    <Column field="errorCause" header="Error Cause" :sortable="true" />
                  </DataTable>
                </div>
              </template>
            </DataTable>
          </div>
        </template>
      </DataTable>
    </Panel>
    <ErrorViewerDialog v-model:visible="errorViewer.visible" :action="errorViewer.action" @update="onRefresh" />
    <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="ackErrorsDialog.dids" @acknowledged="onAcknowledged" />
    <AnnotateDialog ref="annotateDialog" :dids="filterSelectedDids" @refresh-page="onRefresh()" />
    <ResumeDialog ref="resumeDialog" :did="filterSelectedDids" @refresh-page="onRefresh" />
    <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="Add New Auto Resume Rule" required-permission="ResumePolicyCreate" dialog-width="75vw" :row-data-prop="autoResumeSelected">
      <span id="allPanelAutoResumeDialog" />
    </DialogTemplate>
  </div>
</template>

<script setup>
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import AnnotateDialog from "@/components/AnnotateDialog.vue";
import AutoResumeBadge from "@/components/errors/AutoResumeBadge.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import DidLink from "@/components/DidLink.vue";
import ErrorAcknowledgedBadge from "@/components/errors/AcknowledgedBadge.vue";
import ErrorViewerDialog from "@/components/errors/ErrorViewerDialog.vue";
import ResumeDialog from "@/components/errors/ResumeDialog.vue";
import Timestamp from "@/components/Timestamp.vue";
import useErrors from "@/composables/useErrors";
import useErrorsSummary from "@/composables/useErrorsSummary";
import useErrorCount from "@/composables/useErrorCount";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
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
import { FilterMatchMode } from "primevue/api";

const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");
const resumeDialog = ref();
const { fetchAllMessage: getAllErrorsMessage } = useErrorsSummary();
const { pluralize } = useUtilFunctions();
const { fetchErrorCount } = useErrorCount();
const emit = defineEmits(["refreshErrors", "errorMessageChanged:errorMessage"]);
const notify = useNotifications();
const loading = ref(true);
const menu = ref();
const errors = ref([]);
const expandedRows = ref([]);
const totalErrors = ref(0);
const offset = ref(0);
const perPage = ref();
const page = ref(null);
const sortField = ref("modified");
const sortDirection = ref("DESC");
const selectedErrors = ref([]);
const annotateDialog = ref();
const ackErrorsDialog = ref({
  dids: [],
  visible: false,
});
const props = defineProps({
  flow: {
    type: Object,
    required: false,
    default: undefined,
  },
  acknowledged: {
    type: [Boolean, null],
    required: true,
  },
  errorsMessageSelected: {
    type: String,
    required: false,
    default: undefined,
  },
});
const errorViewer = ref({
  visible: false,
  action: {},
});
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
      selectedErrors.value = errors.value;
    },
  },
  {
    separator: true,
    visible: computed(() => hasSomePermissions("DeltaFileAcknowledge", "DeltaFileResume", "ResumePolicyCreate")),
  },
  {
    label: "Acknowledge Selected",
    icon: "fas fa-check-circle fa-fw",
    command: () => {
      acknowledgeClickConfirm();
    },
    visible: computed(() => hasPermission("DeltaFileAcknowledge")),
    disabled: computed(() => selectedErrors.value.length == 0),
  },
  {
    label: "Annotate Selected",
    icon: "fa-solid fa-tags fa-fw",
    visible: computed(() => hasPermission("DeltaFileAnnotate")),
    command: () => {
      annotateDialog.value.showDialog();
    },
  },
  {
    label: "Resume Selected",
    icon: "fas fa-redo fa-fw",
    command: () => {
      resumeDialog.value.showConfirmDialog();
    },
    visible: computed(() => hasPermission("DeltaFileResume")),
    disabled: computed(() => selectedErrors.value.length == 0),
  },
  {
    label: "Create Auto Resume Rule",
    icon: "fas fa-clock-rotate-left fa-flip-horizontal fa-fw",
    command: () => {
      document.getElementById("allPanelAutoResumeDialog").click();
    },
    visible: computed(() => hasPermission("ResumePolicyCreate")),
    disabled: computed(() => selectedErrors.value.length == 0 || selectedErrors.value.length > 1),
  },
]);

const onRefresh = () => {
  unSelectAllRows();
  fetchErrors();
};

const { data: response, fetch: getErrors } = useErrors();
const filters = ref({
  last_error_cause: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

const fetchErrors = async () => {
  await getPersistedParams();
  const flowName = props.flow?.name != null ? props.flow?.name : null;
  const flowType = props.flow?.type != null ? props.flow?.type : null;
  const errorMessage = filters.value.last_error_cause.value != null ? filters.value.last_error_cause.value.message : null;
  loading.value = true;
  await getErrors(props.acknowledged, offset.value, perPage.value, sortField.value, sortDirection.value, flowName, flowType, errorMessage);
  errors.value = response.value.deltaFiles.deltaFiles;
  totalErrors.value = response.value.deltaFiles.totalCount;
  loading.value = false;
  errors.value = errors.value.map((deltaFile) => {
    return {
      ...deltaFile,
      lastErroredFlow: latestErrorFlow(deltaFile),
      lastErroredAction: latestErrorAction(deltaFile),
    };
  });
};

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

const getPage = computed(() => {
  return page.value === null || page.value === undefined ? 0 : (page.value - 1) * perPage.value;
});

const onRowContextMenu = (event) => {
  if (selectedErrors.value.length <= 0) {
    selectedErrors.value = [event.data];
  }
};

const onPanelRightClick = (event) => {
  menu.value.show(event);
};

const acknowledgeClickConfirm = () => {
  ackErrorsDialog.value.dids = selectedErrors.value.map((selectedError) => {
    return selectedError.did;
  });
  ackErrorsDialog.value.visible = true;
};

const unSelectAllRows = async () => {
  selectedErrors.value = [];
};

const onAcknowledged = (dids, reason) => {
  unSelectAllRows();
  ackErrorsDialog.value.dids = [];
  ackErrorsDialog.value.visible = false;
  const pluralized = pluralize(dids.length, "Error");
  notify.success(`Successfully acknowledged ${pluralized}`, reason);
  fetchErrorCount();
  emit("refreshErrors");
};
const autoResumeSelected = computed(() => {
  const newResumeRule = {};
  if (!_.isEmpty(selectedErrors.value)) {
    const rowInfo = JSON.parse(JSON.stringify(selectedErrors.value[0]));
    newResumeRule["dataSource"] = rowInfo.dataSource;
    newResumeRule["action"] = rowInfo.lastErroredAction.name;
    newResumeRule["errorSubstring"] = rowInfo.lastErroredAction.errorCause;
    return newResumeRule;
  } else {
    return selectedErrors.value;
  }
});

const filterSelectedDids = computed(() => {
  const dids = selectedErrors.value.map((selectedError) => {
    return selectedError.did;
  });
  return dids;
});

const latestErrorAction = (deltaFile) => {
  return _.chain(deltaFile.flows)
    .map((flow) => flow.actions)
    .flatten()
    .filter((action) => action.state === "ERROR")
    .sortBy(["modified"])
    .value()[0];
};

const latestErrorFlow = (deltaFile) => {
  return _.chain(deltaFile.flows)
    .filter((flow) => flow.state === "ERROR")
    .sortBy(["modified"])
    .value()[0];
};

const actionRowClass = (action) => {
  if (action.state === "ERROR") return "table-danger action-error";
  if (action.state === "RETRIED") return "table-warning action-error";
};

const actionRowClick = (event) => {
  const action = event.data.actions ? event.data.actions.slice(-1)[0] : event.data;
  if (["ERROR", "RETRIED"].includes(action.state)) {
    errorViewer.value.action = action;
    errorViewer.value.visible = true;
  }
};

const onPage = async (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  page.value = event.page + 1;
  setPersistedParams();
  await nextTick();
  fetchErrors();
  emit("refreshErrors");
};

const onSort = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  sortField.value = event.sortField;
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchErrors();
};

defineExpose({
  fetchErrors,
  unSelectAllRows,
});
const setupWatchers = () => {
  watch(
    () => props.flow,
    () => {
      unSelectAllRows();
      fetchErrors();
    }
  );

  watch(
    () => props.errorsMessageSelected,
    () => {
      unSelectAllRows();
      filters.value.last_error_cause.value = props.errorsMessageSelected ? { message: props.errorsMessageSelected } : null;
    }
  );

  watch(
    () => props.acknowledged,
    () => {
      unSelectAllRows();
      fetchErrors();
    }
  );

  watch(
    () => filters.value.last_error_cause.value,
    () => {
      const errorMessage = filters.value.last_error_cause.value != null ? filters.value.last_error_cause.value.message : null;
      unSelectAllRows();
      fetchErrors();
      emit("errorMessageChanged:errorMessage", errorMessage);
    }
  );
};
onMounted(async () => {
  await getPersistedParams();
  filters.value.last_error_cause.value = props.errorsMessageSelected ? { message: props.errorsMessageSelected } : null;
  await fetchErrors();
  setupWatchers();
  getAllErrorsMessage(); // needed to get All the messages for
});

const getPersistedParams = async () => {
  const state = useStorage("errors-page-all-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  perPage.value = state.value.perPage || 20;
  page.value = state.value.page || 1;
  offset.value = getPage.value;
};

const setPersistedParams = () => {
  const state = useStorage("errors-page-all-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  state.value = {
    perPage: perPage.value,
    page: page.value,
  };
};
</script>

<style>
.all-panel {
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

  td.last-error-column {
    width: auto;
  }

  .p-column-filter-overlay {
    margin-left: -18px;
    max-width: 300px;
  }
}
</style>
