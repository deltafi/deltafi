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
  <span>
    <ConfirmPopup></ConfirmPopup>
    <ConfirmPopup :group="rowData.combinedPluginCoordinates">
      <template #message="slotProps">
        <div class="flex p-4">
          <i :class="slotProps.message.icon" style="font-size: 1.5rem"></i>
          <p class="pl-2">
            {{ slotProps.message.message }}
          </p>
        </div>
      </template>
    </ConfirmPopup>
    <Button v-tooltip.left="`Remove Plugin`" icon="pi pi-trash" class="p-button-text p-button-sm p-button-rounded p-button-danger" @click="confirmationPopup($event, rowData.combinedPluginCoordinates, rowData.displayName, rowData.pluginCoordinates)" />
  </span>
</template>

<script setup>
import usePlugins from "@/composables/usePlugins";
import useNotifications from "@/composables/useNotifications";
import { defineEmits, defineProps, reactive } from "vue";

import _ from "lodash";

import ConfirmPopup from "primevue/confirmpopup";
import Button from "primevue/button";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const emit = defineEmits(["reloadPlugins", "pluginRemovalErrors"]);
const { uninstallPlugin } = usePlugins();
const notify = useNotifications();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: null,
  },
});

const { rowDataProp: rowData } = reactive(props);

const confirmationPopup = (event, combinedPluginCoordinates, displayName, pluginCoordinates) => {
  confirm.require({
    target: event.currentTarget,
    group: combinedPluginCoordinates,
    message: `Remove ${displayName} Plugin?`,
    acceptLabel: "Remove",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: () => {
      notify.info("Removing Plugin", `Removing Plugin ${displayName}.`, 3000);
      confirmedRemovePlugin(displayName, pluginCoordinates);
    },
    reject: () => {},
  });
};

const confirmedRemovePlugin = async (displayName, pluginCoordinates) => {
  let response = await uninstallPlugin(pluginCoordinates.groupId, pluginCoordinates.artifactId, pluginCoordinates.version);
  let responseErrors = _.get(response.uninstallPlugin, "errors", null);
  if (!_.isEmpty(responseErrors)) {
    notify.error(`Removing plugin ${displayName} failed`, `Plugin ${displayName} was not removed.`, 4000);
    emit("pluginRemovalErrors", responseErrors);
  } else {
    notify.success(`Removed ${displayName}`, `Successfully Removed ${displayName}.`, 4000);
    emit("reloadPlugins");
  }
};
</script>
