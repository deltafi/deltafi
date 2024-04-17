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
  <div class="timed-ingress-actions-panel">
    <CollapsiblePanel header="Timed Ingress Actions" class="table-panel pb-3">
      <DataTable :loading="showLoading" :value="timedIngressActions" edit-mode="cell" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines ingress-actions-table" :global-filter-fields="['searchField']" sort-field="name" :sort-order="1" :row-hover="true" @cell-edit-init="onEditInit" @cell-edit-complete="onEditComplete" @cell-edit-cancel="onEditCancel">
        <template #empty>No Timed Ingress Actions found.</template>
        <template #loading>Loading Timed Ingress Actions. Please wait.</template>
        <Column header="Name" field="name" :style="{ width: '25%' }" :sortable="true">
          <template #body="{ data }">
            <div class="d-flex justify-content-between align-items-center">
              <span class="cursor-pointer" @click="showAction(data.name)">{{ data.name }}</span>
              <span>
                <span class="d-flex align-items-center">
                  <IngressActionRemoveButton v-if="data.sourcePlugin.artifactId === 'system-plugin'" :disabled="!$hasPermission('FlowUpdate')" :row-data-prop="data" @reload-ingress-actions="refresh" />
                  <DialogTemplate ref="updateIngressDialog" component-name="ingressActions/IngressActionConfigurationDialog" header="Edit Ingress Action" dialog-width="50vw" :row-data-prop="data" edit-ingress-action @reload-ingress-actions="refresh">
                    <Button v-if="data.sourcePlugin.artifactId === 'system-plugin'" v-tooltip.top="`Edit`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary mx-n2" :disabled="!$hasPermission('FlowUpdate')" />
                  </DialogTemplate>
                  <DialogTemplate ref="updateIngressDialog" component-name="ingressActions/IngressActionConfigurationDialog" header="Create Ingress Action" dialog-width="50vw" :row-data-prop="cloneIngressAction(data)" @reload-ingress-actions="refresh">
                    <Button v-tooltip.top="`Clone`" icon="pi pi-clone" class="p-button-text p-button-sm p-button-rounded p-button-secondary mx-n2" :disabled="!$hasPermission('FlowUpdate')" />
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
        <Column header="Target Flow" field="targetFlow" :sortable="true"></Column>
        <Column header="Publish Rules" field="publishRules" :sortable="true">
          <template #body="{ data, field }">
            <template v-if="!_.isEmpty(data[field])">
              <div>
                <i class="ml-1 text-muted fa-solid fa-right-to-bracket fa-fw" @mouseover="toggle($event, data, field)" @mouseleave="toggle($event, data, field)" />
              </div>
              <OverlayPanel ref="op">
                <PublishRulesCell :data-prop="overlayData" :field-prop="overlayField"></PublishRulesCell>
              </OverlayPanel>
            </template>
          </template>
        </Column>
        <Column header="Cron Schedule" field="cronSchedule" :sortable="true" class="inline-edit-column" style="width: 10rem">
          <template #body="{ data, field }">
            <span v-tooltip.top="cronString.toString(data[field], { verbose: false })">{{ data[field] }} </span>
          </template>
        </Column>
        <Column header="Status" field="ingressStatus" :sortable="true">
          <template #body="{ data }">
            <StatusBadge :status="data.ingressStatus" :message="data.ingressStatusMessage" />
          </template>
        </Column>
        <Column :style="{ width: '7%' }" class="ingress-action-state-column">
          <template #body="{ data }">
            <StateInputSwitch :row-data-prop="data" ingress-action-type="timedIngress" @change="refresh" />
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
    <Dialog v-model:visible="viewDialogVisible" :style="{ width: '30vw' }" :header="dialogHeader" :modal="true" :dismissable-mask="true" class="p-fluid timed-ingress-action-dialog">
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
        <span v-else-if="fieldName == 'publish'">
          <template v-if="_.isEmpty(activeAction[fieldName])"> - </template>
          <template v-else>
            <div class="ml-2">
              <PublishRulesCell :data-prop="activeAction" :field-prop="fieldName"></PublishRulesCell>
            </div>
          </template>
        </span>
        <span v-else>{{ activeAction[fieldName] || "-" }}</span>
      </div>
      <template #footer>
        <div class="d-flex justify-content-between">
          <div>
            <StatusBadge :status="activeAction.ingressStatus" :message="activeAction.ingressStatusMessage" />
          </div>
          <div>
            <StateInputSwitch :row-data-prop="activeAction" ingress-action-type="timedIngress" @change="refresh" />
          </div>
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import IngressActionRemoveButton from "@/components/dataSources/IngressActionRemoveButton.vue";
import StatusBadge from "@/components/dataSources/StatusBadge.vue";
import StateInputSwitch from "@/components/dataSources/StateInputSwitch.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink";
import PublishRulesCell from "@/components/dataSources/PublishRulesCell.vue";
import Timestamp from "@/components/Timestamp.vue";
import useIngressActions from "@/composables/useIngressActions";
import useNotifications from "@/composables/useNotifications";
import { computed, defineEmits, onMounted, inject, ref } from "vue";

