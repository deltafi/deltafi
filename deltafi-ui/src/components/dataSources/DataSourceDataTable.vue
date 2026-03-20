<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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

<!-- ABOUTME: Unified data source table component with plugin-based grouping. -->
<!-- ABOUTME: Displays all data source types (REST, Timed, OnError) in a single table per plugin. -->

<template>
  <ContextMenu ref="menu" :model="menuItems" />
  <div v-for="(pluginFlows, pluginName) in filteredFlowDataByPlugin" :key="pluginName">
    <CollapsiblePanel :header="pluginName" class="table-panel mb-3" @contextmenu="onPanelRightClick($event, pluginName)">
      <template #header>
        <span class="p-panel-title d-flex align-items-center">
          {{ pluginName === 'system-plugin' ? 'Local System Plugin' : pluginName }}
          <i v-if="pluginName === 'system-plugin'" v-tooltip.top="'User-created flows, not from installed plugins'" class="text-muted pi pi-info-circle ml-2" />
          <PermissionedRouterLink :disabled="!$hasPermission('PluginsView')" :to="{ path: 'plugins/' + pluginFlows[0].mvnCoordinates }" class="ml-2">
            <i v-tooltip.top="pluginFlows[0].mvnCoordinates" class="text-muted fas fa-plug fa-rotate-90 fa-fw" />
          </PermissionedRouterLink>
          <Button v-tooltip.top="'Plugin actions'" severity="secondary" text size="small" icon="pi pi-ellipsis-v" aria-label="Plugin actions menu" class="p-0 ml-1" @click.stop="onPanelRightClick($event, pluginName)" />
        </span>
      </template>
      <DataTable
        v-model:filters="filters"
        aria-label="Data sources"
        :edit-mode="$hasPermission('FlowUpdate') ? 'cell' : null"
        :value="pluginFlows"

        striped-rows
        class="p-datatable-sm p-datatable-gridlines data-sources-table"
        :row-class="actionRowClass"
        :global-filter-fields="['searchField']"
        sort-field="name"
        :sort-order="1"
        :row-hover="true"
        data-key="name"
        @cell-edit-init="onEditInit"
        @cell-edit-complete="onEditComplete"
        @cell-edit-cancel="onEditCancel"
      >
        <template #empty> No data sources found. </template>
        <Column header="Name" field="name" class="name-column" :sortable="true">
          <template #body="{ data }">
            <div class="d-flex justify-content-between">
              <span>
                <i v-tooltip.top="dataSourceTypeLabel(data.dataSourceType)" :class="dataSourceTypeIcon(data.dataSourceType)" class="mr-2 text-muted" />
                <span class="cursor-pointer" @click="showAction(data)">{{ data.name }}</span>
              </span>
              <span>
                <DataSourceNameColumnButtonGroup :key="data.name" :row-data-prop="data" @reload-data-sources="refresh" @update-rate-limit="updateRateLimit" />
              </span>
            </div>
          </template>
        </Column>
        <Column header="Description" field="description" :sortable="true">
          <template #body="{ data, field }">
            <div v-if="_.size(data[field]) > maxDescriptionLength" v-tooltip.bottom="data[field]">
              {{ displayDescription(data[field]) }}
            </div>
            <span v-else>{{ data[field] }}</span>
          </template>
        </Column>
        <Column header="Publish" field="topic" :sortable="true">
          <template #body="{ data }">
            {{ data.topic }}
            <TopicSubscribers :topic-name="data.topic" />
          </template>
        </Column>
        <Column header="Max Errors" field="maxErrors" class="max-error-column">
          <template #body="{ data, field }">
            <span v-if="data[field] === null">-</span>
            <span v-else>{{ data[field] }}</span>
          </template>
          <template #editor="{ data, field }">
            <InputNumber v-has-permission:FlowUpdate v-model="data[field]" :min="0" class="p-inputtext-sm max-error-input" autofocus aria-label="Max errors" />
          </template>
        </Column>
        <Column header="Test Mode" class="switch-column">
          <template #body="{ data }">
            <DataSourceTestModeInputSwitch :row-data-prop="data" />
          </template>
        </Column>
        <Column header="Status" class="status-column">
          <template #body="{ data }">
            <div class="d-flex justify-content-center">
              <!-- Prioritized display: if stopped/paused show flow status, if running show ingress status for timed -->
              <StatusBadge v-if="data.dataSourceType === 'TIMED_DATA_SOURCE' && data.flowStatus.state === 'RUNNING'" :status="data.ingressStatus" :message="data.ingressStatusMessage" />
              <FlowStatusBadge v-else :row-data="data" />
            </div>
          </template>
        </Column>
        <Column header="State" :style="{ width: '7%' }" class="switch-column">
          <template #body="{ data }">
            <StateInputSwitch :row-data-prop="data" :data-source-type="data.dataSourceType" @change="refresh" @confirm-start="confirming = true" @confirm-stop="confirming = false" />
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
  </div>
  <ConfirmDialog group="bulkActions">
    <template #message="slotProps">
      <span class="p-confirm-dialog-icon pi pi-exclamation-triangle" />
      <span class="p-confirm-dialog-message" v-html="slotProps.message.message" />
    </template>
  </ConfirmDialog>
  <!-- Detail dialogs for each type -->
  <Dialog v-model:visible="viewDialogVisible" :style="{ width: dialogWidth }" :header="dialogHeader" :modal="true" :dismissable-mask="true" class="p-fluid data-source-dialog">
    <template v-if="activeDataSource">
      <!-- REST Data Source fields -->
      <template v-if="activeDataSource.dataSourceType === 'REST_DATA_SOURCE'">
        <div v-for="(label, fieldName) in restFields" :key="fieldName" class="mb-3">
          <strong>{{ label }}</strong>
          <br />
          <span>{{ activeDataSource[fieldName] || "-" }}</span>
        </div>
      </template>
      <!-- Timed Data Source fields -->
      <template v-else-if="activeDataSource.dataSourceType === 'TIMED_DATA_SOURCE'">
        <div v-for="(label, fieldName) in timedFields" :key="fieldName" class="mb-3">
          <strong>{{ label }}</strong>
          <br />
          <span v-if="fieldName === 'lastRun' || fieldName === 'nextRun'">
            <Timestamp :timestamp="activeDataSource[fieldName]" />
          </span>
          <span v-else-if="fieldName === 'memo'">
            <pre>{{ activeDataSource.memo || "-" }}</pre>
          </span>
          <span v-else-if="fieldName === 'action'">
            <pre>{{ activeDataSource.timedIngressAction?.type || "-" }}</pre>
          </span>
          <span v-else-if="fieldName === 'actionParameters'">
            <pre>{{ activeDataSource.timedIngressAction?.parameters || "-" }}</pre>
          </span>
          <span v-else>{{ activeDataSource[fieldName] || "-" }}</span>
        </div>
        <div class="d-flex justify-content-between">
          <StatusBadge :status="activeDataSource.ingressStatus" :message="activeDataSource.ingressStatusMessage" />
        </div>
      </template>
      <!-- OnError Data Source fields -->
      <template v-else-if="activeDataSource.dataSourceType === 'ON_ERROR_DATA_SOURCE'">
        <div v-for="(label, fieldName) in onErrorFields" :key="fieldName" class="mb-3">
          <strong>{{ label }}</strong>
          <br />
          <span v-if="fieldName === 'includeSourceMetadataRegex'">
            <span v-if="_.isEmpty(activeDataSource.includeSourceMetadataRegex)">-</span>
            <ul v-else>
              <li v-for="(item, index) in activeDataSource.includeSourceMetadataRegex" :key="index">{{ item }}</li>
            </ul>
          </span>
          <span v-else-if="fieldName === 'sourceFilters'">
            <span v-if="_.isEmpty(activeDataSource.sourceFilters)">-</span>
            <DataTable v-else aria-label="Source filters" :value="activeDataSource.sourceFilters" class="p-datatable-sm p-datatable-gridlines">
              <template #empty>No Source Filters defined</template>
              <Column header="Flow Type" field="flowType" />
              <Column header="Flow Name" field="flowName" />
              <Column header="Action Name" field="actionName" />
              <Column header="Action Class" field="actionClass" />
            </DataTable>
          </span>
          <span v-else>{{ activeDataSource[fieldName] || "-" }}</span>
        </div>
      </template>
    </template>
  </Dialog>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DataSourceNameColumnButtonGroup from "@/components/dataSources/DataSourceNameColumnButtonGroup.vue";
