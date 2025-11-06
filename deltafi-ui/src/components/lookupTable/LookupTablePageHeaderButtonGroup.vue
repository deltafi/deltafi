<template>
  <Menu
    ref="menu"
    id="LookupTablePageHeaderMenu"
    :model="items"
    :popup="true"
    @hide="hideMenu"
    :pt="{
      action: {
        class: 'py-1',
      },
    }"
    @click="hideMenu"
  >
    <template #item="{ item, props }">
      <a v-ripple class="flex align-items-center" v-bind="props.action">
        <span :class="item.icon" />
        <span class="ml-2 text-dark">{{ item.label }}</span>
      </a>
    </template>
  </Menu>
  <Button id="LookupTablePageHeaderOptions" ref="optionsButton" type="button" v-tooltip.left="`Options`" @click="showMenu" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" />
  <DialogTemplate ref="addLookupTable" component-name="lookupTable/LookupTableConfigurationDialog" header="Add Lookup Table" dialog-width="50vw" :row-data-prop="{}" @refresh-page="refresh" />
  <FileUpload
    v-show="false"
    auto
    ref="lookupTableFileUploader"
    mode="basic"
    accept=".json,application/json"
    :file-limit="1"
    custom-upload
    @uploader="preUploadValidation"
    :pt="{
      input: {
        id: 'lookupTableFileUploaderButton',
      },
    }"
  />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import useLookupTable from "@/composables/useLookupTable";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import FileUpload from "primevue/fileupload";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const emit = defineEmits(["reloadLookupTables"]);
const { createLookupTable } = useLookupTable();
const notify = useNotifications();
const vRipple = Ripple;
const menu = ref();
const addLookupTable = ref(null);
const optionsButton = ref(null);
const lookupTableFileUploader = ref(null);
const horizontalEllipsis = ref(false);
const hasPermission = inject("hasPermission");

const props = defineProps({
  exportLookupTables: {
    type: Object,
    required: true,
  },
});

const items = ref([
  {
    label: "Add Lookup Table",
    icon: "pi pi-plus",
    visible: computed(() => hasPermission("FlowPlanCreate")),
    command: () => {
      addLookupTable.value.showDialog();
    },
  },
  {
    label: "Import Lookup Tables",
    icon: "text-muted fas fa-upload fa-fw",
    visible: computed(() => hasPermission("FlowPlanCreate")),
    command: () => {
      document.getElementById("lookupTableFileUploaderButton").click();
    },
  },
  {
    label: "Export Lookup Tables",
    icon: "text-muted fas fa-download fa-fw",
    visible: true,
    command: () => {
      exportLookupTables();
    },
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

const refresh = async () => {
  emit("reloadLookupTables");
};

const preUploadValidation = async (request) => {
  for (const file of request.files) {
    const reader = new FileReader();

    reader.readAsText(file);

    reader.onload = function () {
      if (!JSON.parse(reader.result)) {
        notify.error(`Invalid file format`, 4000);
        deleteUploadFile();
      }

      const validJson = JSON.parse(reader.result);

      if (Array.isArray(validJson)) {
        for (const lookupTable of validJson) {
          uploadLookupTable(lookupTable);
        }
      } else {
        uploadLookupTable(validJson);
      }
    };
  }
};

const uploadLookupTable = async (data) => {
  const response = await createLookupTable(data);

  if (!_.isEmpty(response.errors) || _.isEmpty(response.data) || !_.isEmpty(response.data.createLookupTable.errors)) {
    notify.error(`Lookup Table Import Failed`, `Failed to import lookup table ${data.name}.`, 4000);
  } else {
    notify.success(`Imported Successful`, `Successfully imported lookup table ${data.name}.`, 4000);
    refresh();
  }
};

const deleteUploadFile = () => {
  lookupTableFileUploader.value.files = [];
};

const formatExportData = () => {
  let formattedData = _.cloneDeep(props.exportLookupTables);

  formattedData = _.forEach(formattedData.lookupTables, (table) => {
    _.unset(table, "totalRows");
  });

  return formattedData;
};

const exportLookupTables = () => {
  const link = document.createElement("a");
  const downloadFileName = `lookup_table_export_` + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  const blob = new Blob([JSON.stringify(formatExportData(), null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};
</script>
