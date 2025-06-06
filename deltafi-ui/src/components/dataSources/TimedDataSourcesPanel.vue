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
  <div class="timed-data-source-panel">
    <CollapsiblePanel header="Timed Data Sources" class="table-panel pb-3">
      <DataTable v-model:filters="filters" :loading="showLoading" :value="timedDataSources" data-key="name" edit-mode="cell" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines data-sources-table" :global-filter-fields="['name', 'description']" sort-field="name" :sort-order="1" :row-hover="true" @cell-edit-init="onEditInit" @cell-edit-complete="onEditComplete" @cell-edit-cancel="onEditCancel">
        <template #empty> No Timed Data Sources found. </template>
        <template #loading> Loading Timed Data Sources. Please wait. </template>
        <Column header="Name" field="name" :style="{ width: '25%' }" :sortable="true">
          <template #body="{ data }">
            <div class="d-flex justify-content-between align-items-center">
              <span class="cursor-pointer" @click="showAction(data.name)">{{ data.name }}</span>
              <span>
                <span class="d-flex align-items-center">
                  <DataSourceNameColumnButtonGroup :key="Math.random()" :row-data-prop="data" @reload-data-sources="refresh" />
                </span>
              </span>
            </div>
          </template>
        </Column>
        <Column header="Description" field="description" :sortable="true" />
        <Column header="Publish" field="topic" :sortable="true" />
        <Column header="Cron Schedule" field="cronSchedule" :sortable="true" class="inline-edit-column" style="width: 10rem">
          <template #body="{ data, field }">
            <template v-if="$hasPermission('FlowUpdate')">
              <span v-if="data[field]" v-tooltip.top="cronString.toString(data[field], { verbose: false }) + '\n\nClick to edit'" class="cursor-pointer" @click="editCronSchedule(data)">{{ data[field] }} </span>
            </template>
            <template v-else>
              <span v-if="data[field]" v-tooltip.top="cronString.toString(data[field], { verbose: false })">{{ data[field] }} </span>
            </template>
          </template>
        </Column>
        <Column header="Max Errors" field="maxErrors" class="max-error-column">
          <template #body="{ data, field }">
            <span v-if="data[field] === null">-</span>
            <span v-else>{{ data[field] }}</span>
          </template>
          <template #editor="{ data, field }">
            <InputNumber v-has-permission:FlowUpdate v-model="data[field]" :min="0" class="p-inputtext-sm max-error-input" autofocus />
          </template>
        </Column>
        <Column header="Status" field="ingressStatus" :sortable="true">
          <template #body="{ data }">
            <StatusBadge :status="data.ingressStatus" :message="data.ingressStatusMessage" />
          </template>
        </Column>
        <Column header="Test Mode" class="switch-column">
          <template #body="{ data }">
            <TimedDataSourceTestModeInputSwitch :row-data-prop="data" />
          </template>
        </Column>
        <Column :style="{ width: '7%' }" class="switch-column">
          <template #body="{ data }">
            <StateInputSwitch :row-data-prop="data" data-source-type="timedDataSource" @change="refresh" @confirm-start="confirming = true" @confirm-stop="confirming = false" />
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
    <Dialog v-model:visible="viewDialogVisible" :style="{ width: '30vw' }" :header="dialogHeader" :modal="true" :dismissable-mask="true" class="p-fluid timed-data-source-dialog">
      <div v-for="(label, fieldName) in fields" :key="fieldName" class="mb-3">
        <strong>{{ label }}</strong>
        <br />
        <span v-if="fieldName == 'lastRun'">
          <Timestamp :timestamp="activeAction.lastRun" />
        </span>
        <span v-else-if="fieldName == 'nextRun'">
          <Timestamp :timestamp="activeAction.nextRun" />
        </span>
        <span v-else-if="fieldName == 'memo'">
          <pre>{{ activeAction.memo || "-" }}</pre>
        </span>
        <span v-else-if="['memo', 'executeImmediate', 'currentDid'].includes(fieldName)">
          <span v-if="activeAction[fieldName] === null">-</span>
          <pre>{{ activeAction[fieldName] }}</pre>
        </span>
        <span v-else-if="fieldName == 'action'">
          <pre>{{ activeAction.timedIngressAction.type || "-" }}</pre>
        </span>
        <span v-else-if="fieldName == 'actionParameters'">
          <pre>{{ activeAction.timedIngressAction.parameters || "-" }}</pre>
        </span>
        <span v-else>{{ activeAction[fieldName] || "-" }}</span>
      </div>
      <template #footer>
        <div class="d-flex justify-content-between">
          <div>
            <StatusBadge :status="activeAction.ingressStatus" :message="activeAction.ingressStatusMessage" />
          </div>
        </div>
      </template>
    </Dialog>
    <CronScheduleEditDialog v-model:visible="cronEditView.visible" :data="cronEditView.data" @reload-data-sources="refresh" @close="cronEditView.visible = false" />
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DataSourceNameColumnButtonGroup from "@/components/dataSources/DataSourceNameColumnButtonGroup.vue";
import StatusBadge from "@/components/dataSources/StatusBadge.vue";
import StateInputSwitch from "@/components/dataSources/StateInputSwitch.vue";
import TimedDataSourceTestModeInputSwitch from "@/components/dataSources/TimedDataSourceTestModeInputSwitch.vue";
import Timestamp from "@/components/Timestamp.vue";
import useDataSource from "@/composables/useDataSource";
import useNotifications from "@/composables/useNotifications";
import { computed, onMounted, inject, ref, watch } from "vue";
import CronScheduleEditDialog from "@/components/dataSources/CronScheduleEditDialog.vue";

