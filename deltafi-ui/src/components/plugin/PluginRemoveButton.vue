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
  <span>
    <ConfirmDialog :group="rowData.combinedPluginCoordinates" />
  </span>
</template>

<script setup>
import usePlugins from "@/composables/usePlugins";
import useNotifications from "@/composables/useNotifications";
import { reactive } from "vue";

import _ from "lodash";

import ConfirmDialog from "primevue/confirmdialog";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const emit = defineEmits(["reloadPlugins", "pluginRemovalErrors"]);
const { uninstallPlugin } = usePlugins();
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: rowData } = reactive(props);

const confirmationPopup = () => {
  confirm.require({
    position: "center",
    group: rowData.combinedPluginCoordinates,
    message: `Remove ${rowData.displayName} Plugin?`,
    acceptLabel: "Remove",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: () => {
      notify.info("Removing Plugin", `Removing Plugin ${rowData.displayName}.`, 3000);
      confirmedRemovePlugin();
    },
    reject: () => { },
  });
};

const confirmedRemovePlugin = async () => {
  const { groupId, artifactId, version } = rowData.pluginCoordinates;
  const response = await uninstallPlugin(groupId, artifactId, version);
  const responseErrors = _.get(response.uninstallPlugin, "errors", null);
  if (!_.isEmpty(responseErrors)) {
    notify.error(`Removing plugin ${rowData.displayName} failed`, `Plugin ${rowData.displayName} was not removed.`, 4000);
    emit("pluginRemovalErrors", responseErrors);
  } else {
    notify.success(`Removed ${rowData.displayName}`, `Successfully Removed ${rowData.displayName}.`, 4000);
    emit("reloadPlugins");
  }
};

const showDialog = () => {
  confirmationPopup();
};

defineExpose({
  showDialog,
});
</script>
