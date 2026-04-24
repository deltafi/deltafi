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

<template>
  <Menu
    ref="menu"
    :id="`overlay_menu_` + rowData.name + `_` + rowData.type"
    :model="items"
    :popup="true"
    @blur="blurTrigger"
    @hide="hideMenu"
    @show="showMenu"
    :pt="{
      action: {
        class: 'py-1',
      },
    }"
  >
    <template #item="{ item, props }">
      <PermissionedRouterLink v-if="item.route" ref="viewDataSinkPlugin" :disabled="!$hasPermission('PluginsView')" :to="{ path: 'plugins/' + concatMvnCoordinates(rowData.sourcePlugin) }">
        <a
          v-ripple
          v-tooltip.top="{
            value: concatMvnCoordinates(rowData.sourcePlugin),
          }"
          class="flex align-items-center"
          v-bind="props.action"
        >
          <span :class="item.icon" />
          <span class="ml-2 text-dark">{{ item.label }}</span>
        </a>
      </PermissionedRouterLink>
      <a v-else v-ripple class="flex align-items-center" v-bind="props.action">
        <span :class="item.icon" />
        <span class="ml-2 text-dark">{{ item.label }}</span>
      </a>
    </template>
  </Menu>
  <i v-if="hasRateLimit" v-tooltip.top="rateLimitTooltip" class="fa-solid fa-stopwatch fa-lg text-info cursor-pointer mr-2" role="button" tabindex="0" @click="addRateLimit.showDialog()" @keydown.enter="addRateLimit.showDialog()" />
  <Button :id="`${rowData.name}-${rowData.type}`" ref="optionsButton" type="button" v-tooltip.top="`Options`" @click="menuButtonClick" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" class="mt-n1 mb-n1" />
  <DataSinkRemoveButton ref="removeDataSink" :row-data-prop="rowData" @reload-data-sinks="refresh" />
  <DialogTemplate ref="editDataSink" component-name="dataSinks/DataSinkConfigurationDialog" header="Edit Data Sink" dialog-width="50vw" :row-data-prop="rowData" edit-data-sink @refresh-page="refresh" />
  <DialogTemplate ref="cloneDataSink" component-name="dataSinks/DataSinkConfigurationDialog" header="Create Data Sink" dialog-width="50vw" :row-data-prop="formatCloneDataSink(rowData)" @refresh-page="refresh" />
  <DialogTemplate ref="addRateLimit" component-name="dataSinks/RateLimitingForm" :header="rateLimitDialogHeader" dialog-width="35vw" dismissable-mask :data-sink-name="rowData['name']" :rate-limit="rowData['rateLimit']" @close-dialog-template="editing = false" @open-dialog-template="editing = true" @refresh-page="refresh" />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import DataSinkRemoveButton from "@/components/dataSinks/DataSinkRemoveButton.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import { computed, inject, ref, toRef } from "vue";
import { useRouter } from "vue-router";

import _ from "lodash";

import Button from "primevue/button";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const router = useRouter();
const hasPermission = inject("hasPermission");
const emit = defineEmits(["reloadDataSinks", "updateRateLimit"]);
const editing = inject("isEditing");
const vRipple = Ripple;
const editDataSink = ref(null);
const cloneDataSink = ref(null);
const removeDataSink = ref(null);
const addRateLimit = ref(null);
const viewDataSinkPlugin = ref(null);
const optionsButton = ref(null);
const horizontalEllipsis = ref(false);

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const rowData = toRef(props, "rowDataProp");

const menu = ref();

const hasRateLimit = computed(() => !_.isEmpty(rowData.value.rateLimit));

const rateLimitTooltip = computed(() => {
  if (!rowData.value.rateLimit) return "";
  const { maxAmount, unit, durationSeconds } = rowData.value.rateLimit;
  return `Rate limited: ${maxAmount} ${unit.toLowerCase()} every ${durationSeconds}s`;
});

const rateLimitDialogHeader = computed(() => {
  return hasRateLimit.value ? `Edit Rate Limit: ${rowData.value.name}` : `Add Rate Limit: ${rowData.value.name}`;
});

const concatMvnCoordinates = (sourcePlugin) => {
  return sourcePlugin.groupId + ":" + sourcePlugin.artifactId + ":" + sourcePlugin.version;
};
const items = ref([
  {
    label: "View Pipeline",
    icon: "text-muted pi pi-sitemap",
    visible: computed(() => hasPermission("FlowView")),
    command: () => {
      router.push(`/pipeline/${rowData.value.type}/${rowData.value.name}`);
    },
  },
  {
    label: "Edit",
    icon: "text-muted pi pi-pencil",
    visible: computed(() => rowData.value.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanCreate")),
    command: () => {
      editDataSink.value.showDialog();
    },
  },
  {
    label: "Clone",
    icon: "text-muted pi pi-clone",
    visible: computed(() => hasPermission("FlowPlanCreate") && !rowData.value.flowStatus?.placeholder),
    command: () => {
      cloneDataSink.value.showDialog();
    },
  },
  {
    label: "Export",
    icon: "text-muted pi pi-download",
    visible: computed(() => hasPermission("FlowPlanCreate")),
    command: () => {
      exportDataSink();
    },
  },
  {
    label: "View Plugin",
    icon: "text-muted fas fa-plug fa-rotate-90 fa-fw fa-sm",
    visible: computed(() => hasPermission("FlowPlanCreate")),
    route: "/config/plugins/",
  },
  {
    label: computed(() => hasRateLimit.value ? "Edit Rate Limit" : "Add Rate Limit"),
    icon: computed(() => hasRateLimit.value ? "text-muted fa-solid fa-stopwatch fa-sm" : "text-muted fa-solid fa-plus fa-sm"),
    visible: computed(() => hasPermission("FlowUpdate")),
    command: () => {
      addRateLimit.value.showDialog();
    },
  },
  {
    separator: true,
    visible: computed(() => rowData.value.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanDelete")),
  },
  {
    label: "Remove",
    icon: "text-muted fa-solid fa-trash-can fa-sm",
    command: async () => {
      removeDataSink.value.showDialog();
    },
    visible: computed(() => rowData.value.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanDelete")),
  },
]);

const menuButtonClick = (event) => {
  horizontalEllipsis.value = !horizontalEllipsis.value;
  menu.value.toggle(event);
};

const showMenu = (event) => {
  editing.value = true;
};

const hideMenu = async () => {
  horizontalEllipsis.value = false;
  await menu.value.hide();
};

const blurTrigger = () => {
  editing.value = false;
};

const formatCloneDataSink = (dataValue) => {
  const clonedDataSinkObject = _.cloneDeepWith(dataValue);
  clonedDataSinkObject["name"] = "";
  return clonedDataSinkObject;
};

const refresh = async (updateData) => {
  editing.value = false;
  if (updateData && updateData.name && updateData.rateLimit !== undefined) {
    emit("updateRateLimit", updateData);
  } else {
    emit("reloadDataSinks");
  }
};

const formatFlowData = () => {
  let exportableData = JSON.parse(JSON.stringify(rowData.value));
  exportableData = _.pick(exportableData, ["name", "type", "description", "subscribe", "egressAction.name", "egressAction.type", "egressAction.parameters", "egressAction.apiVersion", "egressAction.join"]);
  exportableData["type"] = "DATA_SINK";

  return exportableData;
};

const exportDataSink = () => {
  const link = document.createElement("a");
  const downloadFileName = `${rowData.value.name}_data_sink_export_` + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  const blob = new Blob([JSON.stringify(formatFlowData(), null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};
</script>