import DataSourceTestModeInputSwitch from "@/components/dataSources/DataSourceTestModeInputSwitch.vue";
import FlowStatusBadge from "@/components/flow/FlowStatusBadge.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import StateInputSwitch from "@/components/dataSources/StateInputSwitch.vue";
import StatusBadge from "@/components/dataSources/StatusBadge.vue";
import Timestamp from "@/components/Timestamp.vue";
import TopicSubscribers from "@/components/topics/TopicSubscribers.vue";
import useDataSource from "@/composables/useDataSource";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, onBeforeMount, ref } from "vue";

import Button from "primevue/button";
import Column from "primevue/column";
import ConfirmDialog from "primevue/confirmdialog";
import ContextMenu from "primevue/contextmenu";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import InputNumber from "primevue/inputnumber";
import { FilterMatchMode } from "@primevue/core/api";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const notify = useNotifications();
const confirm = useConfirm();
const editing = inject("isEditing");
const emit = defineEmits(["reloadDataSources", "updateRateLimit"]);
const flowData = ref({});
const flowDataByPlugin = ref({});
const menu = ref();
const selectedPlugin = ref(null);
const confirming = ref(false);

const {
  setRestDataSourceMaxErrors,
  setTimedDataSourceMaxErrors,
  setOnErrorDataSourceMaxErrors,
  startRestDataSourceFlowByName,
  stopRestDataSourceFlowByName,
  startTimedDataSourceFlowByName,
  stopTimedDataSourceFlowByName,
  startOnErrorDataSourceFlowByName,
  stopOnErrorDataSourceFlowByName,
  enableTestRestDataSourceFlowByName,
  disableTestRestDataSourceFlowByName,
  enableTestTimedDataSourceFlowByName,
  disableTestTimedDataSourceFlowByName,
  enableTestOnErrorDataSourceFlowByName,
  disableTestOnErrorDataSourceFlowByName,
} = useDataSource();

