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
  <Panel header="DeltaFiles by Message" @contextmenu="onPanelRightClick">
    <ContextMenu ref="menu" :model="menuItems" />
    <template #icons>
      <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
        <span class="fas fa-bars" />
      </Button>
      <Menu ref="menu" :model="menuItems" :popup="true" />
      <Paginator v-if="errorsMessage.length > 0" :rows="perPage" :first="getPage" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalErrorsMessage" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage($event)" />
    </template>
    <DataTable id="errorsSummaryTable" v-model:expanded-rows="expandedErrorGroups" v-model:selection="selectedErrorGroups" data-key="message" responsive-layout="scroll" selection-mode="multiple" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="groupErrorMessageData" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalErrorsMessage" :row-hover="true" @row-contextmenu="onGroupContextMenu" @sort="onSort($event)" @row-select="onGroupRowSelect" @row-unselect="onGroupRowUnSelect">
      <template #empty> No results to display. </template>
      <template #loading> Loading. Please wait... </template>
      <Column v-if="groupErrorMessageData.length > 0" expander class="expander-column">
        <template #body="{ data }">
          <div @click.stop="onExpandClicked(data)">
            <ChevronSwitch :expanded-rows="expandedErrorGroups" :row="data" class="text-muted" />
          </div>
        </template>
      </Column>
      <Column field="message" header="Message" sortable>
        <template #body="{ data }">
          <a class="monospace" @click="showErrors(data.message, null, null)">{{ data.message }}</a>
        </template>
      </Column>
      <Column field="count" header="Count" sortable />
      <template #expansion="slotProps">
        <div class="errors-Subtable">
          <DataTable :value="slotProps.data.flows" v-model:selection="selectedErrors" data-key="flow" selection-mode="multiple" class="p-datatable-sm p-datatable-gridlines" @row-select="onRowSelect" @row-unselect="onRowUnSelect" @row-contextmenu="onRowContextMenu">
            <Column field="flow" header="Flow" sortable class="filename-column" />
            <Column field="type" header="Flow Type" sortable />
            <Column field="count" header="Count" sortable />
            <Column field="message" header="Message" sortable>
              <template #body="{ data }">
                <a class="monospace" @click="showErrors(data.message, data.flow, data.type)">{{ data.message }}</a>
              </template>
            </Column>
          </DataTable>
        </div>
      </template>
    </DataTable>
  </Panel>
  <ResumeBulkActionDialog ref="resumeBulkActionDialog" :flow-info="filterSelectedDidsBulkAction" bundle-request-type="resumeByErrorCause" :acknowledged="props.acknowledged" @refresh-page="onRefresh()" />
  <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="ackErrorsDialog.dids" @acknowledged="onAcknowledged" />
  <AnnotateDialog ref="annotateDialog" :dids="filterSelectedDids" @refresh-page="onRefresh()" />
  <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="Add New Auto Resume Rule" required-permission="ResumePolicyCreate" dialog-width="75vw" :row-data-prop="autoResumeSelected">
    <span id="summaryMessageAutoResumeDialog" />
  </DialogTemplate>
</template>

<script setup>
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import AnnotateDialog from "@/components/AnnotateDialog.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import ResumeBulkActionDialog from "@/components/errors/ResumeBulkActionDialog.vue";
import ChevronSwitch from "@/components/errors/ChevronSwitch.vue";
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
import Paginator from "primevue/paginator";
import Panel from "primevue/panel";
import Menu from "primevue/menu";

const hasPermission = inject("hasPermission");
const hasSomePermissions = inject("hasSomePermissions");

const loading = ref(true);
const menu = ref();
const errorsMessage = ref([]);
const totalErrorsMessage = ref(0);
const offset = ref(0);
const perPage = ref();
const page = ref(null);
const resumeBulkActionDialog = ref();
const sortField = ref("NAME");
const sortDirection = ref("ASC");
const expandedErrorGroups = ref({});
const selectedErrorGroups = ref([]);
const selectedErrors = ref([]);
const emit = defineEmits(["refreshErrors", "changeTab:showErrors"]);
const notify = useNotifications();
const annotateDialog = ref();
const { pluralize } = useUtilFunctions();
const { fetchErrorCount } = useErrorCount();

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
  queryParams: {
    type: Object,
    required: true,
  },
});

