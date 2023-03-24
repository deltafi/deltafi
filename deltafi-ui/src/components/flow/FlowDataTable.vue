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
          <div class="d-flex justify-content-start">
            <DialogTemplate component-name="flow/FlowViewer" :header="data.name" :flow-name="data.name" :flow-type="data.flowType" :variables="data.variables">
              <span class="cursor-pointer">
                {{ data.name }}
                <i v-tooltip.right="'View Flow information' + errorTooltip(data) + ' for ' + data.name" :class="infoIconClass(data)" />
              </span>
            </DialogTemplate>
            <PermissionedRouterLink :disabled="!$hasPermission('PluginsView')" :to="{ path: 'plugins/' + data.mvnCoordinates }">
              <i v-tooltip.right="data.mvnCoordinates" class="ml-1 text-muted fas fa-plug fa-rotate-90 fa-fw" />
            </PermissionedRouterLink>
          </div>
        </template>
      </Column>
      <Column header="Bit Rate" class="bit-rate-column">
        <template #body="{ data }">
          <span class="text-muted">{{ bitRate(data.name) }}</span>
        </template>
      </Column>
      <Column header="Description" field="description" />
      <Column v-if="FlowTypeTitle == 'Ingress'" header="Max Errors" field="maxErrors" class="max-error-column">
        <template #body="{ data, field }">
          <span>{{ data[field] }}</span>
        </template>
        <template #editor="{ data, field }">
          <InputNumber v-model="data[field]" :min="0" class="p-inputtext-sm max-error-input" autofocus />
        </template>
      </Column>
      <Column v-if="FlowTypeTitle !== 'Enrich'" header="Test Mode" class="test-mode-column">
        <template #body="{ data }">
          <FlowTestModeInputSwitch :row-data-prop="data" @update-flows="emit('updateFlows')" />
        </template>
      </Column>
      <Column header="Active" class="flow-state-column">
        <template #body="{ data }">
          <template v-if="!_.isEmpty(data.flowStatus.errors)">
            <FlowStateValidationButton :row-data-prop="data" @update-flows="emit('updateFlows')" />
          </template>
          <template v-else>
            <FlowStateInputSwitch :row-data-prop="data" @update-flows="emit('updateFlows')" />
          </template>
        </template>
      </Column>
    </DataTable>
  </CollapsiblePanel>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import FlowStateInputSwitch from "@/components/flow/FlowStateInputSwitch.vue";
import FlowStateValidationButton from "@/components/flow/FlowStateValidationButton.vue";
import FlowTestModeInputSwitch from "@/components/flow/FlowTestModeInputSwitch.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink";
import useGraphiteQueryBuilder from "@/composables/useGraphiteQueryBuilder";
import useFlowQueryBuilder from "@/composables/useFlowQueryBuilder";
import { computed, defineProps, inject, onBeforeMount, ref, onUnmounted, watch, defineEmits } from "vue";

import { filesize } from "filesize";
import { FilterMatchMode } from "primevue/api";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputNumber from "primevue/inputnumber";
import useNotifications from "@/composables/useNotifications";
import _ from "lodash";

const { setMaxErrors, errors } = useFlowQueryBuilder();
const notify = useNotifications();

const refreshInterval = 5000; // 5 seconds
let autoRefresh = null;
const isIdle = inject("isIdle");
const { data: metricsData, fetchIngressFlowsByteRate, fetchEgressFlowsByteRate } = useGraphiteQueryBuilder();
const emit = defineEmits(["updateFlows"]);

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

const formattedBitRate = ref({});

onUnmounted(() => {
  clearInterval(autoRefresh);
});

onBeforeMount(async () => {
  await formatBitRate();
  autoRefresh = setInterval(formatBitRate, refreshInterval);
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

const errorTooltip = (data) => {
  return _.isEmpty(data.flowStatus.errors) ? "" : " and errors";
};

const infoIconClass = (data) => {
  return _.isEmpty(data.flowStatus.errors) ? "ml-1 text-muted fas fa-info-circle fa-fw" : "ml-1 far fa-times-circle fa-fw text-danger";
};

const actionRowClass = (data) => {
  return !_.isEmpty(data.flowStatus.errors) ? "table-danger action-error" : null;
};

const FlowTypeTitle = computed(() => {
  return _.startCase([props.flowTypeProp.toString()]);
});

const flowDataByType = computed(() => {
  return props.flowDataProp[props.flowTypeProp];
});

const formatBitRate = async () => {
  if (!isIdle.value) {
    if (_.isEqual(props.flowTypeProp, "ingress")) {
      await fetchIngressFlowsByteRate();
    } else if (_.isEqual(props.flowTypeProp, "egress")) {
      await fetchEgressFlowsByteRate();
    } else {
      return;
    }

    for (let value of metricsData.value) {
      let newKey = value["tags"].ingressFlow;
      formattedBitRate.value[`${newKey}`] = value;
    }
  }
};

const bitRate = (bitsPerFlow) => {
  let bitRate = 0;
  let count = 0;
  let totalBitRate = 0;

  if (!_.isEmpty(_.get(formattedBitRate.value[`${bitsPerFlow}`], "datapoints"))) {
    for (let datapoints of formattedBitRate.value[`${bitsPerFlow}`].datapoints) {
      if (datapoints[0] != null) {
        bitRate = bitRate + datapoints[0];
        count++;
      }
    }
    if (bitRate > 0) {
      totalBitRate = bitRate / (count * 10);
    }
  }
  return filesize(totalBitRate, { roundingMethod: "round", bits: true, symbols: "jedec" }) + "/s";
};

const onCellEditComplete = async (event) => {
  let { data, newValue, field } = event;
  if (_.isEqual(newValue, null)) {
    newValue = 0;
  }

  if (!_.isEqual(data.maxErrors, newValue)) {
    let resetValue = data.maxErrors;
    data[field] = newValue;
    await setMaxErrors(data.name, newValue);
    if (errors.value.length == 0) {
      notify.success("Max Errors Set Successfully", `Max errors for ${data.name} set to ${newValue}`);
    } else {
      data[field] = resetValue;
    }
  }
};
</script>

<style lang="scss">
@import "@/styles/components/flow/flow-data-table.scss";
</style>
