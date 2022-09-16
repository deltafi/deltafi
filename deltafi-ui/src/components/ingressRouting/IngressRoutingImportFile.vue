<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div class="ingress-routing-body">
    <div class="mb-3 row">
      <div class="col-12">
        <FileUpload ref="fileUploader" choose-label="Add Files" cancel-label="Clear" accept=".json,application/JSON" :file-limit="1" :custom-upload="true" @select="beforeUploadTest" @uploader="onUpload">
          <template #empty>
            <div v-if="!_.isEmpty(errorsList)" class="pt-2">
              <Message severity="error" :sticky="true" class="mb-2 mt-0">
                <ul>
                  <div v-for="(error, key) in errorsList" :key="key">
                    <li class="text-wrap text-break">{{ error }}</li>
                  </div>
                </ul>
              </Message>
            </div>
            <i class="ml-3">Drag and drop files to here to upload.</i>
          </template>
        </FileUpload>
      </div>
    </div>
  </div>
</template>

<script setup>
import useIngressRoutingConfiguration from "@/composables/useIngressRoutingConfiguration";
import useIngressRoutingQueryBuilder from "@/composables/useIngressRoutingQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import FileUpload from "primevue/fileupload";
import Message from "primevue/message";
import { defineEmits, defineProps, reactive, ref } from "vue";

import _ from "lodash";

const emit = defineEmits(["reloadIngressRoutes"]);
const { validateIngressRouteFile } = useIngressRoutingConfiguration();
const { loadFlowAssignmentRules } = useIngressRoutingQueryBuilder();
const notify = useNotifications();

const props = defineProps({
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const { closeDialogCommand } = reactive(props);

const fileUploader = ref("");
const validUpload = ref({});

const errorsList = ref([]);

const onUpload = (event) => {
  uploadFiles(event);
};

const beforeUploadTest = async (request) => {
  for (let file of request.files) {
    let reader = new FileReader();

    reader.readAsText(file);

    reader.onload = function () {
      let uploadNotValid = validateIngressRouteFile(reader.result);
      errorsList.value = [];
      if (uploadNotValid) {
        for (let errorMessages of uploadNotValid) {
          errorsList.value.push(errorMessages.message);
        }
        fileUploader.value.files = [];
        notify.error(`Validation Errors ${file.name}`, `Removing ${file.name} from upload list.`, 4000);
      } else {
        validUpload.value = reader.result;
      }
    };
  }
  return request;
};

const uploadFiles = async (event) => {
  for (let file of event.files) {
    await loadFlowAssignmentRules(JSON.parse(validUpload.value));
    notify.success(`Uploaded ${file.name}`, `Successfully uploaded ${file.name}.`, 4000);
    fileUploader.value.files = [];
    closeDialogCommand.command();
    emit("reloadIngressRoutes");
  }
};
</script>

<style lang="scss">
@import "@/styles/components/ingress-routing-upload-file.scss";
</style>