const cronString = require("cronstrue");
import _ from "lodash";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import OverlayPanel from "primevue/overlaypanel";

const emit = defineEmits(["ingressActionsList"]);
const editing = inject("isEditing");
const notify = useNotifications();
const { getAllTimedIngress, setTimedIngressCronSchedule, loaded, loading, errors } = useIngressActions();
const showLoading = computed(() => loading.value && !loaded.value);
const timedIngressActions = ref([]);
const onEditInit = () => (editing.value = true);
const onEditCancel = () => (editing.value = false);
const updateIngressDialog = ref(null);

const overlayData = ref({});
const overlayField = ref(null);

const onEditComplete = async (event) => {
  const { data, newValue, field } = event;

  if (data.cronSchedule !== newValue && newValue !== "") {
    const resetValue = data.cronSchedule;
    data[field] = newValue;
    await setTimedIngressCronSchedule(data.name, newValue);
    if (errors.value.length === 0) {
      notify.success("Cron Schedule Set Successfully", `Cron Schedule for ${data.name} set to ${newValue}`);
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
  if (activeActionName.value === undefined) return timedIngressActions.value[0];
  return _.find(timedIngressActions.value, (action) => {
    return action.name == activeActionName.value;
  });
});

const fields = {
  description: "Description",
  ingressStatusMessage: "Status Message",
  targetFlow: "Target Flow",
  cronSchedule: "Cron Schedule",
  lastRun: "Last Run",
  nextRun: "Next Run",
  memo: "Memo",
  executeImmediate: "Execute Immediate",
  publishRules: "Publish Rules",
};

const showAction = (actionName) => {
  activeActionName.value = actionName;
  viewDialogVisible.value = true;
};
// End Dialog

const cloneIngressAction = (data) => {
  let clonedIngressActionObject = _.cloneDeepWith(data);
  clonedIngressActionObject["name"] = "";
  return clonedIngressActionObject;
};

const concatMvnCoordinates = (sourcePlugin) => {
  return sourcePlugin.groupId + ":" + sourcePlugin.artifactId + ":" + sourcePlugin.version;
};

const refresh = async () => {
  // Do not refresh data while editing.
  if (editing.value) return;

  const response = await getAllTimedIngress();
  timedIngressActions.value = response.data.getAllFlows.dataSource;
  emit("ingressActionsList", timedIngressActions.value);
};

const op = ref();

const toggle = (event, data, field) => {
  overlayData.value = data;
  overlayField.value = field;
  op.value.toggle(event);
};

onMounted(() => {
  refresh();
});

defineExpose({ refresh });
</script>

<style lang="scss">
.timed-ingress-actions-panel {
  .table-panel {
    .ingress-action-table {
      td.ingress-action-state-column {
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

  .timed-ingress-action-dialog {
    .p-dialog-footer {
      padding: 1rem 1rem 0.6rem 1rem !important;
    }
  }
}
</style>
