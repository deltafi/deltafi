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
    :id="`overlay_menu_` + data.name + `_` + data.type"
    :key="`overlay_menu_` + data.name + `_` + data.type"
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
      <PermissionedRouterLink v-if="item.route" ref="viewDataSourcePlugin" :disabled="!$hasPermission('PluginsView')" :to="{ path: 'plugins/' + concatMvnCoordinates(data.sourcePlugin) }">
        <a
          v-ripple
          v-tooltip.top="{
            value: concatMvnCoordinates(data.sourcePlugin),
          }"
          class="flex align-items-center"
          v-bind="props.action"
        >
          <span :class="item.icon" />
          <span class="ml-2">{{ item.label }}</span>
        </a>
      </PermissionedRouterLink>
      <a v-else v-ripple class="flex align-items-center" v-bind="props.action">
        <span :class="item.icon" />
        <span class="ml-2">{{ item.label }}</span>
      </a>
    </template>
  </Menu>
  <i v-if="hasRateLimit" v-tooltip.top="rateLimitTooltip" class="fa-solid fa-stopwatch fa-lg text-info cursor-pointer mr-2" role="button" tabindex="0" @click="addRateLimit.showDialog()" @keydown.enter="addRateLimit.showDialog()" />
  <Button :id="`${data.name}-${data.type}`" ref="optionsButton" type="button" v-tooltip.top="`Options`" @click="menuButtonClick" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" class="mt-n1 mb-n1" />
  <DataSourceRemoveButton ref="removeDataSource" :row-data-prop="data" @reload-data-sources="refresh" />
  <DialogTemplate ref="editDataSource" component-name="dataSources/DataSourceConfigurationDialog" header="Edit Data Source" dialog-width="50vw" :row-data-prop="data" edit-data-source @refresh-page="refresh" />
  <DialogTemplate ref="cloneDataSource" component-name="dataSources/DataSourceConfigurationDialog" header="Create Data Source" dialog-width="50vw" :row-data-prop="formatCloneDataSource(data)" @refresh-page="refresh" />
  <DialogTemplate ref="addRateLimit" component-name="dataSources/RateLimitingForm" :header="rateLimitDialogHeader" dialog-width="35vw" dismissable-mask :rest-data-source-name="data['name']" :rate-limit="data['rateLimit']" @close-dialog-template="editing = false" @open-dialog-template="editing = true" @refresh-page="refresh" />
  <CronScheduleEditDialog v-model:visible="cronEditVisible" :data="data" @reload-data-sources="refresh" @close="cronEditVisible = false" />
</template>

<script setup>
import CronScheduleEditDialog from "@/components/dataSources/CronScheduleEditDialog.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import DataSourceRemoveButton from "@/components/dataSources/DataSourceRemoveButton.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import { computed, inject, ref, toRef } from "vue";
import { useRouter } from "vue-router";

import _ from "lodash";

import Button from "primevue/button";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const overlayPanelPosition = ref({});
const vRipple = Ripple;

const router = useRouter();
const emit = defineEmits(["reloadDataSources", "updateRateLimit"]);
const hasPermission = inject("hasPermission");
const editing = inject("isEditing");
const editDataSource = ref(null);
const cloneDataSource = ref(null);
const removeDataSource = ref(null);
const addRateLimit = ref(null);
const viewDataSourcePlugin = ref(null);
const optionsButton = ref(null);
const horizontalEllipsis = ref(false);
const menu = ref();
const cronEditVisible = ref(false);

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const data = toRef(props, "rowDataProp");

const hasRateLimit = computed(() => _.isEqual(data.value.type, "REST_DATA_SOURCE") && !_.isEmpty(data.value.rateLimit));

const rateLimitTooltip = computed(() => {
  if (!data.value.rateLimit) return "";
  const { maxAmount, unit, durationSeconds } = data.value.rateLimit;
  return `Rate limited: ${maxAmount} ${unit.toLowerCase()} every ${durationSeconds}s`;
});

const rateLimitDialogHeader = computed(() => {
  return hasRateLimit.value ? `Edit Rate Limit: ${data.value.name}` : `Add Rate Limit: ${data.value.name}`;
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
      router.push(`/pipeline/${data.value.type}/${data.value.name}`);
    },
  },
  {
    label: "Edit",
    icon: "text-muted pi pi-pencil",
    visible: computed(() => data.value.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanCreate")),
    command: () => {
      editDataSource.value.showDialog();
    },
  },
  {
    label: "Clone",
    icon: "text-muted pi pi-clone",
    visible: computed(() => hasPermission("FlowPlanCreate") && !data.value.flowStatus?.placeholder),
    command: () => {
      cloneDataSource.value.showDialog();
    },
  },
  {
    label: "Export",
    icon: "text-muted pi pi-download",
    visible: computed(() => hasPermission("FlowPlanCreate")),
    command: () => {
      exportDataSource();
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
    visible: computed(() => _.isEqual(data.value.type, "REST_DATA_SOURCE")),
    command: () => {
      addRateLimit.value.showDialog();
    },
  },
  {
    label: "Edit Cron Schedule",
    icon: "text-muted fa-solid fa-clock",
    visible: computed(() => _.isEqual(data.value.type, "TIMED_DATA_SOURCE") && hasPermission("FlowUpdate")),
    command: () => {
      cronEditVisible.value = true;
    },
  },
  {
    separator: true,
    visible: computed(() => data.value.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanDelete")),
  },
  {
    label: "Remove",
    icon: "text-muted fa-solid fa-trash-can fa-sm",
    command: async () => {
      removeDataSource.value.showDialog();
    },
    visible: computed(() => data.value.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanDelete")),
  },
]);

const menuButtonClick = (event) => {
  overlayPanelPosition.value = event;
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

const formatCloneDataSource = (data) => {
  const clonedDataSourceObject = _.cloneDeepWith(data);
  clonedDataSourceObject["name"] = "";
  return clonedDataSourceObject;
};

const refresh = async (updateData) => {
  editing.value = false;
  if (updateData && updateData.name && updateData.rateLimit !== undefined) {
    emit("updateRateLimit", updateData);
  } else {
    emit("reloadDataSources", true);
  }
};

const formatFlowData = () => {
  let exportableData = JSON.parse(JSON.stringify(data.value));

  if (_.isEqual(exportableData.type, "REST_DATA_SOURCE")) {
    exportableData = _.pick(exportableData, ["name", "type", "description", "metadata", "annotationConfig", "topic"]);
  }

  if (_.isEqual(exportableData.type, "TIMED_DATA_SOURCE")) {
    exportableData = _.pick(exportableData, ["name", "type", "description", "metadata", "topic", "cronSchedule", "timedIngressAction.name", "timedIngressAction.type", "timedIngressAction.parameters", "timedIngressAction.apiVersion", "timedIngressAction.join", "annotationConfig.annotations", "annotationConfig.metadataPatterns", "annotationConfig.discardPrefix"]);
  }

  if (_.isEqual(exportableData.type, "ON_ERROR_DATA_SOURCE")) {
    exportableData = _.pick(exportableData, ["name", "type", "description", "metadata", "annotationConfig", "topic", "errorMessageRegex", "sourceMetadataPrefix", "includeSourceMetadataRegex"]);
  }

  return exportableData;
};

const exportDataSource = () => {
  const link = document.createElement("a");
  const downloadFileName = `${data.value.name}_${data.value.type}_export_` + new Date(Date.now()).toLocaleDateString();
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