const cronEditView = ref({
  visible: false,
  data: {},
});
const cronString = require("cronstrue");
import _ from "lodash";

import { FilterMatchMode } from "primevue/api";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import InputNumber from "primevue/inputnumber";

const emit = defineEmits(["dataSourcesList"]);
const editing = inject("isEditing");
const notify = useNotifications();
const { getTimedDataSources, setTimedDataSourceCronSchedule, setTimedDataSourceMaxErrors, loaded, loading, errors } = useDataSource();

const props = defineProps({
  filterFlowsTextProp: {
    type: String,
    required: false,
    default: null,
  },
});

const showLoading = computed(() => loading.value && !loaded.value);
const timedDataSources = ref([]);
const onEditInit = () => (editing.value = true);
const onEditCancel = () => (editing.value = false);
const confirming = ref(false);

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

watch(
  () => props.filterFlowsTextProp,
  () => {
    filters.value["global"].value = props.filterFlowsTextProp;
  }
);

const onEditComplete = async (event) => {
  const { data, newValue, field } = event;

  if (field === "cronSchedule" && data.cronSchedule !== newValue && newValue !== "") {
    const resetValue = data.cronSchedule;
    data[field] = newValue;
    await setTimedDataSourceCronSchedule(data.name, newValue);
    if (errors.value.length === 0) {
      notify.success("Cron Schedule Set Successfully", `Cron Schedule for ${data.name} set to ${newValue}`);
    } else {
      data[field] = resetValue;
    }
  } else if (field === "maxErrors" && !_.isEqual(data.maxErrors, newValue)) {
    const sendValue = _.isEqual(newValue, null) ? -1 : newValue;
    const resetValue = data.maxErrors;
    data[field] = newValue;
    await setTimedDataSourceMaxErrors(data.name, sendValue);
    if (errors.value.length === 0) {
      if (newValue === null) {
        notify.success("Max Errors Disabled", `Max errors for <b>${data.name}</b> has been disabled`);
      } else {
        notify.success("Max Errors Set Successfully", `Max errors for <b>${data.name}</b> set to <b>${newValue}</b>`);
      }
    } else {
      data[field] = resetValue;
    }
  }

  editing.value = false;
  refresh();
};

const editCronSchedule = (data) => {
  cronEditView.value.data = data;
  cronEditView.value.visible = true;
};

// Start Dialog
const viewDialogVisible = ref(false);
const dialogHeader = computed(() => {
  return activeAction.value ? activeAction.value.name : null;
});
const activeActionName = ref();
const activeAction = computed(() => {
  if (activeActionName.value === undefined) return timedDataSources.value[0];
  return _.find(timedDataSources.value, (action) => {
    return action.name == activeActionName.value;
  });
});

const fields = {
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

const showAction = (actionName) => {
  activeActionName.value = actionName;
  viewDialogVisible.value = true;
};
// End Dialog

const refresh = async () => {
  // Do not refresh data while editing or confirming.
  if (editing.value || confirming.value) return;

  const response = await getTimedDataSources();
  timedDataSources.value = response.data.getAllFlows.timedDataSource.map((ds) => {
    ds.maxErrors = ds.maxErrors !== -1 ? ds.maxErrors : null;
    return ds;
  });

  emit("dataSourcesList", timedDataSources.value);
};

onMounted(() => {
  refresh();
});

defineExpose({ refresh });
</script>

<style>
.timed-data-source-panel {
  .table-panel {
    .data-sources-table {
      td.switch-column {
        padding: 0 !important;

        .p-inputswitch {
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

        >span {
          padding: 0.5rem !important;
        }

        .value-clickable {
          cursor: pointer;
          width: 100%;
          display: flex;
        }

        .value-clickable>* {
          flex: 0 0 auto;
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
  }

  .timed-data-source-dialog {
    .p-dialog-footer {
      padding: 1rem 1rem 0.6rem 1rem !important;
    }
  }
}
</style>
