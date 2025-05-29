<template>
  <Menu
    ref="menu"
    id="DataSinkPageHeaderMenu"
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
  <Button id="DataSinkPageHeaderOptions" ref="optionsButton" type="button" v-tooltip.left="`Options`" @click="showMenu" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" />
  <DialogTemplate ref="addDataSink" component-name="dataSinks/DataSinkConfigurationDialog" header="Add Data Sink" dialog-width="50vw" :row-data-prop="{}" @refresh-page="refresh" />
  <FileUpload
    v-show="false"
    auto
    ref="dataSinksFileUploader"
    mode="basic"
    accept=".json,application/json"
    :file-limit="1"
    custom-upload
    @uploader="preUploadValidation"
    :pt="{
      input: {
        id: 'dataSinksFileUploaderButton',
      },
    }"
  />
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import useDataSink from "@/composables/useDataSink";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import FileUpload from "primevue/fileupload";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const emit = defineEmits(["reloadDataSinks"]);
const { saveDataSinkPlan } = useDataSink();
const notify = useNotifications();
const vRipple = Ripple;
const menu = ref();
const addDataSink = ref(null);
const optionsButton = ref(null);
const dataSinksFileUploader = ref(null);
const horizontalEllipsis = ref(false);
const hasPermission = inject("hasPermission");

const props = defineProps({
  exportDataSinks: {
    type: Object,
    required: true,
  },
});

const items = ref([
  {
    label: "Add Data Sink",
    icon: "pi pi-plus",
    visible: computed(() => hasPermission("FlowPlanCreate")),
    command: () => {
      addDataSink.value.showDialog();
    },
  },
  {
    label: "Import Data Sinks",
    icon: "text-muted fas fa-upload fa-fw",
    visible: computed(() => hasPermission("FlowPlanCreate")),
    command: () => {
      document.getElementById("dataSinksFileUploaderButton").click();
    },
  },
  {
    label: "Export Data Sinks",
    icon: "text-muted fas fa-download fa-fw",
    visible: true,
    command: () => {
      exportDataSinks();
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
  emit("reloadDataSinks");
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

      if (_.has(validJson, "dataSinks")) {
        for (const dataSink of validJson.dataSinks) {
          uploadDataSink(dataSink);
        }
      } else if (_.has(validJson, "type")) {
        uploadDataSink(validJson, true);
      } else {
        notify.error(`Invalid file format`, 4000);
      }
    };
  }
};

const uploadDataSink = async (data) => {
  let response = await saveDataSinkPlan(data);
  response = response.data.saveDataSinkPlan;

  if (_.isEmpty(_.get(response, "name", null))) {
    notify.error(`Data Sink Import Failed`, `Failed to import data sink ${data.name}.`, 4000);
  } else {
    notify.success(`Imported Successful`, `Successfully imported data sink ${data.name}.`, 4000);
  }
};

const deleteUploadFile = () => {
  dataSinksFileUploader.value.files = [];
};

const exportDataSinks = () => {
  const link = document.createElement("a");
  const downloadFileName = `data_sinks_export_` + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  const blob = new Blob([JSON.stringify(props.exportDataSinks, null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};
</script>
