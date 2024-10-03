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
  <div class="rest-data-source-panel">
    <CollapsiblePanel header="REST Data Sources" class="table-panel pb-3">
      <DataTable :loading="showLoading" :value="restDataSources" data-key="name" edit-mode="cell" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines data-sources-table" :global-filter-fields="['searchField']" sort-field="name" :sort-order="1" :row-hover="true" @cell-edit-init="onEditInit" @cell-edit-complete="onEditComplete" @cell-edit-cancel="onEditCancel">
        <template #empty>No REST Data Sources found.</template>
        <template #loading>Loading REST Data Sources. Please wait.</template>
        <Column header="Name" field="name" :style="{ width: '25%' }" :sortable="true">
          <template #body="{ data }">
            <div class="d-flex justify-content-between align-items-center">
              <span class="cursor-pointer" @click="showAction(data.name)">{{ data.name }}</span>
              <span>
                <span class="d-flex align-items-center">
                  <DataSourceRemoveButton v-if="data.sourcePlugin.artifactId === 'system-plugin'" :disabled="!$hasPermission('FlowUpdate')" :row-data-prop="data" @reload-data-sources="refresh" />
                  <DialogTemplate ref="updateDataSource" component-name="dataSources/DataSourceConfigurationDialog" header="Edit Data Source" dialog-width="50vw" :row-data-prop="data" edit-data-source @reload-data-sources="refresh">
                    <i v-if="data.sourcePlugin.artifactId === 'system-plugin'" v-tooltip.top="`Edit`" class="ml-2 text-muted pi pi-pencil cursor-pointer" :disabled="!$hasPermission('FlowUpdate')" />
                  </DialogTemplate>
                  <DialogTemplate ref="updateDataSource" component-name="dataSources/DataSourceConfigurationDialog" header="Create Data Source" dialog-width="50vw" :row-data-prop="cloneDataSource(data)" @reload-data-sources="refresh">
                    <i v-tooltip.top="`Clone`" class="ml-2 text-muted pi pi-clone cursor-pointer" :disabled="!$hasPermission('FlowUpdate')" />
                  </DialogTemplate>
                  <PermissionedRouterLink :disabled="!$hasPermission('PluginsView')" :to="{ path: 'plugins/' + concatMvnCoordinates(data.sourcePlugin) }">
                    <i v-tooltip.top="concatMvnCoordinates(data.sourcePlugin)" class="ml-1 text-muted fas fa-plug fa-rotate-90 fa-fw align-items-center" />
                  </PermissionedRouterLink>
                </span>
              </span>
            </div>
          </template>
        </Column>
        <Column header="Description" field="description" :sortable="true"></Column>
        <Column header="Publish" field="topic" :sortable="true"></Column>
        <Column header="Max Errors" field="maxErrors" class="max-error-column">
          <template #body="{ data, field }">
            <span v-if="data[field] === null">-</span>
            <span v-else>{{ data[field] }}</span>
          </template>
          <template #editor="{ data, field }">
            <InputNumber v-model="data[field]" :min="0" class="p-inputtext-sm max-error-input" autofocus />
          </template>
        </Column>
        <Column header="Test Mode" class="switch-column">
          <template #body="{ data }">
            <RestDataSourceTestModeInputSwitch :row-data-prop="data" />
          </template>
        </Column>
        <Column header="Active" :style="{ width: '7%' }" class="switch-column">
          <template #body="{ data }">
            <StateInputSwitch :row-data-prop="data" data-source-type="restDataSource" @change="refresh" />
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
    <Dialog v-model:visible="viewDialogVisible" :style="{ width: '30vw' }" :header="dialogHeader" :modal="true" :dismissable-mask="true" class="p-fluid rest-data-source-dialog">
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
        <span v-else>{{ activeAction[fieldName] || "-" }}</span>
      </div>
      <template #footer>
        <div class="d-flex justify-content-between">
          <span></span>
          <div>
            <StateInputSwitch :row-data-prop="activeAction" data-source-type="restDataSource" @change="refresh" />
          </div>
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import DataSourceRemoveButton from "@/components/dataSources/DataSourceRemoveButton.vue";
import RestDataSourceTestModeInputSwitch from "@/components/dataSources/RestDataSourceTestModeInputSwitch.vue";
import StateInputSwitch from "@/components/dataSources/StateInputSwitch.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink";
import Timestamp from "@/components/Timestamp.vue";
import useDataSource from "@/composables/useDataSource";
import useNotifications from "@/composables/useNotifications";
import { computed, defineEmits, onMounted, inject, ref } from "vue";

import _ from "lodash";

import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import InputNumber from "primevue/inputnumber";

const emit = defineEmits(["dataSourcesList"]);
const editing = inject("isEditing");
const notify = useNotifications();
const { getRestDataSources, setRestDataSourceMaxErrors, loaded, loading, errors } = useDataSource();
const showLoading = computed(() => loading.value && !loaded.value);
const restDataSources = ref([]);
const onEditInit = () => (editing.value = true);
const onEditCancel = () => (editing.value = false);
const updateDataSource = ref(null);

const onEditComplete = async (event) => {
  const { data, newValue, field } = event;

  if (field === "maxErrors" && !_.isEqual(data.maxErrors, newValue)) {
    let sendValue = _.isEqual(newValue, null) ? -1 : newValue;
    const resetValue = data.maxErrors;
    data[field] = newValue;
    await setRestDataSourceMaxErrors(data.name, sendValue);
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

// Start Dialog
const viewDialogVisible = ref(false);
const dialogHeader = computed(() => {
  return activeAction.value ? activeAction.value.name : null;
});
const activeActionName = ref();
const activeAction = computed(() => {
  if (activeActionName.value === undefined) return restDataSources.value[0];
  return _.find(restDataSources.value, (action) => {
    return action.name == activeActionName.value;
  });
});

const fields = {
  name: "Name",
  description: "Description",
  topic: "Topic",
};

const showAction = (actionName) => {
  activeActionName.value = actionName;
  viewDialogVisible.value = true;
};
// End Dialog

const cloneDataSource = (data) => {
  let clonedDataSourceObject = _.cloneDeepWith(data);
  clonedDataSourceObject["name"] = "";
  return clonedDataSourceObject;
};

const concatMvnCoordinates = (sourcePlugin) => {
  return sourcePlugin.groupId + ":" + sourcePlugin.artifactId + ":" + sourcePlugin.version;
};

const refresh = async () => {
  // Do not refresh data while editing.
  if (editing.value) return;

  const response = await getRestDataSources();
  restDataSources.value = response.data.getAllFlows.restDataSource.map((ds) => {
    ds.maxErrors = ds.maxErrors !== -1 ? ds.maxErrors : null;
    return ds;
  });

  emit("dataSourcesList", restDataSources.value);
};

onMounted(() => {
  refresh();
});

defineExpose({ refresh });
</script>

<style lang="scss">
.rest-data-source-panel {
  .table-panel {
    .data-sources-table {
      td.switch-column {
        padding: 0 !important;

        .p-inputswitch {
          padding: 0.25rem !important;
          margin: 0.25rem 0 0 0.25rem !important;
        }

        .p-button {
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

  .rest-data-source-dialog {
    .p-dialog-footer {
      padding: 1rem 1rem 0.6rem 1rem !important;
    }
  }
}
</style>