<template>
  <div class="d-flex">
    <Button label="Import" icon="fas fa-upload fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" @click="importDataSinks" />
    <Button label="Export" icon="fas fa-download fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" @click="exportDataSinks" />
    <Button v-has-permission:FlowPlanCreate label="Add" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" @click="showAddDataSink" />

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
    <DialogTemplate ref="addDataSink" component-name="dataSinks/DataSinkConfigurationDialog" header="Add Data Sink" dialog-width="50vw" :row-data-prop="{}" @refresh-page="refresh" />
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import useDataSink from "@/composables/useDataSink";
import useNotifications from "@/composables/useNotifications";
import { ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import FileUpload from "primevue/fileupload";

const emit = defineEmits(["reloadDataSinks"]);
const { saveDataSinkPlan } = useDataSink();
const notify = useNotifications();
const addDataSink = ref(null);
const dataSinksFileUploader = ref(null);

const props = defineProps({
  exportDataSinks: {
    type: Object,
    required: true,
  },
});

const showAddDataSink = () => {
  addDataSink.value.showDialog();
};

const importDataSinks = () => {
  document.getElementById("dataSinksFileUploaderButton").click();
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
