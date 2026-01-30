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
  <div class="d-flex">
    <Button label="Import" icon="fas fa-upload fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" @click="importDataSources" />
    <Button label="Export" icon="fas fa-download fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" @click="exportDataSources" />
    <Button v-has-permission:FlowPlanCreate label="Add" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" @click="showAddDataSource" />

    <FileUpload
      v-show="false"
      auto
      ref="dataSourceFileUploader"
      mode="basic"
      accept=".json,application/json"
      :file-limit="1"
      custom-upload
      @uploader="preUploadValidation"
      :pt="{
        input: {
          id: 'dataSourceFileUploaderButton',
        },
      }"
    />
    <DialogTemplate ref="addDataSource" component-name="dataSources/DataSourceConfigurationDialog" header="Add Data Source" dialog-width="50vw" :row-data-prop="{}" @refresh-page="refresh" />
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import useDataSource from "@/composables/useDataSource";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import FileUpload from "primevue/fileupload";
import Ripple from "primevue/ripple";

const emit = defineEmits(["reloadDataSources"]);
const hasPermission = inject("hasPermission");
const { saveTimedDataSourcePlan, saveRestDataSourcePlan, saveOnErrorDataSourcePlan } = useDataSource();
const notify = useNotifications();
const menu = ref();
const addDataSource = ref(null);
const dataSourceFileUploader = ref(null);
const horizontalEllipsis = ref(false);

const props = defineProps({
  exportDataSources: {
    type: Object,
    required: true,
  },
});


const showAddDataSource = () => {
  addDataSource.value.showDialog();
};

const importDataSources = () => {
  document.getElementById("dataSourceFileUploaderButton").click();
};

const showMenu = (event) => {
  horizontalEllipsis.value = !horizontalEllipsis.value;
  menu.value.toggle(event);
};

const hideMenu = async () => {
  horizontalEllipsis.value = false;
  menu.value.hide();
};

const refresh = async () => {
  emit("reloadDataSources");
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

      if (_.has(validJson, "restDataSources") || _.has(validJson, "timedDataSources")) {
        if (_.has(validJson, "restDataSources")) {
          for (const restDataSource of validJson.restDataSources) {
            uploadDataSource(restDataSource, true);
          }
        }

        if (_.has(validJson, "timedDataSources")) {
          for (const timedDataSource of validJson.timedDataSources) {
            uploadDataSource(timedDataSource, false);
          }
        }
      } else if (_.has(validJson, "type")) {
        if (_.isEqual(validJson.type, "REST_DATA_SOURCE")) {
          uploadDataSource(validJson, true);
        }
        if (_.isEqual(validJson.type, "TIMED_DATA_SOURCE")) {
          uploadDataSource(validJson, false);
        }
      } else {
        notify.error(`Invalid file format`, 4000);
      }
    };
  }
};

const uploadDataSource = async (data, isRestDataSource) => {
  let response = null;
  if (isRestDataSource) {
    response = await saveRestDataSourcePlan(data);
    response = response.data.saveRestDataSourcePlan;
  } else {
    response = await saveTimedDataSourcePlan(data);
    response = response.data.saveTimedDataSourcePlan;
  }

  if (_.isEmpty(_.get(response, "name", null))) {
    notify.error(`Data Source Import Failed`, `Failed to import data source ${data.name}.`, 4000);
  } else {
    notify.success(`Imported Successful`, `Successfully imported data source ${data.name}.`, 4000);
  }
};

const deleteUploadFile = () => {
  dataSourceFileUploader.value.files = [];
};

const exportDataSources = () => {
  const link = document.createElement("a");
  const downloadFileName = `data_sources_export_` + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  const blob = new Blob([JSON.stringify(props.exportDataSources, null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};
</script>