const unSelectAllRows = async () => {
  selectedErrorGroups.value = [];
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
      selectedErrorGroups.value = groupErrorMessageData.value;
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
      resumeBulkActionDialog.value.showConfirmDialog();
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
  await fetchErrorsMessages();
  setupWatchers();
});

const { data: response, fetchErrorSummaryByMessage } = useErrorsSummary();

const onRefresh = () => {
  unSelectAllRows();
  fetchErrorsMessages();
};

const showErrors = (errorMessage, flowName, flowType) => {
  emit("changeTab:showErrors", errorMessage, flowName, flowType);
};

const filterSelectedDids = computed(() => {
  const dids = selectedErrors.value.map((selectedError) => {
    return selectedError.dids;
  });

  return _.flatten([...new Set(dids)]);
});

const filterSelectedDidsBulkAction = computed(() => {
  const flowInfo = selectedErrors.value.map((selectedError) => {
    const foundObject = _.find(selectedErrorGroups.value, { message: selectedError.message });
    const messageGrouping = foundObject ? "ALL" : "SINGLE";
    return { messageGrouping: messageGrouping, flowType: selectedError.type, flowName: selectedError.flow, dids: selectedError.dids, message: selectedError.message };
  });

  return flowInfo;
});

const fetchErrorsMessages = async () => {
  getPersistedParams();
  const flowName = props.flow?.name != null ? props.flow?.name : null;
  loading.value = true;
  await fetchErrorSummaryByMessage(props.queryParams, props.acknowledged, offset.value, perPage.value, sortField.value, sortDirection.value, flowName);
  errorsMessage.value = response.value.countPerMessage;
  totalErrorsMessage.value = response.value.totalCount;
  loading.value = false;
};

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

const onGroupContextMenu = (event) => {
  const newSelectedGroupErrors = [];
  const tmpSelectedErrorGroups = JSON.parse(JSON.stringify(selectedErrorGroups.value));
  const foundGroupErrorMessages = _.find(tmpSelectedErrorGroups, { message: event.data.message });

  if (!foundGroupErrorMessages) {
    newSelectedGroupErrors.push(event.data);
  }
  if (newSelectedGroupErrors.length > 0) {
    selectedErrorGroups.value = [...selectedErrorGroups.value, ...newSelectedGroupErrors];
  }

  onGroupRowSelect(event);
};

const onRowContextMenu = (event) => {
  const newSelectedErrors = [];
  const tmpSelectedErrorArray = JSON.parse(JSON.stringify(selectedErrors.value));
  const foundObject = _.find(tmpSelectedErrorArray, event.data);

  if (!foundObject) {
    newSelectedErrors.push(event.data);
  }
  if (newSelectedErrors.length > 0) {
    selectedErrors.value = [...selectedErrors.value, ...newSelectedErrors];
  }

  onRowSelect(event);
};

const onPanelRightClick = (event) => {
  menu.value.show(event);
};

defineExpose({
  fetchErrorsMessages,
  unSelectAllRows,
});

const onSort = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  sortField.value = event.sortField === "flow" ? "NAME" : event.sortField.toUpperCase();
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchErrorsMessages();
};

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
    newResumeRule["errorSubstring"] = rowInfo.message;
    return newResumeRule;
  } else {
    return selectedErrors.value;
  }
});

const groupErrorMessageData = computed(() => {
  const grouped = {};

  for (const error of errorsMessage.value) {
    const { message } = error;

    if (!grouped[message]) {
      grouped[message] = {
        count: 0,
        message,
        flows: [],
      };
    }

    grouped[message].count += error.count;
    grouped[message].flows.push(error);
  }

  return Object.values(grouped);
});

const setupWatchers = () => {
  watch(
    () => props.flow,
    () => {
      unSelectAllRows();
      fetchErrorsMessages();
    }
  );

  watch(
    () => props.acknowledged,
    () => {
      unSelectAllRows();
      fetchErrorsMessages();
    }
  );
};

const onPage = async (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  page.value = event.page + 1;
  setPersistedParams();
  await nextTick();
  fetchErrorsMessages();
  emit("refreshErrors");
};

