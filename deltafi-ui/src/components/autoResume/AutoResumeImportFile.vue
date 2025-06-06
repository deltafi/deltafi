<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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
  <span>
    <div class="auto-resume-import-file">
      <FileUpload ref="fileUploader" auto mode="basic" choose-label="Import Rules" accept=".json,application/json" :file-limit="1" custom-upload class="p-button-sm p-button-secondary p-button-outlined mx-1" @uploader="preUploadValidation" @click="setOverlayPanelPosition" />
    </div>
    <OverlayPanel ref="errorOverlayPanel" dismissable show-close-icon @hide="clearUploadErrors">
      <Message severity="error" :sticky="true" class="mb-2 mt-0" :closable="false">
        <ul>
          <div v-for="(error, key) in _.uniq(errorsList)" :key="key">
            <li class="text-wrap text-break">{{ error }}</li>
          </div>
        </ul>
      </Message>
    </OverlayPanel>
  </span>
</template>

<script setup>
import useAutoResumeConfiguration from "@/composables/useAutoResumeConfiguration";
import useAutoResumeQueryBuilder from "@/composables/useAutoResumeQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import FileUpload from "@/components/deprecatedPrimeVue/FileUpload.vue";
import Message from "primevue/message";
import OverlayPanel from "primevue/overlaypanel";
import { ref } from "vue";

import _ from "lodash";

const emit = defineEmits(["refreshPage"]);
const { validateAutoResumeFile: validateFile } = useAutoResumeConfiguration();
const { loadResumePolicies } = useAutoResumeQueryBuilder();
const notify = useNotifications();

const fileUploader = ref("");
const validUpload = ref({});
const errorsList = ref([]);
const errorOverlayPanel = ref(null);
const overlayPanelPosition = ref({});

const preUploadValidation = async (request) => {
  for (const file of request.files) {
    const reader = new FileReader();

    reader.readAsText(file);

    reader.onload = function () {
      const uploadNotValid = validateFile(reader.result);

      errorsList.value = [];
      if (uploadNotValid) {
        for (const errorMessages of uploadNotValid) {
          errorsList.value.push(errorMessages.message);
        }
        deleteUploadFile();
        errorOverlayPanel.value.toggle(overlayPanelPosition.value, overlayPanelPosition.value.target);
      } else {
        validUpload.value = reader.result;
        uploadFile(file);
      }
    };
  }
};

const uploadFile = async (file) => {
  const response = await loadResumePolicies(JSON.parse(validUpload.value));
  if (!_.isEmpty(_.get(response, "errors", null))) {
    notify.error(`Upload failed for ${file.name}`, `Removing ${file.name}.`, 4000);
  } else {
    notify.success(`Uploaded ${file.name}`, `Successfully uploaded ${file.name}.`, 4000);
  }
  deleteUploadFile();
  emit("refreshPage");
};

const deleteUploadFile = () => {
  fileUploader.value.files = [];
  fileUploader.value.uploadedFileCount = 0;
};

const clearUploadErrors = () => {
  errorsList.value = [];
};

const setOverlayPanelPosition = (event) => {
  overlayPanelPosition.value = event;
};
</script>

<style>
.auto-resume-import-file {
  .p-fileupload-choose:not(.p-disabled):hover {
    background: rgba(108, 117, 125, 0.04) !important;
    color: #6c757d !important;
    border-color: #6c757d !important;
  }

  .p-fileupload-choose:not(.p-disabled):active {
    background: transparent !important;
    color: #6c757d !important;
    border-color: #6c757d !important;
  }
}
</style>
