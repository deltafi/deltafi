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

<!-- ABOUTME: Data sink table component with plugin-based grouping. -->
<!-- ABOUTME: Displays data sinks grouped by plugin with context menu for bulk operations. -->

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
        aria-label="Data sinks"
        :edit-mode="$hasPermission('FlowUpdate') ? 'cell' : null"
        :value="pluginFlows"

        striped-rows
        class="p-datatable-sm p-datatable-gridlines data-sinks-table"
        :row-class="actionRowClass"
        :global-filter-fields="['searchField']"
        sort-field="name"
        :sort-order="1"
        :row-hover="true"
        data-key="name"
      >
        <template #empty> No data sinks found. </template>
        <Column header="Name" field="name" class="name-column" :sortable="true">
          <template #body="{ data }">
            <div class="d-flex justify-content-between">
              <span>
                <DialogTemplate component-name="flow/FlowViewer" :header="data.name" :flow-name="data.name" flow-type="dataSink" @close-dialog-template="flowViewerPopup" @open-dialog-template="flowViewerPopup">
                  <span v-tooltip.right="'View egress information' + errorTooltip(data) + ' for ' + data.name" class="cursor-pointer">
                    {{ data.name }}
                  </span>
                </DialogTemplate>
              </span>
              <span>
                <DataSinkNameColumnButtonGroup :key="data.name" :row-data-prop="data" @reload-data-sinks="refresh" @update-rate-limit="updateRateLimit" />
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
        <Column header="Subscribe" field="subscribe" :style="{ width: '20%' }">
          <template #body="{ data, field }">
            <template v-if="!_.isEmpty(data[field])">
              <SubscribeCell :subscribe-data="data[field]" />
            </template>
          </template>
        </Column>
        <Column header="Test Mode" class="switch-column">
          <template #body="{ data }">
            <DataSinkTestModeInputSwitch :row-data-prop="data" />
          </template>
        </Column>
        <Column header="Status" :style="{ width: '7%' }">
          <template #body="{ data }">
            <FlowStatusBadge :row-data="data" />
          </template>
        </Column>
        <Column header="State" :style="{ width: '7%' }" class="switch-column">
          <template #body="{ data }">
            <StateInputSwitch :row-data-prop="data" @change="refresh" @confirm-start="confirming = true" @confirm-stop="confirming = false" />
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
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DataSinkNameColumnButtonGroup from "@/components/dataSinks/DataSinkNameColumnButtonGroup.vue";
import DataSinkTestModeInputSwitch from "@/components/dataSinks/DataSinkTestModeInputSwitch.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import FlowStatusBadge from "@/components/flow/FlowStatusBadge.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import StateInputSwitch from "@/components/dataSinks/StateInputSwitch.vue";
import SubscribeCell from "@/components/SubscribeCell.vue";
import useDataSink from "@/composables/useDataSink";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, onBeforeMount, ref } from "vue";

import Button from "primevue/button";
import Column from "primevue/column";
import ConfirmDialog from "primevue/confirmdialog";
import ContextMenu from "primevue/contextmenu";
import DataTable from "primevue/datatable";
import { FilterMatchMode } from "@primevue/core/api";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const notify = useNotifications();
const confirm = useConfirm();
const editing = inject("isEditing");
const emit = defineEmits(["reloadDataSinks", "updateRateLimit"]);
const flowData = ref({});
const menu = ref();
const selectedPlugin = ref(null);
const confirming = ref(false);

const {
  startDataSinkByName,
  stopDataSinkByName,
  enableDataSinkTestModeByName,
  disableDataSinkTestModeByName,
} = useDataSink();

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
  filters.value["global"].value = props.filterFlowsTextProp;
});

// Context menu
const menuItems = ref([
  {
    label: "Start All Plugin Data Sinks",
    icon: "fas fa-play fa-fw",
    command: () => confirmAllFlows("Start", ""),
  },
  {
    label: "Stop All Plugin Data Sinks",
    icon: "fas fa-stop fa-fw",
    command: () => confirmAllFlows("Stop", ""),
  },
  { separator: true },
  {
    label: "Enable Test Mode For All Plugin Data Sinks",
    icon: "fas fa-flask fa-fw",
    command: () => confirmAllFlows("Enable", "Test Mode for"),
  },
  {
    label: "Disable Test Mode For All Plugin Data Sinks",
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
    message: `${runType} ${message} all <b>${selectedPlugin.value}</b> data sinks?`,
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
      label: runType,
    },
    accept: () => runForAllFlowsForPlugin(runType),
    reject: () => {},
  });
};

const runForAllFlowsForPlugin = async (runType) => {
  const pluginFlows = props.flowDataByPluginProp[selectedPlugin.value];
  if (!pluginFlows) return;
  for (const element of pluginFlows) {
    if (runType === "Start") {
      notify.info("Starting All Data Sinks", `Starting all <b>${selectedPlugin.value}</b> data sinks.`, 3000);
      await startDataSinkByName(element.name);
    } else if (runType === "Stop") {
      notify.info("Stopping All Data Sinks", `Stopping all <b>${selectedPlugin.value}</b> data sinks.`, 3000);
      await stopDataSinkByName(element.name);
    } else if (runType === "Enable") {
      notify.info("Enabling Test Mode", `Enabling Test Mode for all <b>${selectedPlugin.value}</b> data sinks.`, 3000);
      await enableDataSinkTestModeByName(element.name);
    } else if (runType === "Disable") {
      notify.info("Disabling Test Mode", `Disabling Test Mode for all <b>${selectedPlugin.value}</b> data sinks.`, 3000);
      await disableDataSinkTestModeByName(element.name);
    }
  }
  emit("reloadDataSinks");
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

const errorTooltip = (data) => {
  return _.isEmpty(data.flowStatus?.errors) ? "" : " and errors";
};

const concatMvnCoordinates = (sourcePlugin) => {
  return sourcePlugin.groupId + ":" + sourcePlugin.artifactId + ":" + sourcePlugin.version;
};

const flowViewerPopup = () => {
  editing.value = !editing.value;
};

const refresh = async () => {
  emit("reloadDataSinks");
};

const updateRateLimit = (updateData) => {
  emit("updateRateLimit", updateData);
};
</script>

<style>
.data-sinks-table {
  th.name-column {
    width: 25%;
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
}
</style>