const props = defineProps({
  flowDataProp: {
    type: Array,
    required: true,
  },
  filterFlowsTextProp: {
    type: String,
    required: false,
    default: null,
  },
  flowDataByPluginProp: {
    type: Object,
    required: true,
  },
});

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

// Computed that filters flows and groups by plugin, hiding empty groups
const filteredFlowDataByPlugin = computed(() => {
  const filterText = props.filterFlowsTextProp?.toLowerCase();
  if (!filterText) {
    return props.flowDataByPluginProp;
  }

  // Filter flows that match the search text
  const filteredFlows = props.flowDataProp.filter(flow =>
    flow.searchField?.toLowerCase().includes(filterText)
  );

  // Group filtered flows by plugin, with system-plugin last
  const grouped = _.chain(filteredFlows).sortBy("artifactId").groupBy("artifactId").value();
  const { 'system-plugin': systemPlugin, ...rest } = grouped;
  return systemPlugin ? { ...rest, 'system-plugin': systemPlugin } : rest;
});

onBeforeMount(async () => {
  flowData.value = props.flowDataProp;
  flowDataByPlugin.value = props.flowDataByPluginProp;
  filters.value["global"].value = props.filterFlowsTextProp;
});

// Type icons from SystemMapGraph
const dataSourceTypeIcon = (type) => {
  const icons = {
    REST_DATA_SOURCE: "fas fa-file-import",
    TIMED_DATA_SOURCE: "fas fa-clock",
    ON_ERROR_DATA_SOURCE: "fas fa-triangle-exclamation",
  };
  return icons[type] || "fas fa-question";
};

