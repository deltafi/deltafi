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
  <Panel header="DeltaFiles with Errors by Message" @contextmenu="onPanelRightClick">
    <ContextMenu ref="menu" :model="menuItems" />
    <template #icons>
      <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
        <span class="fas fa-bars" />
      </Button>
      <Menu ref="menu" :model="menuItems" :popup="true" />
      <Paginator v-if="errorsMessage.length > 0" :rows="perPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalErrorsMessage" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage($event)"></Paginator>
    </template>
    <DataTable id="errorsSummaryTable" v-model:selection="selectedErrors" responsive-layout="scroll" selection-mode="multiple" data-key="dids" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="errorsMessage" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalErrorsMessage" :row-hover="true" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
      <template #empty>No results to display.</template>
      <template #loading>Loading. Please wait...</template>
      <Column field="flow" header="Flow" :sortable="true" class="filename-column"> </Column>
      <Column field="count" header="Count" :sortable="true" />
      <Column field="message" header="Message" :sortable="true">
        <template #body="msg">
          <a class="monospace" @click="showAll(msg.data.message, msg.data.flow)">{{ msg.data.message }}</a>
        </template>
      </Column>
    </DataTable>
  </Panel>
  <MetadataDialogResume ref="metadataDialogResume" :did="filterSelectedDids" />
  <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="ackErrorsDialog.dids" @acknowledged="onAcknowledged" />
  <AnnotateDialog ref="annotateDialog" :dids="filterSelectedDids" @refresh-page="onRefresh()" />
  <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="Add New Auto Resume Rule" required-permission="ResumePolicyCreate" dialog-width="75vw" :row-data-prop="autoResumeSelected">
    <span id="summaryMessageAutoResumeDialog" />
  </DialogTemplate>
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
import MetadataDialogResume from "@/components/errors/MetadataDialogResume.vue";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import { computed, defineEmits, defineExpose, defineProps, inject, nextTick, onMounted, ref, watch } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";
import AnnotateDialog from "@/components/AnnotateDialog.vue";
import _ from "lodash";

const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");

const loading = ref(true);
const menu = ref();
const errorsMessage = ref([]);
const totalErrorsMessage = ref(0);
const offset = ref(0);
const perPage = ref();
const sortField = ref("modified");
const metadataDialogResume = ref();
const sortDirection = ref("DESC");
const selectedErrors = ref([]);
const emit = defineEmits(["refreshErrors", "changeTab:errorMessage:flowSelected"]);
const notify = useNotifications();
const annotateDialog = ref();
const { pluralize } = useUtilFunctions();
const { fetchErrorCount } = useErrorCount();
const ackErrorsDialog = ref({
  dids: [],
  visible: false,
});
const props = defineProps({
  ingressFlowName: {
    type: String,
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
      selectedErrors.value = errorsMessage.value;
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
      metadataDialogResume.value.showConfirmDialog();
    },
    visible: computed(() => hasPermission("DeltaFileResume")),
    disabled: computed(() => selectedErrors.value.length == 0),
  },
  {
    label: "Create Auto Resume Rule",
    icon: "fas fa-clock-rotate-left fa-flip-horizontal fa-fw",
    command: () => {
      document.getElementById("summaryMessageAutoResumeDialog").click();
    },
    visible: computed(() => hasPermission("ResumePolicyCreate")),
    disabled: computed(() => selectedErrors.value.length == 0 || selectedErrors.value.length > 1),
  },
]);

onMounted(async () => {
  await getPersistedParams();
  fetchErrorsMessages();
  setupWatchers();
});

const { data: response, fetchByMessage: getErrorsByMessage } = useErrorsSummary();

const onRefresh = () => {
  selectedErrors.value = [];
  fetchErrorsMessages();
};

const showAll = (errorMessage, flowSel) => {
  emit("changeTab:errorMessage:flowSelected", errorMessage, flowSel);
};

const filterSelectedDids = computed(() => {
  let dids = selectedErrors.value.map((selectedError) => {
    return selectedError.dids;
  });
  let allDids = [].concat.apply([], dids);

  return [...new Set(allDids)];
});

const fetchErrorsMessages = async () => {
  getPersistedParams();
  let ingressFlowName = props.ingressFlowName != null ? props.ingressFlowName : null;
  let showAcknowled = props.awknowledged ? null : false;
  loading.value = true;
  await getErrorsByMessage(showAcknowled, offset.value, perPage.value, sortField.value, sortDirection.value, ingressFlowName);
  errorsMessage.value = response.value.countPerMessage;
  totalErrorsMessage.value = response.value.totalCount;
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

defineExpose({
  fetchErrorsMessages,
});

const onSort = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  sortField.value = event.sortField;
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchErrorsMessages();
};

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

const autoResumeSelected = computed(() => {
  let newResumeRule = {};
  if (!_.isEmpty(selectedErrors.value)) {
    let rowInfo = JSON.parse(JSON.stringify(selectedErrors.value[0]));
    newResumeRule["flow"] = rowInfo.flow;
    newResumeRule["errorSubstring"] = rowInfo.message;
    return newResumeRule;
  } else {
    return selectedErrors.value;
  }
});
const setupWatchers = () => {
  watch(
    () => props.ingressFlowName,
    () => {
      fetchErrorsMessages();
    }
  );

  watch(
    () => props.awknowledged,
    () => {
      selectedErrors.value = [];
      fetchErrorsMessages();
    }
  );
};
const onPage = async (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  setPersistedParams();
  await nextTick();
  fetchErrorsMessages();
  emit("refreshErrors");
};

const getPersistedParams = async () => {
  let state = useStorage("errors-page-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  perPage.value = state.value.perPage || 20;
};

const setPersistedParams = () => {
  let state = useStorage("errors-page-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  state.value = {
    perPage: perPage.value,
  };
};
</script>

<style lang="scss"></style>
