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
  <div class="install-plugin-configuration-body">
    <div class="install-plugin-panel">
      <div class="pb-0">
        <div v-if="!_.isEmpty(errorsList)" class="pt-2">
          <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
            <ul>
              <div v-for="(error, key) in errorsList" :key="key">
                <li class="text-wrap text-break">{{ error }}</li>
              </div>
            </ul>
          </Message>
        </div>
        <dl>
          <dt>{{ pluginConfigurationMap.get("groupId").header }}</dt>
          <dd>
            <InputText v-model="selectedGroupId" :placeholder="pluginConfigurationMap.get('groupId').placeholder" :disabled="pluginConfigurationMap.get('groupId').disabled" class="inputWidth" />
          </dd>
          <dt>{{ pluginConfigurationMap.get("artifactId").header }}</dt>
          <dd>
            <InputText v-model="selectedArtifactId" :placeholder="pluginConfigurationMap.get('artifactId').placeholder" :disabled="pluginConfigurationMap.get('artifactId').disabled" class="inputWidth" />
          </dd>
          <dt>{{ pluginConfigurationMap.get("version").header }}</dt>
          <dd>
            <InputText v-model="selectedVersion" :placeholder="pluginConfigurationMap.get('version').placeholder" :disabled="pluginConfigurationMap.get('version').disabled" class="inputWidth" />
          </dd>
        </dl>
      </div>
    </div>
    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Submit" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import usePlugins from "@/composables/usePlugins";
import { useMounted } from "@vueuse/core";
import { defineEmits, defineProps, nextTick, onMounted, reactive, ref } from "vue";

import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import _ from "lodash";

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: Object,
  },
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const { installPlugin, uninstallPlugin } = usePlugins();

const rowdata = reactive(JSON.parse(JSON.stringify(props.rowDataProp)));
const { closeDialogCommand } = reactive(props);
const emit = defineEmits(["reloadPlugins"]);
const isMounted = ref(useMounted());

const errorsList = ref([]);

const pluginConfigurationMap = new Map([
  ["groupId", { header: "Group Id", placeholder: "e.g. org.deltafi.stix", type: "string" }],
  ["artifactId", { header: "Artifact Id", placeholder: "e.g. stix, passthrough", type: "string" }],
  ["version", { header: "Version", placeholder: "e.g. 1.0.1, <commit hash>", type: "string" }],
]);

const selectedGroupId = ref(_.get(rowdata, "pluginCoordinates.groupId", null));
const originalGroupId = selectedGroupId.value;
const selectedArtifactId = ref(_.get(rowdata, "pluginCoordinates.artifactId", null));
const originalArtifactId = selectedArtifactId.value;
const selectedVersion = ref(_.get(rowdata, "pluginCoordinates.version", null));
const originalVersion = selectedVersion.value;

onMounted(async () => {});

const clearErrors = () => {
  errorsList.value = [];
};

const submit = async () => {
  await pluginUpdateFlow();

  if (errorsList.value.length === 0) {
    closeDialogCommand.command();
  }
  emit("reloadPlugins");
};

const pluginUpdateFlow = async () => {
  clearErrors();
  if (!selectedGroupId.value || !selectedArtifactId.value || !selectedVersion.value) {
    errorsList.value.push("You must provide a Group Id, Artifact Id, and Version");
    await nextTick();
    return;
  }

  if (!originalGroupId && !originalArtifactId && !originalVersion) {
    await installPlugin(selectedGroupId.value, selectedArtifactId.value, selectedVersion.value);
    return;
  }

  if (!_.isEqual(originalGroupId, selectedGroupId.value) || !_.isEqual(originalArtifactId, selectedArtifactId.value)) {
    await uninstallPlugin(selectedGroupId.value, selectedArtifactId.value, selectedVersion.value);
  }

  await installPlugin(selectedGroupId.value, selectedArtifactId.value, selectedVersion.value);
};
</script>

<style lang="scss">
@import "@/styles/components/plugin-configuration-dialog.scss";
</style>