const dataSourceTypeLabel = (type) => {
  const labels = {
    REST_DATA_SOURCE: "REST Data Source",
    TIMED_DATA_SOURCE: "Timed Data Source",
    ON_ERROR_DATA_SOURCE: "On-Error Data Source",
  };
  return labels[type] || type;
};

// Context menu
const menuItems = ref([
  {
    label: "Start All Plugin Data Sources",
    icon: "fas fa-play fa-fw",
    command: () => confirmAllFlows("Start", ""),
  },
  {
    label: "Stop All Plugin Data Sources",
    icon: "fas fa-stop fa-fw",
    command: () => confirmAllFlows("Stop", ""),
  },
  { separator: true },
  {
    label: "Enable Test Mode For All Plugin Data Sources",
    icon: "fas fa-flask fa-fw",
    command: () => confirmAllFlows("Enable", "Test Mode for"),
  },
  {
    label: "Disable Test Mode For All Plugin Data Sources",
    icon: "fas fa-flask fa-fw",
    command: () => confirmAllFlows("Disable", "Test Mode for"),
  },
]);

const onPanelRightClick = (event, currentPlugin) => {
  selectedPlugin.value = currentPlugin;
  menu.value.show(event);
};

const confirmAllFlows = (runType, message) => {
  confirm.require({
    group: "bulkActions",
    message: `${runType} ${message} all <b>${selectedPlugin.value}</b> data sources?`,
    acceptLabel: runType,
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    header: "Confirmation",
    rejectProps: {
      label: "Cancel",
      severity: "secondary",
      outlined: true,
    },
    acceptProps: {
      label: `${runType} ${message} all <b>${selectedPlugin.value}</b> data sources?`,
    },
    accept: () => runForAllFlowsForPlugin(runType),
    reject: () => {},
  });
};

const startMethods = {
  REST_DATA_SOURCE: startRestDataSourceFlowByName,
  TIMED_DATA_SOURCE: startTimedDataSourceFlowByName,
  ON_ERROR_DATA_SOURCE: startOnErrorDataSourceFlowByName,
};

const stopMethods = {
  REST_DATA_SOURCE: stopRestDataSourceFlowByName,
  TIMED_DATA_SOURCE: stopTimedDataSourceFlowByName,
  ON_ERROR_DATA_SOURCE: stopOnErrorDataSourceFlowByName,
};

const enableTestMethods = {
  REST_DATA_SOURCE: enableTestRestDataSourceFlowByName,
  TIMED_DATA_SOURCE: enableTestTimedDataSourceFlowByName,
  ON_ERROR_DATA_SOURCE: enableTestOnErrorDataSourceFlowByName,
};

const disableTestMethods = {
  REST_DATA_SOURCE: disableTestRestDataSourceFlowByName,
  TIMED_DATA_SOURCE: disableTestTimedDataSourceFlowByName,
  ON_ERROR_DATA_SOURCE: disableTestOnErrorDataSourceFlowByName,
};

const runForAllFlowsForPlugin = async (runType) => {
  const pluginFlows = flowDataByPlugin.value[selectedPlugin.value];
  for (const element of pluginFlows) {
    const type = element.dataSourceType;
    if (runType === "Start") {
      notify.info("Starting All Data Sources", `Starting all <b>${selectedPlugin.value}</b> data sources.`, 3000);
      await startMethods[type](element.name);
    } else if (runType === "Stop") {
      notify.info("Stopping All Data Sources", `Stopping all <b>${selectedPlugin.value}</b> data sources.`, 3000);
      await stopMethods[type](element.name);
    } else if (runType === "Enable") {
      notify.info("Enabling Test Mode", `Enabling Test Mode for all <b>${selectedPlugin.value}</b> data sources.`, 3000);
      await enableTestMethods[type](element.name);
    } else if (runType === "Disable") {
      notify.info("Disabling Test Mode", `Disabling Test Mode for all <b>${selectedPlugin.value}</b> data sources.`, 3000);
      await disableTestMethods[type](element.name);
    }
  }
  emit("reloadDataSources");
};

