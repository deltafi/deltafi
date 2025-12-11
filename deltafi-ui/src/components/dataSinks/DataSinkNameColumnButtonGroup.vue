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
      <PermissionedRouterLink v-if="item.route" ref="viewDataSinkPlugin" :disabled="!$hasPermission('PluginsView')" :to="{ path: 'plugins/' + concatMvnCoordinates(data.sourcePlugin) }">
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
  <DataSinkRemoveButton ref="removeDataSink" :row-data-prop="data" @reload-data-sinks="refresh" />
  <DialogTemplate ref="editDataSink" component-name="dataSinks/DataSinkConfigurationDialog" header="Edit Data Sink" dialog-width="50vw" :row-data-prop="data" edit-data-sink @refresh-page="refresh" />
  <DialogTemplate ref="cloneDataSink" component-name="dataSinks/DataSinkConfigurationDialog" header="Create Data Sink" dialog-width="50vw" :row-data-prop="formatCloneDataSink(data)" @refresh-page="refresh" />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import DataSinkRemoveButton from "@/components/dataSinks/DataSinkRemoveButton.vue";
import PermissionedRouterLink from "@/components/PermissionedRouterLink.vue";
import { computed, inject, reactive, ref } from "vue";
import { useRouter } from "vue-router";

import _ from "lodash";

import Button from "primevue/button";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const router = useRouter();
const hasPermission = inject("hasPermission");
const emit = defineEmits(["reloadDataSinks"]);
const vRipple = Ripple;
const editDataSink = ref(null);
const cloneDataSink = ref(null);
const removeDataSink = ref(null);
const viewDataSinkPlugin = ref(null);
const optionsButton = ref(null);
const horizontalEllipsis = ref(false);

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: true,
  },
});

const { rowDataProp: data } = reactive(props);

const menu = ref();

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
      editDataSink.value.showDialog();
    },
  },
  {
    label: "Clone",
    icon: "text-muted pi pi-clone",
    visible: computed(() => hasPermission("FlowPlanCreate")),
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
      removeDataSink.value.showDialog();
    },
    visible: computed(() => data.sourcePlugin.artifactId === "system-plugin" && hasPermission("FlowPlanDelete")),
  },
]);

const showMenu = (event) => {
  horizontalEllipsis.value = !horizontalEllipsis.value;
  menu.value.toggle(event);
};

const hideMenu = async () => {
  horizontalEllipsis.value = false;
  menu.value.hide();
};

const formatCloneDataSink = (data) => {
  const clonedDataSinkObject = _.cloneDeepWith(data);
  clonedDataSinkObject["name"] = "";
  return clonedDataSinkObject;
};

const refresh = async () => {
  emit("reloadDataSinks");
};

const formatFlowData = () => {
  let exportableData = JSON.parse(JSON.stringify(data));
  exportableData = _.pick(exportableData, ["name", "type", "description", "subscribe", "egressAction.name", "egressAction.type", "egressAction.parameters", "egressAction.apiVersion", "egressAction.join"]);
  exportableData["type"] = "DATA_SINK";

  return exportableData;
};

const exportDataSink = () => {
  const link = document.createElement("a");
  const downloadFileName = `${data.name}_data_sink_export_` + new Date(Date.now()).toLocaleDateString();
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
