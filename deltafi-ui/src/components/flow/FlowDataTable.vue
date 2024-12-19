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
  <ContextMenu ref="menu" :model="menuItems" />
  <div v-for="(pluginFlows, pluginName) in flowDataByPlugin" :key="pluginName">
    <CollapsiblePanel :header="pluginName" class="table-panel pb-3" @contextmenu="onPanelRightClick($event, pluginName)">
      <template #header>
        <span class="p-panel-title">
          {{ pluginName }}
          <PermissionedRouterLink :disabled="!$hasPermission('PluginsView')" :to="{ path: 'plugins/' + pluginFlows[0].mvnCoordinates }">
            <i v-tooltip.top="pluginFlows[0].mvnCoordinates" class="ml-1 text-muted fas fa-plug fa-rotate-90 fa-fw" />
          </PermissionedRouterLink>
        </span>
      </template>
      <DataTable v-model:filters="filters" :edit-mode="$hasPermission('FlowUpdate') ? 'cell' : null" :value="pluginFlows" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines flows-table" :row-class="actionRowClass" :global-filter-fields="['searchField']" sort-field="name" :sort-order="1" :row-hover="true" data-key="name">
        <template #empty>No flows found.</template>
        <Column header="Name" field="name" class="name-column" :sortable="true">
          <template #body="{ data }">
            <div class="d-flex justify-content-between">
              <span>
                <DialogTemplate component-name="flow/FlowViewer" :header="data.name" :flow-name="data.name" :flow-type="data.flowType">
                  <span v-tooltip.right="'View transform information' + errorTooltip(data) + ' for ' + data.name" class="cursor-pointer">
                    {{ data.name }}
                  </span>
                </DialogTemplate>
              </span>
              <span>
                <span v-if="data.sourcePlugin.artifactId === 'system-plugin'" v-tooltip.top="'Remove'" class="cursor-pointer" @click="confirmationPopup($event, data)">
                  <i class="ml-2 text-muted fa-solid fa-trash-can" />
                </span>
                <PermissionedRouterLink v-if="data.sourcePlugin.artifactId === 'system-plugin'" :disabled="!$hasPermission('FlowUpdate')" :to="{ path: 'transform-builder/' }" @click="setTransformParams(data, true)">
                  <i v-tooltip.top="{ value: `Edit`, class: 'tooltip-width' }" class="ml-2 text-muted pi pi-pencil" />
                </PermissionedRouterLink>
                <PermissionedRouterLink :disabled="!$hasPermission('FlowUpdate')" :to="{ path: 'transform-builder/' }" @click="setTransformParams(data)">
                  <i v-tooltip.top="{ value: `Clone`, class: 'tooltip-width' }" class="ml-2 text-muted pi pi-clone" />
                </PermissionedRouterLink>
              </span>
            </div>
          </template>
        </Column>
        <Column header="Description" field="description" class="truncateDescription">
          <template #body="{ data, field }">
            <div v-if="_.size(data[field]) > maxDescriptionLength" v-tooltip.bottom="data[field]">{{ displayDescription(data[field]) }}</div>
            <span v-else>{{ data[field] }}</span>
          </template>
        </Column>
        <Column header="Subscribe" field="subscribe" :style="{ width: '15%' }">
          <template #body="{ data, field }">
            <template v-if="!_.isEmpty(data[field])">
              <div>
                <SubscribeCell :subscribe-data="data[field]"></SubscribeCell>
              </div>
            </template>
          </template>
        </Column>
        <Column header="Publish" field="publish" :style="{ width: '15%' }">
          <template #body="{ data, field }">
            <template v-if="!_.isEmpty(data[field])">
              <div>
                <PublishCell :publish-data="data[field]"></PublishCell>
              </div>
            </template>
          </template>
        </Column>
        <Column header="Test Mode" class="test-mode-column">
          <template #body="{ data }">
            <FlowTestModeInputSwitch :row-data-prop="data" />
          </template>
        </Column>
        <Column class="flow-state-column">
          <template #body="{ data }">
            <template v-if="_.isEqual(data.flowStatus.state, 'INVALID')">
              <FlowStateValidationButton :row-data-prop="data" @update-flows="emit('updateFlows')" />
            </template>
            <template v-else>
              <FlowStateInputSwitch :row-data-prop="data" @change="emit('updateFlows')" />
            </template>
          </template>
        </Column>
      </DataTable>
      <ConfirmPopup></ConfirmPopup>
    </CollapsiblePanel>
  </div>
  <ConfirmDialog group="bulkActions">
    <template #message="slotProps">
      <span class="p-confirm-dialog-icon pi pi-exclamation-triangle"></span>
      <span class="p-confirm-dialog-message" v-html="slotProps.message.message" />
    </template>
  </ConfirmDialog>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import FlowStateInputSwitch from "@/components/flow/FlowStateInputSwitch.vue";
