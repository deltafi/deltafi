<template>
  <Menu
    ref="menu"
    id="DataSourcePageHeaderMenu"
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
  <Button id="DataSourcePageHeaderOptions" ref="optionsButton" type="button" v-tooltip.left="`Options`" @click="showMenu" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" />
  <DialogTemplate ref="addDataSource" component-name="dataSources/DataSourceConfigurationDialog" header="Add Data Source" dialog-width="50vw" :row-data-prop="{}" @refresh-page="refresh" />
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
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import useDataSource from "@/composables/useDataSource";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import FileUpload from "primevue/fileupload";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const emit = defineEmits(["reloadDataSources"]);
const hasPermission = inject("hasPermission");
const { saveTimedDataSourcePlan, saveRestDataSourcePlan } = useDataSource();
const notify = useNotifications();
const vRipple = Ripple;
const menu = ref();
const addDataSource = ref(null);
const optionsButton = ref(null);
const dataSourceFileUploader = ref(null);
const horizontalEllipsis = ref(false);

const props = defineProps({
  exportDataSources: {
    type: Object,
    required: true,
  },
});

const items = ref([
  {
    label: "Add Data Sources",
    icon: "pi pi-plus",
    visible: computed(() => hasPermission("FlowPlanCreate")),
    command: () => {
      addDataSource.value.showDialog();
    },
  },
  {
    label: "Import Data Sources",
    icon: "text-muted fas fa-upload fa-fw",
    visible: computed(() => hasPermission("FlowPlanCreate")),
    command: () => {
      document.getElementById("dataSourceFileUploaderButton").click();
    },
  },
  {
    label: "Export Data Sources",
    icon: "text-muted fas fa-download fa-fw",
    visible: true,
    command: () => {
      exportDataSources();
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
