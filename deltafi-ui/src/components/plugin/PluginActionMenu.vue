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
<!-- ABOUTME: Dropdown action menu for plugin row actions (retry, rollback, enable, disable, update, remove). -->
<!-- ABOUTME: Replaces individual action buttons with a cleaner "..." menu pattern. -->

<template>
  <span class="d-flex align-items-center">
    <Menu ref="menu" :model="items" :popup="true" :pt="{ action: { class: 'py-1' } }" @hide="hideMenu">
      <template #item="{ item, props }">
        <a v-ripple class="flex align-items-center" v-bind="props.action">
          <span :class="item.icon" />
          <span class="ml-2 text-dark">{{ item.label }}</span>
        </a>
      </template>
    </Menu>
    <Button ref="optionsButton" type="button" v-tooltip.top="`Actions`" @click="menuButtonClick" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" class="mt-n1 mb-n1" />
    <DialogTemplate ref="updatePluginDialog" component-name="plugin/PluginConfigurationDialog" header="Update Plugin" required-permission="PluginInstall" dialog-width="40vw" :row-data-prop="data" @refresh-page="refresh" @close-dialog-template="closeUpdatePluginDialog" @open-dialog-template="openUpdatePluginDialog" />
    <PluginRemoveButton ref="removePluginButton" :row-data-prop="data" @reload-plugins="refresh" @plugin-removal-errors="emitPluginRemovalErrors" />
  </span>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import PluginRemoveButton from "@/components/plugin/PluginRemoveButton.vue";
import { computed, inject, ref, toRef } from "vue";

import Button from "primevue/button";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const vRipple = Ripple;

const emit = defineEmits(["reloadPlugins", "pluginRemovalErrors", "retry", "rollback", "enable", "disable"]);
const hasPermission = inject("hasPermission");
const editingPlugin = inject("isEditingPlugin");
const menu = ref();
const horizontalEllipsis = ref(false);
const updatePluginDialog = ref(null);
const removePluginButton = ref(null);

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const data = toRef(props, "rowDataProp");

const items = computed(() => [
  {
    label: "Retry Installation",
    icon: "text-muted pi pi-refresh",
    visible: data.value.installState === "FAILED" && hasPermission("PluginInstall"),
    command: () => {
      emit("retry", data.value);
    },
  },
  {
    label: `Rollback to ${data.value.lastSuccessfulVersion}`,
    icon: "text-muted pi pi-undo",
    visible: data.value.canRollback && hasPermission("PluginInstall"),
    command: () => {
      emit("rollback", data.value);
    },
  },
  {
    label: "Enable Plugin",
    icon: "text-muted pi pi-play",
    visible: data.value.disabled && hasPermission("PluginInstall"),
    command: () => {
      emit("enable", data.value);
    },
  },
  {
    label: "Disable Plugin",
    icon: "text-muted pi pi-pause",
    visible: !data.value.disabled && data.value.installState === "INSTALLED" && hasPermission("PluginUninstall"),
    command: () => {
      emit("disable", data.value);
    },
  },
  {
    label: "Update Plugin",
    icon: "text-muted pi pi-pencil",
    visible: hasPermission("PluginInstall"),
    command: () => {
      updatePluginDialog.value.showDialog();
    },
  },
  {
    separator: true,
    visible: hasPermission("PluginUninstall"),
  },
  {
    label: "Remove",
    icon: "text-muted fa-solid fa-trash-can",
    visible: hasPermission("PluginUninstall"),
    command: () => {
      removePluginButton.value.showDialog();
    },
  },
]);

const menuButtonClick = (event) => {
  horizontalEllipsis.value = !horizontalEllipsis.value;
  menu.value.toggle(event);
};

const hideMenu = async () => {
  horizontalEllipsis.value = false;
  await menu.value.hide();
};

const refresh = () => {
  emit("reloadPlugins");
};

const emitPluginRemovalErrors = (errors) => {
  emit("pluginRemovalErrors", errors);
};

const closeUpdatePluginDialog = () => {
  editingPlugin.value = false;
};

const openUpdatePluginDialog = () => {
  editingPlugin.value = true;
};
</script>
