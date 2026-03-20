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

<!-- ABOUTME: Unified test mode toggle switch for all data source types. -->
<!-- ABOUTME: Dispatches to correct enable/disable methods based on dataSourceType prop. -->

<template>
  <div class="d-flex justify-content-center">
    <span v-if="$hasPermission('FlowUpdate')">
      <ConfirmPopup />
      <ConfirmPopup :group="rowData.dataSourceType + '_testmode_' + rowData.name">
        <template #message="slotProps">
          <div class="flex btn-group p-4">
            <i :class="slotProps.message.icon" style="font-size: 1.5rem" />
            <p class="pl-2" v-html="slotProps.message.message" />
          </div>
        </template>
      </ConfirmPopup>
      <ToggleSwitch v-tooltip.top="tooltip" :model-value="rowData.flowStatus.testMode" class="p-button-sm" @click.prevent="confirmationPopup($event, rowData.name, rowData.flowStatus.testMode)" />
    </span>
    <span v-else>
      <Button :label="testModeToolTip" :class="testModeButtonClass" style="width: 5.5rem" disabled />
    </span>
  </div>
</template>

<script setup>
import useDataSource from "@/composables/useDataSource";
import useNotifications from "@/composables/useNotifications";
import { computed, toRefs, ref } from "vue";

import Button from "primevue/button";
import ConfirmPopup from "primevue/confirmpopup";
import ToggleSwitch from "primevue/toggleswitch";
import { useConfirm } from "primevue/useconfirm";
import _ from "lodash";

const confirm = useConfirm();

const {
  enableTestRestDataSourceFlowByName,
  disableTestRestDataSourceFlowByName,
  enableTestTimedDataSourceFlowByName,
  disableTestTimedDataSourceFlowByName,
  enableTestOnErrorDataSourceFlowByName,
  disableTestOnErrorDataSourceFlowByName,
} = useDataSource();
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = toRefs(props);
const tooltip = ref();
tooltip.value = rowData.value.flowStatus.testMode ? "Test Mode Enabled" : "Test Mode Disabled";

const testModeToolTip = computed(() => {
  return _.isEqual(rowData.value.flowStatus.testMode, true) ? "Enabled" : "Disabled";
});

const testModeButtonClass = computed(() => {
  return _.isEqual(rowData.value.flowStatus.testMode, true) ? "p-button-primary" : "p-button-secondary";
});

const confirmationPopup = (event, name, testMode) => {
  if (testMode) {
    confirm.require({
      target: event.currentTarget,
      group: `${rowData.value.dataSourceType}_testmode_${rowData.value.name}`,
      message: `Disable Test Mode for the <b>${name}</b> data source?`,
      acceptLabel: "Disable Test Mode",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => toggleFlowState(name, testMode),
      reject: () => { },
    });
  } else {
    confirm.require({
      target: event.currentTarget,
      group: `${rowData.value.dataSourceType}_testmode_${rowData.value.name}`,
      message: `Enable Test Mode for the <b>${name}</b> data source?`,
      acceptLabel: "Enable Test Mode",
      rejectLabel: "Cancel",
      icon: "pi pi-exclamation-triangle",
      accept: () => toggleFlowState(name, testMode),
      reject: () => { },
    });
  }
};

const enableMethods = {
  REST_DATA_SOURCE: enableTestRestDataSourceFlowByName,
  TIMED_DATA_SOURCE: enableTestTimedDataSourceFlowByName,
  ON_ERROR_DATA_SOURCE: enableTestOnErrorDataSourceFlowByName,
};

const disableMethods = {
  REST_DATA_SOURCE: disableTestRestDataSourceFlowByName,
  TIMED_DATA_SOURCE: disableTestTimedDataSourceFlowByName,
  ON_ERROR_DATA_SOURCE: disableTestOnErrorDataSourceFlowByName,
};

const toggleFlowState = async (flowName, currentTestMode) => {
  const dataSourceType = rowData.value.dataSourceType;

  if (!currentTestMode) {
    notify.info("Enabling Test Mode", `Enabling Test Mode for the <b>${flowName}</b> data source.`, 3000);
    await enableMethods[dataSourceType](flowName);
  } else {
    notify.info("Disabling Test Mode", `Disabling Test Mode for the <b>${flowName}</b> data source.`, 3000);
    await disableMethods[dataSourceType](flowName);
  }
  rowData.value.flowStatus.testMode = !rowData.value.flowStatus.testMode;
  tooltip.value = rowData.value.flowStatus.testMode ? "Test Mode Enabled" : "Test Mode Disabled";
};
</script>
