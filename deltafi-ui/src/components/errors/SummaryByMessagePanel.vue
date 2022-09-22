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
  <Panel header="DeltaFiles with Errors by Message" @contextmenu="onPanelRightClick">
    <ContextMenu ref="menu" :model="menuItems" />
    <template #icons>
      <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
        <span class="fas fa-bars" />
      </Button>
      <Menu ref="menu" :model="menuItems" :popup="true" />
      <Paginator v-if="errorsMessage.length > 0" :rows="10" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalErrorsMessage" :rows-per-page-options="[10, 20, 50, 100, 1000]" class="p-panel-header" style="float: left" @page="onPage($event)"></Paginator>
    </template>
    <DataTable id="errorsSummaryTable" v-model:selection="selectedErrors" responsive-layout="scroll" selection-mode="multiple" data-key="message" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="errorsMessage" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalErrorsMessage" :row-hover="true" @row-contextmenu="onRowContextMenu" @sort="onSort($event)">
      <template #empty>No results to display.</template>
      <template #loading>Loading. Please wait...</template>
      <Column field="flow" header="Flow" :sortable="true" class="filename-column"> </Column>
      <Column field="count" header="Count" :sortable="true" />
      <Column field="message" header="Message" :sortable="true">
        <template #body="msg">
          <a class="monospace" @click="showAll(msg.data.message)">{{ msg.data.message }}</a>
        </template>
      </Column>
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
import { ref, onMounted, defineExpose, defineProps, watch, computed, defineEmits } from "vue";

const loading = ref(true);
const menu = ref();
const errorsMessage = ref([]);
const totalErrorsMessage = ref(0);
const offset = ref(0);
const perPage = ref(10);
const sortField = ref("modified");
const metadataDialog = ref();
const sortDirection = ref("DESC");
const selectedErrors = ref([]);
const emit = defineEmits(["refreshErrors", "changeTab:errorMessage"]);
const notify = useNotifications();
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
      selectedErrors.value = errorsMessage.value;
    },
  },
  {
    separator: true,
  },
  {
    label: "Acknowledge Selected",
    icon: "fas fa-check-circle fa-fw",
    command: () => {
      acknowledgeClickConfirm();
    },
    disabled: () => {
      return selectedErrors.value.length == 0;
    },
  },
  {
    label: "Resume Selected",
    icon: "fas fa-redo fa-fw",
    command: () => {
      metadataDialog.value.showConfirmDialog("Resume");
    },
    disabled: () => {
      return selectedErrors.value.length == 0;
    },
  },
]);

onMounted(() => {
  fetchErrorsMessages();
});

const { data: response, fetchByMessage: getErrorsByMessage } = useErrorsSummary();

const onRefresh = () => {
  selectedErrors.value = [];
  fetchErrorsMessages();
};

const showAll = (errorMessage) => {
  emit("changeTab:errorMessage", errorMessage);
};

const filterSelectedDids = computed(() => {
  let dids = selectedErrors.value.map((selectedError) => {
    return selectedError.dids;
  });
  let allDids = [].concat.apply([], dids);

  return [...new Set(allDids)];
});

const fetchErrorsMessages = async () => {
  let ingressFlowName = props.ingressFlowName != null ? props.ingressFlowName.name : null;
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

const onPage = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  fetchErrorsMessages();
};
</script>

<style lang="scss"></style>