import FlowStateValidationButton from "@/components/flow/FlowStateValidationButton.vue";
import FlowTestModeInputSwitch from "@/components/flow/FlowTestModeInputSwitch.vue";
import SubscribeCell from "@/components/SubscribeCell.vue";
import PublishCell from "@/components/PublishCell.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useFlowPlanQueryBuilder from "@/composables/useFlowPlanQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { useStorage, StorageSerializers } from "@vueuse/core";
import { defineProps, onBeforeMount, ref, reactive, onUnmounted, watch, defineEmits } from "vue";
import ContextMenu from "primevue/contextmenu";
import Column from "primevue/column";
import ConfirmPopup from "primevue/confirmpopup";
import DataTable from "primevue/datatable";
import { FilterMatchMode } from "primevue/api";
import { useConfirm } from "primevue/useconfirm";
import ConfirmDialog from "primevue/confirmdialog";
import _ from "lodash";

const notify = useNotifications();
const confirm = useConfirm();
const { removeTransformFlowPlanByName } = useFlowPlanQueryBuilder();

let autoRefresh = null;
const emit = defineEmits(["updateFlows"]);
const flowData = ref({});
const flowDataByPlugin = ref({});
const linkedTransform = useStorage("linked-transform-persisted-params", {}, sessionStorage, { serializer: StorageSerializers.object });

const { startTransformFlowByName, stopTransformFlowByName, enableTestTransformFlowByName, disableTestTransformFlowByName } = useFlowQueryBuilder();
const menu = ref();
const selectedPlugin = ref(null);

const menuItems = reactive([
  {
    label: "Start All Plugin Flows",
    icon: "fas fa-play fa-fw",
    command: () => {
      confirmAllFlows("Start", "");
    },
  },
  {
    label: "Stop All Plugin Flows",
    icon: "fas fa-stop fa-fw",
    command: () => {
      confirmAllFlows("Stop", "");
    },
  },
  {
    separator: true,
  },
  {
    label: "Enable Test Mode For All Plugin Flows",
    icon: "fas fa-flask fa-fw",
    command: () => {
      confirmAllFlows("Enable", "Test Mode for");
    },
  },
  {
    label: "Disable Test Mode For All Plugin Flows",
    icon: "fas fa-flask fa-fw",
    command: () => {
      confirmAllFlows("Disable", "Test Mode for");
    },
  },
]);

