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
          <span class="ml-2 text-dark">{{ item.label }}</span>
        </a>
      </PermissionedRouterLink>
      <a v-else v-ripple class="flex align-items-center" v-bind="props.action">
        <span :class="item.icon" />
        <span class="ml-2 text-dark">{{ item.label }}</span>
      </a>
    </template>
  </Menu>
  <Button :id="`${data.name}-${data.type}`" ref="optionsButton" type="button" v-tooltip.top="`Options`" @click="menuButtonClick" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" class="mt-n1 mb-n1" />
  <DataSourceRemoveButton ref="removeDataSource" :row-data-prop="data" @reload-data-sources="refresh" />
  <DialogTemplate ref="editDataSource" component-name="dataSources/DataSourceConfigurationDialog" header="Edit Data Source" dialog-width="50vw" :row-data-prop="data" edit-data-source @refresh-page="refresh" />
  <DialogTemplate ref="cloneDataSource" component-name="dataSources/DataSourceConfigurationDialog" header="Create Data Source" dialog-width="50vw" :row-data-prop="formatCloneDataSource(data)" @refresh-page="refresh" />
  <DialogTemplate ref="addRateLimit" component-name="dataSources/RateLimitingForm" :header="`Add Rate Limit: ${data.name}`" dialog-width="35vw" :rest-data-source-name="data['name']" :rate-limit="data['rateLimit']" @close-dialog-template="editing = false" @open-dialog-template="editing = true" @refresh-page="refresh" />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import DataSourceRemoveButton from "@/components/dataSources/DataSourceRemoveButton.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import useNotifications from "@/composables/useNotifications";
import useRateLimiting from "@/composables/useRateLimiting";
import { computed, inject, reactive, ref } from "vue";
import { useRouter } from "vue-router";

import _ from "lodash";

import Button from "primevue/button";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const overlayPanelPosition = ref({});
const vRipple = Ripple;

const router = useRouter();
const { removeRestDataSourceRateLimit } = useRateLimiting();
const notify = useNotifications();
const emit = defineEmits(["reloadDataSources"]);
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
    label: "View Pipeline",
    icon: "text-muted pi pi-sitemap",
    visible: computed(() => hasPermission("FlowView")),
    command: () => {
      router.push(`/pipeline/${data.type}/${data.name}`);
    },
  },
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
    visible: computed(() => hasPermission("FlowPlanCreate") && !data.flowStatus?.placeholder),
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
    label: "Add Rate Limit",
    icon: "text-muted fa-solid fa-plus",
    visible: computed(() => _.isEqual(data.type, "REST_DATA_SOURCE")),
    command: () => {
      addRateLimit.value.showDialog();
    },
  },
  {
    label: "Remove Rate Limit",
    icon: "text-muted fa-solid fa-xmark",
    visible: computed(() => _.isEqual(data.type, "REST_DATA_SOURCE") && !_.isEmpty(data.rateLimit)),
    command: () => {
      deleteRateLimit();
    },
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
    exportableData = _.pick(exportableData, ["name", "type", "description", "metadata", "annotationConfig", "topic", "errorMessageRegex", "sourceMetadataPrefix", "includeSourceMetadataRegex"]);
  }

  return exportableData;
};

const deleteRateLimit = async () => {
  const response = await removeRestDataSourceRateLimit(data.name);
  if (response) {
    notify.success("Removed Rate Limit", `Removed Rate Limit from Data Source ${data.name}.`, 3000);
  } else {
    notify.error("Error Removing Rate Limit", `Error removing Rate Limit from Data Source ${data.name}.`, 3000);
  }
  refresh();
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
