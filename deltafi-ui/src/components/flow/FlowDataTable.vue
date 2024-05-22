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
  <CollapsiblePanel :header="FlowTypeTitle" class="table-panel pb-3">
    <DataTable v-model:filters="filters" :edit-mode="$hasPermission('FlowUpdate') ? 'cell' : null" :value="flowDataByType" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines flows-table" :row-class="actionRowClass" :global-filter-fields="['searchField', 'mvnCoordinates']" sort-field="name" :sort-order="1" :row-hover="true" @cell-edit-complete="onCellEditComplete">
      <template #empty>No {{ FlowTypeTitle }} flows found.</template>
      <template #loading>Loading {{ FlowTypeTitle }} flows. Please wait.</template>
      <Column header="Name" field="name" class="name-column" :sortable="true">
        <template #body="{ data }">
          <div class="d-flex justify-content-between">
            <span>
              <DialogTemplate component-name="flow/FlowViewer" :header="data.name" :flow-name="data.name" :flow-type="data.flowType">
                <span v-tooltip.right="'View Flow information' + errorTooltip(data) + ' for ' + data.name" class="cursor-pointer">
                  {{ data.name }}
                </span>
              </DialogTemplate>
            </span>
            <span>
              <span v-if="data.sourcePlugin.artifactId === 'system-plugin'" v-tooltip.top="'Remove'" class="cursor-pointer" @click="confirmationPopup($event, data)">
                <i class="ml-2 text-muted fa-solid fa-trash-can" />
              </span>
              <PermissionedRouterLink v-if="data.sourcePlugin.artifactId === 'system-plugin'" :disabled="!$hasPermission('FlowUpdate')" :to="{ path: 'flow-plan-builder/' }" @click="setFlowPlanParams(data, true)">
                <i v-tooltip.top="{ value: `Edit`, class: 'tooltip-width' }" class="ml-2 text-muted pi pi-pencil" />
              </PermissionedRouterLink>
              <PermissionedRouterLink :disabled="!$hasPermission('FlowUpdate')" :to="{ path: 'flow-plan-builder/' }" @click="setFlowPlanParams(data)">
                <i v-tooltip.top="{ value: `Clone`, class: 'tooltip-width' }" class="ml-2 text-muted pi pi-clone" />
              </PermissionedRouterLink>
              <PermissionedRouterLink :disabled="!$hasPermission('PluginsView')" :to="{ path: 'plugins/' + data.mvnCoordinates }">
                <i v-tooltip.top="data.mvnCoordinates" class="ml-1 text-muted fas fa-plug fa-rotate-90 fa-fw" />
              </PermissionedRouterLink>
            </span>
          </div>
        </template>
      </Column>
      <Column header="Description" field="description" class="truncateDescription">
        <template #body="{ data, field }">
          <div v-if="_.size(data[field]) > 95" v-tooltip.bottom="data[field]">{{ displayDiscription(data[field]) }}</div>
          <span v-else>{{ data[field] }}</span>
        </template>
      </Column>
      <Column header="Subscribe" field="subscribe" :style="{ width: '7%' }">
        <template #body="{ data, field }">
          <template v-if="!_.isEmpty(data[field])">
            <div>
              <i class="ml-1 text-muted fa-solid fa-right-to-bracket fa-fw" @mouseover="toggle($event, data, field)" @mouseleave="toggle($event, data, field)" />
            </div>
            <OverlayPanel ref="op">
              <SubscribeCell :data-prop="overlayData" :field-prop="overlayField"></SubscribeCell>
            </OverlayPanel>
          </template>
        </template>
      </Column>
      <Column header="Max Errors" field="maxErrors" class="max-error-column">
        <template #body="{ data, field }">
          <span v-if="data[field] === null">-</span>
          <span v-else>{{ data[field] }}</span>
        </template>
        <template #editor="{ data, field }">
          <InputNumber v-model="data[field]" :min="0" class="p-inputtext-sm max-error-input" autofocus />
        </template>
      </Column>
      <Column header="Test Mode" class="test-mode-column">
        <template #body="{ data }">
          <FlowTestModeInputSwitch :row-data-prop="data" />
        </template>
      </Column>
      <Column header="Active" class="flow-state-column">
        <template #body="{ data }">
          <template v-if="!_.isEmpty(data.flowStatus.errors)">
            <FlowStateValidationButton :row-data-prop="data" @update-flows="emit('updateFlows')" />
          </template>
          <template v-else>
            <FlowStateInputSwitch :row-data-prop="data" />
          </template>
        </template>
      </Column>
    </DataTable>
    <ConfirmPopup></ConfirmPopup>
  </CollapsiblePanel>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import FlowStateInputSwitch from "@/components/flow/FlowStateInputSwitch.vue";
