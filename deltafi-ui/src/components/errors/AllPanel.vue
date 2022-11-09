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
  <Panel header="DeltaFiles with Errors" @contextmenu="onPanelRightClick">
    <ContextMenu ref="menu" :model="menuItems" />
    <template #icons>
      <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
        <span class="fas fa-bars" />
      </Button>
      <Menu ref="menu" :model="menuItems" :popup="true" />
      <Paginator v-if="errors.length > 0" :rows="perPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalErrors" :rows-per-page-options="[10, 20, 50, 100, 1000]" class="p-panel-header" style="float: left" @page="onPage($event)"></Paginator>
    </template>
    <DataTable id="errorsTable" v-model:expandedRows="expandedRows" v-model:selection="selectedErrors" v-model:filters="filters" responsive-layout="scroll" selection-mode="multiple" data-key="did" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="errors" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalErrors" :row-hover="true" filter-display="menu" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
      <template #empty>No results to display.</template>
      <template #loading>Loading. Please wait...</template>
      <Column class="expander-column" :expander="true" />
      <Column field="did" header="DID" class="did-column">
        <template #body="{ data }">
          <DidLink :did="data.did" />
        </template>
      </Column>
      <Column field="sourceInfo.filename" header="Filename" :sortable="true" class="filename-column" />
      <Column field="sourceInfo.flow" header="Flow" :sortable="true" />
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
      <Column field="last_error_cause" header="Last Error" filter-field="last_error_cause" :show-filter-menu="true" :show-filter-match-modes="false" :show-apply-button="false" :show-clear-button="false">
        <template #body="{ data }">
          <ErrorAcknowledgedBadge v-if="data.errorAcknowledged" :reason="data.errorAcknowledgedReason" :timestamp="data.errorAcknowledged" class="mr-1" />
          {{ latestError(data.actions).errorCause }}
        </template>
        <template #filter="{ filterModel, filterCallback }">
          <Dropdown v-model="filterModel.value" placeholder="Select an Error Message" :options="errorsMessages" :filter="true" option-label="message" show-clear :editable="false" class="p-column-filter deltafi-input-field ml-3" @change="filterCallback()" />
        </template>
      </Column>
      <template #expansion="error">
        <div class="errors-Subtable">
          <DataTable responsive-layout="scroll" :value="error.data.actions" :row-hover="false" striped-rows class="p-datatable-sm p-datatable-gridlines" :row-class="actionRowClass" @row-click="actionRowClick">
            <Column field="name" header="Action" />
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
            <Column field="errorCause" header="Error Cause">
              <template #body="action">
                <span v-if="['ERROR', 'RETRIED'].includes(action.data.state) && action.data.errorCause !== null">{{ action.data.errorCause }}</span>
                <span v-else>N/A</span>
              </template>
            </Column>
          </DataTable>
        </div>
      </template>
    </DataTable>
  </Panel>
  <ErrorViewerDialog v-model:visible="errorViewer.visible" :action="errorViewer.action" />
  <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="ackErrorsDialog.dids" @acknowledged="onAcknowledged" />
  <MetadataDialog ref="metadataDialog" :did="filterSelectedDids" @update="onRefresh()" />
</template>

<script setup>
import Column from "primevue/column";
import Dropdown from "primevue/dropdown";
import DataTable from "primevue/datatable";
import Button from "primevue/button";
import Panel from "primevue/panel";
import Menu from "primevue/menu";
import ContextMenu from "primevue/contextmenu";
import ErrorViewerDialog from "@/components/errors/ViewerDialog.vue";
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import ErrorAcknowledgedBadge from "@/components/errors/AcknowledgedBadge.vue";
import Paginator from "primevue/paginator";
import DidLink from "@/components/DidLink.vue";
import Timestamp from "@/components/Timestamp.vue";
import useErrors from "@/composables/useErrors";
import useErrorCount from "@/composables/useErrorCount";
import MetadataDialog from "@/components/MetadataDialog.vue";
import useNotifications from "@/composables/useNotifications";
import { FilterMatchMode } from "primevue/api";
import useUtilFunctions from "@/composables/useUtilFunctions";
import useErrorsSummary from "@/composables/useErrorsSummary";
import { computed, defineEmits, defineExpose, defineProps, inject, nextTick, onMounted, ref, watch } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";

const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");

