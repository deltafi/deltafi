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
            <InputText v-model="model.groupId" :placeholder="pluginConfigurationMap.get('groupId').placeholder" :disabled="pluginConfigurationMap.get('groupId').disabled" class="inputWidth" />
          </dd>
          <dt>{{ pluginConfigurationMap.get("artifactId").header }}</dt>
          <dd>
            <InputText v-model="model.artifactId" :placeholder="pluginConfigurationMap.get('artifactId').placeholder" :disabled="pluginConfigurationMap.get('artifactId').disabled" class="inputWidth" />
          </dd>
          <dt>{{ pluginConfigurationMap.get("version").header }}</dt>
          <dd>
            <InputText v-model="model.version" :placeholder="pluginConfigurationMap.get('version').placeholder" :disabled="pluginConfigurationMap.get('version').disabled" class="inputWidth" />
          </dd>
        </dl>
      </div>
    </div>
    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Submit" :disabled="disableSubmit" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import useNotifications from "@/composables/useNotifications";
import usePlugins from "@/composables/usePlugins";
import { useMounted } from "@vueuse/core";
import { computed, defineEmits, defineProps, nextTick, reactive, ref } from "vue";

import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import _ from "lodash";

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: null,
  },
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const pluginCoordinates = {
  groupId: null,
  artifactId: null,
  version: null,
};

const notify = useNotifications();
const { installPlugin, uninstallPlugin } = usePlugins();
const { closeDialogCommand } = reactive(props);
const rowData = ref(Object.assign({}, props.rowDataProp || pluginCoordinates));
const emit = defineEmits(["reloadPlugins"]);
const isMounted = ref(useMounted());
const errorsList = ref([]);
const model = computed({
  get() {
    return new Proxy(rowData.value, {
      set(obj, key, value) {
        model.value = { ...obj, [key]: value };
        return true;
      },
    });
  },
  set(newValue) {
    Object.assign(
      rowData.value,
      _.mapValues(newValue, (v) => (v === "" ? null : v))
    );
  },
});
const originalModel = Object.assign({}, model.value);

const pluginConfigurationMap = new Map([
  ["groupId", { header: "Group Id", placeholder: "e.g. org.deltafi.passthrough" }],
  ["artifactId", { header: "Artifact Id", placeholder: "e.g. passthrough" }],
  ["version", { header: "Version", placeholder: "e.g. 1.0.1, <commit hash>" }],
]);

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
  // Check to see if all required fields have values, is not return out
  if (!Object.values(model.value).every((x) => x)) {
    errorsList.value.push("You must provide a Group Id, Artifact Id, and Version");
    await nextTick();
    return;
  }

  // Check to see if this is a new plugin, if so install it and return out
  if (!Object.values(originalModel).every((x) => x)) {
    await installPlugin(model.value.groupId, model.value.artifactId, model.value.version);
    return;
  }

  // Check to see if this is updating an existing plugins groupId or artifactId, if so uninstall the plugin and continue
  if (!_.isEqual(originalModel.groupId, model.value.groupId) || !_.isEqual(originalModel.artifactId, model.value.artifactId)) {
    let response = await uninstallPlugin(originalModel.groupId, originalModel.artifactId, originalModel.version);
    let responseErrors = _.get(response.uninstallPlugin, "errors", null);
    if (!_.isEmpty(responseErrors)) {
      for (let errorMessages of responseErrors) {
        errorsList.value.push(errorMessages);
      }
      notify.error(`Removing plugin ${originalModel.artifactId} failed`, `Plugin ${originalModel.artifactId} was not removed.`, 4000);
      return;
    } else {
      notify.success(`Removed ${originalModel.artifactId}}`, `Successfully Removed ${originalModel.artifactId}}.`, 4000);
    }
  }

  await installPlugin(model.value.groupId, model.value.artifactId, model.value.version);
};

const disableSubmit = computed(() => {
  return _.isEqual(model.value, originalModel) || !Object.values(model.value).every((x) => x);
});
</script>

<style lang="scss">
@import "@/styles/components/plugin-configuration-dialog.scss";
</style>
