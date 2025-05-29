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
      <Paginator v-if="errorsFlow.length > 0" :rows="perPage" :first="getPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalErrorsFlow" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage($event)" />
    </template>
    <DataTable id="errorsSummaryTable" v-model:selection="selectedErrors" responsive-layout="scroll" selection-mode="multiple" data-key="flow" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="errorsFlow" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalErrorsFlow" :row-hover="true" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
      <template #empty> No results to display. </template>
      <template #loading> Loading. Please wait... </template>
      <Column field="flow" header="Flow" sortable class="filename-column">
        <template #body="{ data }">
          <a class="monospace" @click="showErrors(null, data.flow, data.type)">{{ data.flow }}</a>
        </template>
      </Column>
      <Column field="type" header="Flow Type" sortable />
      <Column field="count" header="Count" sortable />
    </DataTable>
  </Panel>
  <ResumeDialog ref="resumeDialog" :did="filterSelectedDids" @refresh-page="onRefresh" />
  <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="ackErrorsDialog.dids" @acknowledged="onAcknowledged" />
  <AnnotateDialog ref="annotateDialog" :dids="filterSelectedDids" @refresh-page="onRefresh()" />
  <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="Add New Auto Resume Rule" required-permission="ResumePolicyCreate" dialog-width="75vw" :row-data-prop="autoResumeSelected">
    <span id="summaryFlowAutoResumeDialog" />
  </DialogTemplate>
</template>

<script setup>
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import AnnotateDialog from "@/components/AnnotateDialog.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import ResumeDialog from "@/components/errors/ResumeDialog.vue";
import useErrorsSummary from "@/composables/useErrorsSummary";
import useErrorCount from "@/composables/useErrorCount";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, inject, nextTick, onMounted, ref, watch } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";

import _ from "lodash";

import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Button from "primevue/button";
import Panel from "primevue/panel";
import Menu from "primevue/menu";
import ContextMenu from "primevue/contextmenu";
import Paginator from "primevue/paginator";

const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");

const loading = ref(true);
const menu = ref();
const errorsFlow = ref([]);
const totalErrorsFlow = ref(0);
const offset = ref(0);
const perPage = ref();
const page = ref(null);
const resumeDialog = ref();
const sortField = ref("NAME");
const sortDirection = ref("ASC");
const selectedErrors = ref([]);
const notify = useNotifications();
const emit = defineEmits(["refreshErrors", "changeTab:showErrors"]);
const { pluralize } = useUtilFunctions();
const { fetchErrorCount } = useErrorCount();
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
});

const unSelectAllRows = async () => {
  selectedErrors.value = [];
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
      selectedErrors.value = errorsFlow.value;
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
      document.getElementById("summaryFlowAutoResumeDialog").click();
    },
    visible: computed(() => hasPermission("ResumePolicyCreate")),
    disabled: computed(() => selectedErrors.value.length == 0 || selectedErrors.value.length > 1),
  },
]);

const showErrors = (errorMessage, flowName, flowType) => {
  emit("changeTab:showErrors", errorMessage, flowName, flowType);
};

onMounted(async () => {
  await getPersistedParams();
  fetchErrorsFlow();
  setupWatchers();
});

const onRefresh = () => {
  unSelectAllRows();
  fetchErrorsFlow();
};

const { data: response, fetchErrorSummaryByFlow } = useErrorsSummary();

const fetchErrorsFlow = async () => {
  getPersistedParams();
  const flowName = props.flow?.name != null ? props.flow?.name : null;
  loading.value = true;
  await fetchErrorSummaryByFlow(props.acknowledged, offset.value, perPage.value, sortField.value, sortDirection.value, flowName);
  errorsFlow.value = response.value.countPerFlow;
  totalErrorsFlow.value = response.value.totalCount;
  loading.value = false;
};

const filterSelectedDids = computed(() => {
  const dids = selectedErrors.value.map((selectedError) => {
    return selectedError.dids;
  });
  return _.flatten([...new Set(dids)]);
});

const acknowledgeClickConfirm = () => {
  ackErrorsDialog.value.dids = JSON.parse(JSON.stringify(filterSelectedDids.value));
  ackErrorsDialog.value.visible = true;
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
    newResumeRule["Flow"] = rowInfo.flow;
    return newResumeRule;
  } else {
    return selectedErrors.value;
  }
});

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

defineExpose({
  fetchErrorsFlow,
  unSelectAllRows,
});

const onSort = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  sortField.value = event.sortField === "flow" ? "NAME" : event.sortField.toUpperCase();
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchErrorsFlow();
};

const onPage = async (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  page.value = event.page + 1;
  await nextTick();
  setPersistedParams();
  fetchErrorsFlow();
  emit("refreshErrors");
};

const getPage = computed(() => {
  return page.value === null || page.value === undefined ? 0 : (page.value - 1) * perPage.value;
});

const setupWatchers = () => {
  watch(
    () => props.flow,
    () => {
      unSelectAllRows();
      fetchErrorsFlow();
    }
  );

  watch(
    () => props.acknowledged,
    () => {
      unSelectAllRows();
      fetchErrorsFlow();
    }
  );
};
const getPersistedParams = async () => {
  const state = useStorage("errors-page-by-flow-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  perPage.value = state.value.perPage || 20;
  page.value = state.value.page || 1;
  offset.value = getPage.value;
};

const setPersistedParams = () => {
  const state = useStorage("errors-page-by-flow-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  state.value = {
    perPage: perPage.value,
    page: page.value,
  };
};
</script>

<style />
