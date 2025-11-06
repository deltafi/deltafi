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
      <a v-ripple class="flex align-items-center" v-bind="props.action">
        <span :class="item.icon" />
        <span class="ml-2 text-dark">{{ item.label }}</span>
      </a>
    </template>
  </Menu>
  <Button :id="`${data.name}-${data.type}`" ref="optionsButton" type="button" v-tooltip.top="`Options`" @click="showMenu" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" class="mt-n1 mb-n1" />
  <LookupTableRemoveButton ref="removeLookupTable" :row-data-prop="data" @refresh-page="refresh" />
  <DialogTemplate ref="addLookupTableRow" component-name="lookupTable/LookupTableAddRow" header="Add Lookup Table Row" dialog-width="30vw" :row-data-prop="data" edit-lookup-table @refresh-page="refresh" />
  <DialogTemplate ref="cloneLookupTable" component-name="lookupTable/LookupTableConfigurationDialog" header="Create Lookup Table" dialog-width="50vw" :row-data-prop="formatCloneLookupTable(data)" @refresh-page="refresh" />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import LookupTableRemoveButton from "@/components/lookupTable/LookupTableRemoveButton.vue";
import { computed, inject, reactive, ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const hasPermission = inject("hasPermission");
const emit = defineEmits(["reloadLookupTables"]);
const vRipple = Ripple;
const addLookupTableRow = ref(null);
const cloneLookupTable = ref(null);
const removeLookupTable = ref(null);
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

const items = ref([
  {
    label: "Add Row",
    icon: "text-muted pi pi-pencil",
    visible: computed(() => hasPermission("LookupTableUpdate")),
    command: () => {
      addLookupTableRow.value.showDialog();
    },
  },
  {
    label: "Clone",
    icon: "text-muted pi pi-clone",
    visible: computed(() => hasPermission("LookupTableCreate")),
    command: () => {
      cloneLookupTable.value.showDialog();
    },
  },
  {
    label: "Export",
    icon: "text-muted pi pi-download",
    visible: computed(() => hasPermission("LookupTableRead")),
    command: () => {
      exportLookupTable();
    },
  },
  {
    separator: true,
    visible: computed(() => hasPermission("LookupTableDelete")),
  },
  {
    label: "Remove",
    icon: "text-muted fa-solid fa-trash-can",
    command: async () => {
      removeLookupTable.value.showDialog();
    },
    visible: computed(() => hasPermission("LookupTableDelete")),
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

const formatCloneLookupTable = (data) => {
  const clonedLookupTableObject = _.cloneDeepWith(data);
  clonedLookupTableObject["name"] = "";
  return clonedLookupTableObject;
};

const refresh = async () => {
  emit("reloadLookupTables");
};

const formatData = () => {
  let exportableData = JSON.parse(JSON.stringify(data));
  exportableData = _.pick(exportableData, ["name", "columns", "keyColumns", "serviceBacked", "backingServiceActive", "pullThrough", "refreshDuration", "lastRefresh"]);

  return exportableData;
};

const exportLookupTable = () => {
  const link = document.createElement("a");
  const downloadFileName = `${data.name}_lookup_table_row_export_` + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  const blob = new Blob([JSON.stringify(formatData(), null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};
</script>
