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
  <div class="egress-actions-panel">
    <CollapsiblePanel header="Data Sinks" class="table-panel pb-3">
      <DataTable v-model:filters="filters" :loading="showLoading" :value="egressActionsList" edit-mode="cell" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines egress-action-table" :global-filter-fields="['name', 'description']" sort-field="name" :sort-order="1" :row-hover="true" data-key="name">
        <template #empty>No Data Sinks found.</template>
        <template #loading>Loading Data Sinks. Please wait.</template>
        <Column header="Name" field="name" :style="{ width: '25%' }" :sortable="true">
          <template #body="{ data }">
            <div class="d-flex justify-content-between align-items-center">
              <span>
                <DialogTemplate component-name="flow/FlowViewer" :header="data.name" :flow-name="data.name" flow-type="dataSink" @close-dialog-template="flowViewerPopup" @open-dialog-template="flowViewerPopup">
                  <span v-tooltip.right="'View egress information' + errorTooltip(data) + ' for ' + data.name" class="cursor-pointer">
                    {{ data.name }}
                  </span>
                </DialogTemplate>
              </span>
              <span>
                <span class="d-flex align-items-center">
                  <EgressActionRemoveButton v-if="data.sourcePlugin.artifactId === 'system-plugin'" :disabled="!$hasPermission('FlowUpdate')" :row-data-prop="data" @reload-egress-actions="refresh" />
                  <DialogTemplate ref="updateEgressDialog" component-name="dataSinks/DataSinkConfigurationDialog" header="Edit Egress Action" dialog-width="50vw" :row-data-prop="data" edit-egress-action @reload-egress-actions="refresh">
                    <i v-if="data.sourcePlugin.artifactId === 'system-plugin'" v-tooltip.top="`Edit`" class="ml-2 text-muted pi pi-pencil cursor-pointer" :disabled="!$hasPermission('FlowUpdate')" />
                  </DialogTemplate>
                  <DialogTemplate ref="updateEgressDialog" component-name="dataSinks/DataSinkConfigurationDialog" header="Create Egress Action" dialog-width="50vw" :row-data-prop="cloneEgressAction(data)" @reload-egress-actions="refresh">
                    <i v-tooltip.top="`Clone`" icon="pi pi-clone" class="ml-2 text-muted pi pi-clone cursor-pointer" :disabled="!$hasPermission('FlowUpdate')" />
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
        <Column header="Subscribe" field="subscribe" :style="{ width: '20%' }">
          <template #body="{ data, field }">
            <template v-if="!_.isEmpty(data[field])">
              <SubscribeCell :subscribe-data="data[field]"></SubscribeCell>
            </template>
          </template>
        </Column>
        <Column header="Test Mode" class="switch-column">
          <template #body="{ data }">
            <EgressTestModeInputSwitch :row-data-prop="data" />
          </template>
        </Column>
        <Column header="Active" :style="{ width: '7%' }" class="egress-action-state-column">
          <template #body="{ data }">
            <StateInputSwitch :row-data-prop="data" @change="refresh" />
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import EgressActionRemoveButton from "@/components/dataSinks/DataSinkRemoveButton.vue";
import StateInputSwitch from "@/components/dataSinks/StateInputSwitch.vue";
import EgressTestModeInputSwitch from "@/components/dataSinks/DataSinkTestModeInputSwitch.vue";
import SubscribeCell from "@/components/SubscribeCell.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink";
import useDataSink from "@/composables/useDataSink";
import { computed, defineEmits, defineProps, inject, onMounted, ref, watch } from "vue";

import _ from "lodash";

import { FilterMatchMode } from "primevue/api";
import Column from "primevue/column";
import DataTable from "primevue/datatable";

const emit = defineEmits(["egressActionsList"]);
const editing = inject("isEditing");
const { getAllDataSinks, loaded, loading } = useDataSink();
const showLoading = computed(() => loading.value && !loaded.value);
const egressActionsList = ref([]);
const updateEgressDialog = ref(null);

const props = defineProps({
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

onMounted(() => {
  refresh();
});

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

watch(
  () => props.filterFlowsTextProp,
  () => {
    filters.value["global"].value = props.filterFlowsTextProp;
  }
);

const errorTooltip = (data) => {
  return _.isEmpty(data.flowStatus.errors) ? "" : " and errors";
};

const cloneEgressAction = (data) => {
  let clonedEgressActionObject = _.cloneDeepWith(data);
  clonedEgressActionObject["name"] = "";
  return clonedEgressActionObject;
};

const concatMvnCoordinates = (sourcePlugin) => {
  return sourcePlugin.groupId + ":" + sourcePlugin.artifactId + ":" + sourcePlugin.version;
};

const refresh = async () => {
  // Do not refresh data while editing.
  if (editing.value) return;

  const response = await getAllDataSinks();
  egressActionsList.value = response.data.getAllFlows.dataSink;
  emit("egressActionsList", egressActionsList.value);
};

const flowViewerPopup = () => {
  editing.value = !editing.value;
};

defineExpose({ refresh });
</script>

<style lang="scss">
.egress-actions-panel {
  .table-panel {
    .egress-action-table {
      td.switch-column {
        padding: 0 !important;
        width: 7rem;

        .p-inputswitch {
          padding: 0.25rem !important;
          margin: 0.25rem 0 0 0.25rem !important;
        }

        .p-button {
          padding: 0.25rem !important;
          margin: 0 0 0 0.25rem !important;
        }
      }
      td.egress-action-state-column {
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
    }
  }
}
</style>