const metadataDialog = ref();
const { data: errorsMessages, fetchAllMessage: getAllErrorsMessage } = useErrorsSummary();
const { pluralize } = useUtilFunctions();
const { fetchErrorCount } = useErrorCount();
const emit = defineEmits(["refreshErrors"]);
const notify = useNotifications();
const loading = ref(true);
const menu = ref();
const errors = ref([]);
const expandedRows = ref([]);
const totalErrors = ref(0);
const offset = ref(0);
const perPage = ref();
const sortField = ref("modified");
const sortDirection = ref("DESC");
const selectedErrors = ref([]);
const ackErrorsDialog = ref({
  dids: [],
  visible: false,
});
const props = defineProps({
  ingressFlowName: {
    type: Object,
    required: false,
    default: undefined,
  },
  awknowledged: {
    type: Boolean,
    required: true,
  },
  errorsMessageSelected: {
    type: Object,
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
      selectedErrors.value = [];
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
    visible: computed(() => hasSomePermissions("DeltaFileAcknowledge", "DeltaFileResume")),
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
    label: "Resume Selected",
    icon: "fas fa-redo fa-fw",
    command: () => {
      metadataDialog.value.showConfirmDialog("Resume");
    },
    visible: computed(() => hasPermission("DeltaFileResume")),
    disabled: computed(() => selectedErrors.value.length == 0),
  },
]);

const onRefresh = () => {
  selectedErrors.value = [];
  fetchErrors();
};

const { data: response, fetch: getErrors } = useErrors();
const filters = ref({
  last_error_cause: { value: null, matchMode: FilterMatchMode.EQUALS },
});

const fetchErrors = async () => {
  getPersistedParams();
  let ingressFlowName = props.ingressFlowName != null ? props.ingressFlowName.name : null;
  let errorMessage = filters.value.last_error_cause.value != null ? filters.value.last_error_cause.value.message : null;
  let showAcknowled = props.awknowledged ? null : false;
  loading.value = true;
  await getErrors(showAcknowled, offset.value, perPage.value, sortField.value, sortDirection.value, ingressFlowName, errorMessage);
  errors.value = response.value.deltaFiles.deltaFiles;
  totalErrors.value = response.value.deltaFiles.totalCount;
  loading.value = false;
};

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

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

const onAcknowledged = (dids, reason) => {
  selectedErrors.value = [];
  ackErrorsDialog.value.dids = [];
  ackErrorsDialog.value.visible = false;
  let pluralized = pluralize(dids.length, "Error");
  notify.success(`Successfully acknowledged ${pluralized}`, reason);
  fetchErrorCount();
  emit("refreshErrors");
};

const filterSelectedDids = computed(() => {
  let dids = selectedErrors.value.map((selectedError) => {
    return selectedError.did;
  });
  return dids;
});

const filterErrors = (actions) => {
  return actions.filter((action) => {
    return action.state === "ERROR";
  });
};

const latestError = (actions) => {
  return filterErrors(actions).sort((a, b) => (a.modified < b.modified ? 1 : -1))[0];
};

const actionRowClass = (action) => {
  if (action.state === "ERROR") return "table-danger action-error";
  if (action.state === "RETRIED") return "table-warning action-error";
};

const actionRowClick = (event) => {
  let action = event.data;
  if (["ERROR", "RETRIED"].includes(action.state)) {
    errorViewer.value.action = action;
    errorViewer.value.visible = true;
  }
};

const onPage = async (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
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
});
watch(
  () => props.ingressFlowName,
  () => {
    fetchErrors();
  }
);

watch(
  () => props.errorsMessageSelected,
  () => {
    filters.value.last_error_cause.value = props.errorsMessageSelected;
    fetchErrors();
  }
);

watch(
  () => props.awknowledged,
  () => {
    selectedErrors.value = [];
    fetchErrors();
  }
);

watch(
  () => filters.value.last_error_cause.value,
  () => {
    fetchErrors();
  }
);

onMounted(() => {
  getPersistedParams();
  fetchErrors();
  getAllErrorsMessage();
});

const getPersistedParams = async () => {
  let state = useStorage("errors-page-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  perPage.value = state.value.perPage || 10;
};

const setPersistedParams = () => {
  let state = useStorage("errors-page-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  state.value = {
    perPage: perPage.value,
  };
};
</script>

<style lang="scss">
.p-column-filter-overlay {
  margin-left: -18px;
  max-width: 300px;
}
</style>