// Description truncation
const maxDescriptionLength = ref(60);
const displayDescription = (data) => {
  return _.truncate(data, { length: maxDescriptionLength.value, separator: " " });
};

// Row styling for errors
const actionRowClass = (data) => {
  return !_.isEmpty(data.flowStatus?.errors) ? "table-danger action-error" : null;
};

// Cell editing for maxErrors
const onEditInit = () => (editing.value = true);
const onEditCancel = () => (editing.value = false);

const setMaxErrorsMethods = {
  REST_DATA_SOURCE: setRestDataSourceMaxErrors,
  TIMED_DATA_SOURCE: setTimedDataSourceMaxErrors,
  ON_ERROR_DATA_SOURCE: setOnErrorDataSourceMaxErrors,
};

const onEditComplete = async (event) => {
  const { data, newValue, field } = event;

  if (field === "maxErrors" && !_.isEqual(data.maxErrors, newValue)) {
    const sendValue = _.isEqual(newValue, null) ? -1 : newValue;
    const resetValue = data.maxErrors;
    data[field] = newValue;
    await setMaxErrorsMethods[data.dataSourceType](data.name, sendValue);
    if (newValue === null) {
      notify.success("Max Errors Disabled", `Max errors for <b>${data.name}</b> has been disabled`);
    } else {
      notify.success("Max Errors Set Successfully", `Max errors for <b>${data.name}</b> set to <b>${newValue}</b>`);
    }
  }

  editing.value = false;
  refresh();
};

// Detail dialog
const viewDialogVisible = ref(false);
const activeDataSource = ref(null);

const dialogHeader = computed(() => activeDataSource.value?.name || null);
const dialogWidth = computed(() => {
  if (activeDataSource.value?.dataSourceType === "ON_ERROR_DATA_SOURCE" && !_.isEmpty(activeDataSource.value?.sourceFilters)) {
    return "60vw";
  }
  return "30vw";
});

const restFields = {
  name: "Name",
  description: "Description",
  topic: "Topic",
};

const timedFields = {
  description: "Description",
  ingressStatusMessage: "Status Message",
  cronSchedule: "Cron Schedule",
  lastRun: "Last Run",
  nextRun: "Next Run",
  memo: "Memo",
  executeImmediate: "Execute Immediate",
  topic: "Topic",
  action: "Action",
  actionParameters: "Action Parameters",
};

const onErrorFields = {
  name: "Name",
  description: "Description",
  topic: "Topic",
  maxErrors: "Max Errors",
  errorMessageRegex: "Error Message Regex",
  sourceMetadataPrefix: "Source Metadata Prefix",
  includeSourceMetadataRegex: "Include Source Metadata Regex",
  sourceFilters: "Source Filters",
};

const showAction = (data) => {
  activeDataSource.value = data;
  viewDialogVisible.value = true;
};

const updateRateLimit = (data) => {
  emit("updateRateLimit", data);
};

const refresh = async () => {
  emit("reloadDataSources");
};
</script>

<style>
.data-sources-table {
  th.name-column {
    width: 25%;
  }

  th.status-column {
    width: 10rem;
  }

  td.switch-column {
    padding: 0 !important;
    width: 7rem;

    .p-toggleswitch {
      padding: 0.25rem !important;
      margin: 0.25rem 0 0 0.25rem !important;
    }

    .control-buttons {
      padding: 0.25rem !important;
      margin: 0 0 0 0.25rem !important;
    }
  }

  td.max-error-column {
    width: 7rem;
    padding: 0 !important;

    > span {
      padding: 0.5rem !important;
    }

    .p-inputnumber {
      padding: 0 !important;
      margin: 0;

      .p-inputtext {
        width: 6.5rem;
      }
    }
  }
}

.data-source-dialog {
  .p-dialog-footer {
    padding: 1rem 1rem 0.6rem 1rem !important;
  }
}
</style>
