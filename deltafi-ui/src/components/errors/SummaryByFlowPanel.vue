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
  <Panel header="DeltaFiles with Errors by Flow" @contextmenu="onPanelRightClick">
    <ContextMenu ref="menu" :model="menuItems" />
    <template #icons>
      <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
        <span class="fas fa-bars" />
      </Button>
      <Menu ref="menu" :model="menuItems" :popup="true" />
      <Paginator v-if="errorsFlow.length > 0" :rows="perPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalErrorsFlow" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage($event)"></Paginator>
    </template>
    <DataTable id="errorsSummaryTable" v-model:selection="selectedErrors" responsive-layout="scroll" selection-mode="multiple" data-key="flow" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="errorsFlow" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalErrorsFlow" :row-hover="true" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
      <template #empty>No results to display.</template>
      <template #loading>Loading. Please wait...</template>
      <Column field="flow" header="Flow" :sortable="true" class="filename-column" />
      <Column field="count" header="Count" :sortable="true" />
    </DataTable>
  </Panel>
  <MetadataDialog ref="metadataDialog" :did="filterSelectedDids" @update="onRefresh()" />
  <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="ackErrorsDialog.dids" @acknowledged="onAcknowledged" />
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Button from "primevue/button";
import Panel from "primevue/panel";
import Menu from "primevue/menu";
import ContextMenu from "primevue/contextmenu";
import Paginator from "primevue/paginator";
import useErrorsSummary from "@/composables/useErrorsSummary";
import useErrorCount from "@/composables/useErrorCount";
import MetadataDialog from "@/components/MetadataDialog.vue";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import { computed, defineEmits, defineExpose, defineProps, inject, nextTick, onMounted, ref, watch } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";

const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");

const loading = ref(true);
const menu = ref();
const errorsFlow = ref([]);
const totalErrorsFlow = ref(0);
const offset = ref(0);
const perPage = ref();
const sortField = ref("modified");
const metadataDialog = ref();
const sortDirection = ref("DESC");
const selectedErrors = ref([]);
const notify = useNotifications();
const emit = defineEmits(["refreshErrors"]);
const { pluralize } = useUtilFunctions();
const { fetchErrorCount } = useErrorCount();
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
    label: "Resume Selected",
    icon: "fas fa-redo fa-fw",
    command: () => {
      metadataDialog.value.showConfirmDialog("Resume");
    },
    visible: computed(() => hasPermission("DeltaFileResume")),
    disabled: computed(() => selectedErrors.value.length == 0),
  },
]);

onMounted(() => {
  getPersistedParams();
  fetchErrorsFlow();
});

const onRefresh = () => {
  selectedErrors.value = [];
  fetchErrorsFlow();
};

const { data: response, fetchByFlow: getErrorsByFlow } = useErrorsSummary();

const fetchErrorsFlow = async () => {
  getPersistedParams();
  let ingressFlowName = props.ingressFlowName != null ? props.ingressFlowName : null;
  let showAcknowled = props.awknowledged ? null : false;
  loading.value = true;
  await getErrorsByFlow(showAcknowled, offset.value, perPage.value, sortField.value, sortDirection.value, ingressFlowName);
  errorsFlow.value = response.value.countPerFlow;
  totalErrorsFlow.value = response.value.totalCount;
  loading.value = false;
};

const filterSelectedDids = computed(() => {
  let dids = selectedErrors.value.map((selectedError) => {
    return selectedError.dids;
  });
  let allDids = [].concat.apply([], dids);

  return [...new Set(allDids)];
});

const acknowledgeClickConfirm = () => {
  ackErrorsDialog.value.dids = JSON.parse(JSON.stringify(filterSelectedDids.value));
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
});

const onSort = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  sortField.value = event.sortField;
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchErrorsFlow();
};

const onPage = async (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  await nextTick();
  setPersistedParams();
  fetchErrorsFlow();
  emit("refreshErrors");
};

watch(
  () => props.ingressFlowName,
  () => {
    fetchErrorsFlow();
  }
);

watch(
  () => props.awknowledged,
  () => {
    selectedErrors.value = [];
    fetchErrorsFlow();
  }
);

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

<style lang="scss"></style>
