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
  <div class="delete-policy-import-file">
    <span class="delete-policy-file-uploader">
      <FileUpload ref="fileUploader" auto mode="basic" choose-label="Import Policies" accept=".json,application/json" :file-limit="1" custom-upload class="p-button-sm p-button-secondary p-button-outlined mx-1" @uploader="preUploadValidation" @click="setOverlayPanelPosition" />
    </span>
    <OverlayPanel ref="errorOverlayPanel" dismissable show-close-icon @hide="clearUploadErrors">
      <Message severity="error" :sticky="true" class="mb-2 mt-0" :closable="false">
        <ul>
          <div v-for="(error, key) in _.uniq(errorsList)" :key="key">
            <li class="text-wrap text-break">{{ error }}</li>
          </div>
        </ul>
      </Message>
    </OverlayPanel>
  </div>
</template>

<script setup>
import useDeletePolicyConfiguration from "@/composables/useDeletePolicyConfiguration";
import useDeletePolicyQueryBuilder from "@/composables/useDeletePolicyQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import FileUpload from "@/components/deprecatedPrimeVue/FileUpload";
import Message from "primevue/message";
import OverlayPanel from "primevue/overlaypanel";
import { defineEmits, ref } from "vue";

import _ from "lodash";

const emit = defineEmits(["reloadDeletePolicies"]);
const { validateDeletePolicyFile: validateFile } = useDeletePolicyConfiguration();
const { loadDeletePolicies } = useDeletePolicyQueryBuilder();
const notify = useNotifications();

const fileUploader = ref("");
const validUpload = ref({});
const errorsList = ref([]);
const errorOverlayPanel = ref(null);
const overlayPanelPosition = ref({});

const preUploadValidation = async (request) => {
  for (let file of request.files) {
    let reader = new FileReader();

    reader.readAsText(file);

    reader.onload = function () {
      let uploadNotValid = validateFile(reader.result);
      errorsList.value = [];
      if (uploadNotValid) {
        for (let errorMessages of uploadNotValid) {
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
  let response = await loadDeletePolicies(JSON.parse(validUpload.value));
  if (!_.isEmpty(_.get(response, "errors", null))) {
    notify.error(`Upload failed for ${file.name}`, `Removing ${file.name}.`, 4000);
  } else {
    notify.success(`Uploaded ${file.name}`, `Successfully uploaded ${file.name}.`, 4000);
  }
  deleteUploadFile();
  emit("reloadDeletePolicies");
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

<style lang="scss">
@import "@/styles/components/deletePolicy/delete-policy-import-file.scss";
</style>