const props = defineProps({
  flowTypeProp: {
    type: String,
    required: true,
  },
  flowDataProp: {
    type: Object,
    required: true,
  },
  pluginNameSelectedProp: {
    type: Object,
    required: false,
    default: null,
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

const runForAllFlowsForPlugin = (runType) => {
  const pluginFlows = flowDataByPlugin.value[selectedPlugin.value];
  pluginFlows.forEach(async (element) => {
    if (runType === "Start") {
      notify.info("Starting All Flow's", `Starting all <b>${selectedPlugin.value}</b> flows.`, 3000);
      await startTransformFlowByName(element.name);
    } else if (runType === "Stop") {
      notify.info("Stopping All Flow's", `Stopping all <b>${selectedPlugin.value}</b> flows.`, 3000);
      await stopTransformFlowByName(element.name);
    } else if (runType === "Enable") {
      notify.info("Enabling Test Mode", `Enabling Test Mode for all <b>${selectedPlugin.value}</b> flows.`, 3000);
      await enableTestTransformFlowByName(element.name);
    } else if (runType === "Disable") {
      notify.info("Disabling Test Mode", `Disabling Test Mode for all <b>${selectedPlugin.value}</b> flows.`, 3000);
      await disableTestTransformFlowByName(element.name);
    }
    emit("updateFlows");
  });
};
const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
  mvnCoordinates: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});

onBeforeMount(async () => {
  flowData.value = props.flowDataProp;
  flowDataByPlugin.value = props.flowDataByPluginProp;
});

watch(
  () => props.pluginNameSelectedProp,
  () => {
    filters.value["mvnCoordinates"].value = _.isEmpty(props.pluginNameSelectedProp) ? null : props.pluginNameSelectedProp.name;
  }
);

watch(
  () => props.filterFlowsTextProp,
  () => {
    filters.value["global"].value = props.filterFlowsTextProp;
  }
);
watch(
  () => props.flowDataProp,
  () => {
    flowDataByPlugin.value = props.flowDataByPluginProp;
    flowData.value = props.flowDataProp;
  }
);
const deleteFlow = async (data) => {
  let response = false;
  response = await removeTransformFlowPlanByName(data.name);
  if (response) {
    notify.success(`Removed ${data.flowType} flow:`, data.name);
    removeFlowFromProp(data);
    emit("updateFlows");
  } else {
    notify.error(`Failed to remove`, data.name);
  }
};

const onPanelRightClick = (event, curentPlugin) => {
  selectedPlugin.value = curentPlugin;
  menu.value.show(event);
};

const maxDescriptionLength = ref(80);
const displayDescription = (data) => {
  return _.truncate(data, {
    length: maxDescriptionLength.value,
    separator: " ",
  });
};

const errorTooltip = (data) => {
  return _.isEmpty(data.flowStatus.errors) ? "" : " and errors";
};

const actionRowClass = (data) => {
  return !_.isEmpty(data.flowStatus.errors) ? "table-danger action-error" : null;
};

const confirmationPopup = (event, data) => {
  if (_.isEqual(data.flowStatus.state, "RUNNING")) {
    notify.warn(`Unable to remove running flow: `, data.name);
    return;
  }
  confirm.require({
    target: event.currentTarget,
    message: `Are you sure you want to remove ${data.name}?`,
    icon: "pi pi-exclamation-triangle",
    acceptLabel: "Remove",
    rejectLabel: "Cancel",
    accept: () => {
      deleteFlow(data);
    },
    reject: () => {},
  });
};

const confirmAllFlows = (runType, message) => {
  confirm.require({
    group: "bulkActions",
    message: `${runType} ${message} all <b>${selectedPlugin.value}</b> flows?`,
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
      label: `${runType} ${message} all <b>${selectedPlugin.value}</b> flows?`,
    },
    accept: () => {
      runForAllFlowsForPlugin(runType);
    },
    reject: () => {},
  });
};

const removeFlowFromProp = (data) => {
  flowData.value[props.flowTypeProp] = flowData.value[props.flowTypeProp].filter((flow) => {
    return flow.name !== data.name;
  });
};

const setTransformParams = (data, editExistingTransform) => {
  linkedTransform.value["transformParams"] = { type: data.flowType, selectedTransformName: data.name, selectedTransform: data, editExistingTransform: editExistingTransform };
};
</script>

<style lang="scss">
@import "@/styles/components/flow/flow-data-table.scss";
</style>