const getPage = computed(() => {
  return page.value === null || page.value === undefined ? 0 : (page.value - 1) * perPage.value;
});

const getPersistedParams = async () => {
  const state = useStorage("errors-page-by-message-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  perPage.value = state.value.perPage || 20;
  page.value = state.value.page || 1;
  offset.value = getPage.value;
};

const setPersistedParams = () => {
  const state = useStorage("errors-page-by-message-session-storage", {}, sessionStorage, { serializer: StorageSerializers.object });
  state.value = {
    perPage: perPage.value,
    page: page.value,
  };
};

// When a group error row is selected, add all its errors to the list of selected errors.
const onGroupRowSelect = (event) => {
  const newSelectedErrors = [];
  for (const flow of event.data.flows) {
    const tmpSelectedErrorArray = JSON.parse(JSON.stringify(selectedErrors.value));
    const foundObject = _.find(tmpSelectedErrorArray, flow);

    if (!foundObject) {
      newSelectedErrors.push(flow);
    }
  }
  if (newSelectedErrors.length > 0) {
    selectedErrors.value = [...selectedErrors.value, ...newSelectedErrors];
  }
};

// When a group error row is unselected, remove all its errors from the list of selected errors.
const onGroupRowUnSelect = (event) => {
  for (const flow of event.data.flows) {
    let tmpSelectedError = JSON.parse(JSON.stringify(selectedErrors.value));
    tmpSelectedError = _.filter(tmpSelectedError, function (o) {
      return !_.isEqual(JSON.stringify(o), JSON.stringify(flow));
    });
    selectedErrors.value = tmpSelectedError;
  }
};

// When a row is selected and all other rows of that group are already selected, add its group to the list of selected groups.
const onRowSelect = (event) => {
  const filteredSelectedErrors = _.filter(selectedErrors.value, { message: event.data.message });
  const foundGroupErrorMessages = _.find(groupErrorMessageData.value, { message: event.data.message });

  const tmpSelectedErrorGroups = JSON.parse(JSON.stringify(selectedErrorGroups.value));
  const foundObject = _.find(selectedErrorGroups.value, { message: event.data.message });
  const countOfFilteredSelectedErrors = _.sumBy(filteredSelectedErrors, function (o) {
    return o.count;
  });
  if (_.isEqual(foundGroupErrorMessages.count, countOfFilteredSelectedErrors)) {
    if (_.isEmpty(foundObject)) {
      tmpSelectedErrorGroups.push(foundGroupErrorMessages);
      selectedErrorGroups.value = tmpSelectedErrorGroups;
    }
  }
};

// When a row is unselected remove the group from the list of selected groups.
const onRowUnSelect = (event) => {
  const filteredSelectedErrors = _.filter(selectedErrors.value, { message: event.data.message });
  const foundGroupErrorMessages = _.find(groupErrorMessageData.value, { message: event.data.message });

  let tmpSelectedErrorGroups = JSON.parse(JSON.stringify(selectedErrorGroups.value));
  const foundObject = _.find(selectedErrorGroups.value, { message: event.data.message });
  const countOfFilteredSelectedErrors = _.sumBy(filteredSelectedErrors, function (o) {
    return o.count;
  });
  if (!_.isEqual(foundGroupErrorMessages.count, countOfFilteredSelectedErrors)) {
    if (!_.isEmpty(foundObject)) {
      tmpSelectedErrorGroups = _.filter(tmpSelectedErrorGroups, function (o) {
        return !_.isEqual(JSON.stringify(o), JSON.stringify(foundGroupErrorMessages));
      });
      selectedErrorGroups.value = tmpSelectedErrorGroups;
    }
  }
};

const isExpanded = (data) => {
  return _.has(expandedErrorGroups.value, data.message);
};

// Function to handle expander click
const onExpandClicked = (data) => {
  const tmpExpandedErrorGroups = JSON.parse(JSON.stringify(expandedErrorGroups.value));
  if (isExpanded(data)) {
    delete tmpExpandedErrorGroups[data.message.toString()];
    expandedErrorGroups.value = tmpExpandedErrorGroups;
  } else {
    tmpExpandedErrorGroups[data.message.toString()] = true;
    expandedErrorGroups.value = tmpExpandedErrorGroups;
  }
};
</script>

<style />
