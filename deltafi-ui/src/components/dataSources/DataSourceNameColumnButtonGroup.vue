<template>
  <Menu
    ref="menu"
    :id="`overlay_menu_` + data.name + `_` + data.type"
    :model="items"
    :popup="true"
    @hide="hideMenu"
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
          <span class="ml-2 text-dark">{{ item.label }}</span>
        </a>
      </PermissionedRouterLink>
      <a v-else v-ripple class="flex align-items-center" v-bind="props.action">
        <span :class="item.icon" />
        <span class="ml-2 text-dark">{{ item.label }}</span>
      </a>
    </template>
  </Menu>
  <Button :id="`${data.name}-${data.type}`" ref="optionsButton" type="button" v-tooltip.top="`Options`" @click="showMenu" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" class="mt-n1 mb-n1" />
  <DataSourceRemoveButton ref="removeDataSource" :row-data-prop="data" @reload-data-sources="refresh" />
  <DialogTemplate ref="editDataSource" component-name="dataSources/DataSourceConfigurationDialog" header="Edit Data Source" dialog-width="50vw" :row-data-prop="data" edit-data-source @refresh-page="refresh" />
  <DialogTemplate ref="cloneDataSource" component-name="dataSources/DataSourceConfigurationDialog" header="Create Data Source" dialog-width="50vw" :row-data-prop="formatCloneDataSource(data)" @refresh-page="refresh" />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import DataSourceRemoveButton from "@/components/dataSources/DataSourceRemoveButton.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import { computed, inject, reactive, ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const overlayPanelPosition = ref({});
const vRipple = Ripple;

const emit = defineEmits(["reloadDataSources"]);
const hasPermission = inject("hasPermission");
const editDataSource = ref(null);
const cloneDataSource = ref(null);
const removeDataSource = ref(null);
const viewDataSourcePlugin = ref(null);
const optionsButton = ref(null);
const horizontalEllipsis = ref(false);
const menu = ref();

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: data } = reactive(props);

const concatMvnCoordinates = (sourcePlugin) => {
  return sourcePlugin.groupId + ":" + sourcePlugin.artifactId + ":" + sourcePlugin.version;
};
const items = ref([
  {
    label: "Edit",
    icon: "text-muted pi pi-pencil",
    visible: computed(() => data.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanCreate")),
    command: () => {
      editDataSource.value.showDialog();
    },
  },
  {
    label: "Clone",
    icon: "text-muted pi pi-clone",
    visible: computed(() => hasPermission("FlowPlanCreate")),
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
    icon: "text-muted fas fa-plug fa-rotate-90 fa-fw align-items-center",
    visible: computed(() => hasPermission("FlowPlanCreate")),
    route: "/config/plugins/",
  },
  {
    separator: true,
    visible: computed(() => data.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanDelete")),
  },
  {
    label: "Remove",
    icon: "text-muted fa-solid fa-trash-can",
    command: async () => {
      removeDataSource.value.showDialog();
    },
    visible: computed(() => data.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanDelete")),
  },
]);

const showMenu = (event) => {
  overlayPanelPosition.value = event;
  horizontalEllipsis.value = !horizontalEllipsis.value;
  menu.value.toggle(event);
};

const hideMenu = async () => {
  horizontalEllipsis.value = false;
  menu.value.hide();
};

const formatCloneDataSource = (data) => {
  const clonedDataSourceObject = _.cloneDeepWith(data);
  clonedDataSourceObject["name"] = "";
  return clonedDataSourceObject;
};

const refresh = async () => {
  emit("reloadDataSources");
};

const formatFlowData = () => {
  let exportableData = JSON.parse(JSON.stringify(data));

  if (_.isEqual(exportableData.type, "REST_DATA_SOURCE")) {
    exportableData = _.pick(exportableData, ["name", "type", "description", "metadata", "annotationConfig", "topic"]);
  }

  if (_.isEqual(exportableData.type, "TIMED_DATA_SOURCE")) {
    exportableData = _.pick(exportableData, ["name", "type", "description", "metadata", "topic", "cronSchedule", "timedIngressAction.name", "timedIngressAction.type", "timedIngressAction.parameters", "timedIngressAction.apiVersion", "timedIngressAction.join", "annotationConfig.annotations", "annotationConfig.metadataPatterns", "annotationConfig.discardPrefix"]);
  }

  if (_.isEqual(exportableData.type, "ON_ERROR_DATA_SOURCE")) {
    exportableData = _.pick(exportableData, ["name", "type", "description", "metadata", "annotationConfig", "topic", "errorMessageRegex"]);
  }

  return exportableData;
};

const exportDataSource = () => {
  const link = document.createElement("a");
  const downloadFileName = `${data.name}_${data.type}_export_` + new Date(Date.now()).toLocaleDateString();
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