import FlowStateValidationButton from "@/components/flow/FlowStateValidationButton.vue";
import FlowTestModeInputSwitch from "@/components/flow/FlowTestModeInputSwitch.vue";
import SubscribeCell from "@/components/flow/SubscribeCell.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import useFlowPlanQueryBuilder from "@/composables/useFlowPlanQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import { useStorage, StorageSerializers } from "@vueuse/core";
import { computed, defineProps, onBeforeMount, ref, onUnmounted, watch, defineEmits } from "vue";

import Column from "primevue/column";
import ConfirmPopup from "primevue/confirmpopup";
import DataTable from "primevue/datatable";
import { FilterMatchMode } from "primevue/api";
import InputNumber from "primevue/inputnumber";
import { useConfirm } from "primevue/useconfirm";
import OverlayPanel from "primevue/overlaypanel";

import _ from "lodash";

const { setMaxErrors, errors } = useFlowQueryBuilder();
const notify = useNotifications();
const confirm = useConfirm();
const { removeTransformFlowPlanByName } = useFlowPlanQueryBuilder();

let autoRefresh = null;
const emit = defineEmits(["updateFlows"]);
const flowData = ref({});

const overlayData = ref({});
const overlayField = ref(null);

const linkedFlowPlan = useStorage("linked-flow-plan-persisted-params", {}, sessionStorage, { serializer: StorageSerializers.object });

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
});

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
  mvnCoordinates: { value: null, matchMode: FilterMatchMode.CONTAINS },
});


onUnmounted(() => {
  clearInterval(autoRefresh);
});

onBeforeMount(async () => {
  flowData.value = props.flowDataProp;
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
    flowData.value = props.flowDataProp;
  }
);
const deleteFlow = async (data) => {
  let response = false;
  response = await removeTransformFlowPlanByName(data.name);
  if (response) {
    notify.success(`Removed ${data.flowType} flow:`, data.name);
    removeFlowFromProp(data);
  } else {
    notify.error(`Failed to remove`, data.name);
  }
};

const displayDiscription = (data) => {
  return _.truncate(data, {
    length: 95,
    separator: " ",
  });
};

const errorTooltip = (data) => {
  return _.isEmpty(data.flowStatus.errors) ? "" : " and errors";
};

const actionRowClass = (data) => {
  return !_.isEmpty(data.flowStatus.errors) ? "table-danger action-error" : null;
};

const FlowTypeTitle = computed(() => {
  return _.startCase([props.flowTypeProp.toString()]);
});

const flowDataByType = computed(() => {
  return (flowData.value[props.flowTypeProp] || []).map((flow) => {
    if (flow.maxErrors === -1) flow.maxErrors = null;
    return flow;
  });
});


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
    reject: () => { },
  });
};

const removeFlowFromProp = (data) => {
  flowData.value[props.flowTypeProp] = flowData.value[props.flowTypeProp].filter((flow) => {
    return flow.name !== data.name;
  });
};

const onCellEditComplete = async (event) => {
  let { data, newValue, field } = event;

  if (!_.isEqual(data.maxErrors, newValue)) {
    if (_.isEqual(newValue, null)) newValue = -1;
    const resetValue = data.maxErrors;
    data[field] = newValue;
    await setMaxErrors(data.name, newValue);
    if (errors.value.length === 0) {
      if (newValue === -1) {
        notify.success("Max Errors Disabled", `Max errors for ${data.name} has been disabled`);
      } else {
        notify.success("Max Errors Set Successfully", `Max errors for ${data.name} set to ${newValue}`);
      }
    } else {
      data[field] = resetValue;
    }
  }
};

const op = ref();

const toggle = (event, data, field) => {
  overlayData.value = data;
  overlayField.value = field;
  op.value.toggle(event);
};

const setFlowPlanParams = (data, editExistingFlow) => {
  linkedFlowPlan.value["flowPlanParams"] = { type: data.flowType, selectedFlowPlanName: data.name, selectedFlowPlan: data, editExistingFlow: editExistingFlow };
};
</script>

<style lang="scss">
@import "@/styles/components/flow/flow-data-table.scss";
</style>