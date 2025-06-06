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
        <template #empty> No flows found. </template>
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
                <FlowNameColumnButtonGroup :key="Math.random()" :row-data-prop="data" @reload-Transforms="refresh" @remove-transform-from-table="removeFlowFromProp" />
              </span>
            </div>
          </template>
        </Column>
        <Column header="Description" field="description" class="truncateDescription">
          <template #body="{ data, field }">
            <div v-if="_.size(data[field]) > maxDescriptionLength" v-tooltip.bottom="data[field]">
              {{ displayDescription(data[field]) }}
            </div>
            <span v-else>{{ data[field] }}</span>
          </template>
        </Column>
        <Column header="Subscribe" field="subscribe" :style="{ width: '15%' }">
          <template #body="{ data, field }">
            <template v-if="!_.isEmpty(data[field])">
              <div>
                <SubscribeCell :subscribe-data="data[field]" />
              </div>
            </template>
          </template>
        </Column>
        <Column header="Publish" field="publish" :style="{ width: '15%' }">
          <template #body="{ data, field }">
            <template v-if="!_.isEmpty(data[field])">
              <div>
                <PublishCell :publish-data="data[field]" />
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
              <FlowStateValidationButton :row-data-prop="data" @reload-transforms="refresh" />
            </template>
            <template v-else>
              <FlowStateInputSwitch :row-data-prop="data" @change="refresh" />
            </template>
          </template>
        </Column>
      </DataTable>
      <ConfirmPopup />
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
import DialogTemplate from "@/components/DialogTemplate.vue";
import FlowNameColumnButtonGroup from "@/components/flow/FlowNameColumnButtonGroup.vue";
import FlowStateInputSwitch from "@/components/flow/FlowStateInputSwitch.vue";
import FlowStateValidationButton from "@/components/flow/FlowStateValidationButton.vue";
import FlowTestModeInputSwitch from "@/components/flow/FlowTestModeInputSwitch.vue";
import SubscribeCell from "@/components/SubscribeCell.vue";
import PublishCell from "@/components/PublishCell.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { onBeforeMount, ref, reactive, onUnmounted, watch } from "vue";
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

const autoRefresh = null;
const emit = defineEmits(["reloadTransforms"]);
const flowData = ref({});
const flowDataByPlugin = ref({});

const { startTransformFlowByName, stopTransformFlowByName, enableTestTransformFlowByName, disableTestTransformFlowByName } = useFlowQueryBuilder();
const menu = ref();
const selectedPlugin = ref(null);

const menuItems = reactive([
  {
    label: "Start All Plugin Transforms",
    icon: "fas fa-play fa-fw",
    command: () => {
      confirmAllFlows("Start", "");
    },
  },
  {
    label: "Stop All Plugin Transforms",
    icon: "fas fa-stop fa-fw",
    command: () => {
      confirmAllFlows("Stop", "");
    },
  },
  {
    separator: true,
  },
  {
    label: "Enable Test Mode For All Plugin Transforms",
    icon: "fas fa-flask fa-fw",
    command: () => {
      confirmAllFlows("Enable", "Test Mode for");
    },
  },
  {
    label: "Disable Test Mode For All Plugin Transforms",
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
    type: Array,
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
      notify.info("Starting All Transforms", `Starting all <b>${selectedPlugin.value}</b> transforms.`, 3000);
      await startTransformFlowByName(element.name);
    } else if (runType === "Stop") {
      notify.info("Stopping All Transforms", `Stopping all <b>${selectedPlugin.value}</b> transforms.`, 3000);
      await stopTransformFlowByName(element.name);
    } else if (runType === "Enable") {
      notify.info("Enabling Test Mode", `Enabling Test Mode for all <b>${selectedPlugin.value}</b> transforms.`, 3000);
      await enableTestTransformFlowByName(element.name);
    } else if (runType === "Disable") {
      notify.info("Disabling Test Mode", `Disabling Test Mode for all <b>${selectedPlugin.value}</b> transforms.`, 3000);
      await disableTestTransformFlowByName(element.name);
    }
    emit("reloadTransforms");
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

const confirmAllFlows = (runType, message) => {
  confirm.require({
    group: "bulkActions",
    message: `${runType} ${message} all <b>${selectedPlugin.value}</b> transforms?`,
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
      label: `${runType} ${message} all <b>${selectedPlugin.value}</b> transforms?`,
    },
    accept: () => {
      runForAllFlowsForPlugin(runType);
    },
    reject: () => { },
  });
};

const removeFlowFromProp = (data) => {
  flowData.value = flowData.value.filter((flow) => {
    return flow.name !== data.name;
  });
};

const refresh = async () => {
  emit("reloadTransforms");
};
</script>

<style>
.flows-table {
  th.name-column {
    width: 25%;
  }

  th.bit-rate-column {
    width: 10rem;
  }

  th.test-mode-column,
  th.flow-state-column {
    width: 7rem !important;
  }

  td.test-mode-column,
  td.flow-state-column {
    padding: 0 !important;

    .p-inputswitch {
      margin: 0.25rem 0 0 0.25rem !important;
    }

    .control-buttons {
      padding: 0.25rem !important;
      margin: 0 0 0 0.25rem !important;
    }
  }
}

.tooltip-width {
  max-width: none !important;
}
</style>
