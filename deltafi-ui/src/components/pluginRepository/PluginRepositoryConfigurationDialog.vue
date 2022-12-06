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
  <div class="plugin-repo-config-dialog-body">
    <div class="plugin-repo-config-dialog-panel">
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
          <dt>{{ pluginImageRepoConfigurationMap.get("imageRepositoryBase").header }}</dt>
          <dd>
            <InputText v-model="model.imageRepositoryBase" :placeholder="pluginImageRepoConfigurationMap.get('imageRepositoryBase').placeholder" :disabled="pluginImageRepoConfigurationMap.get('imageRepositoryBase').disabled" class="inputWidth" />
          </dd>
          <dt>{{ pluginImageRepoConfigurationMap.get("pluginGroupIds").header }}</dt>
          <dd>
            <Chips v-model="model.pluginGroupIds" separator="," add-on-blur :allow-duplicate="false" :placeholder="!model.pluginGroupIds ? pluginImageRepoConfigurationMap.get('pluginGroupIds').placeholder : null" :disabled="pluginImageRepoConfigurationMap.get('pluginGroupIds').disabled" class="chipInputWidth" @add="addPluginGroupID" />
          </dd>
          <dt>{{ pluginImageRepoConfigurationMap.get("imagePullSecret").header }}</dt>
          <dd>
            <InputText v-model="model.imagePullSecret" :placeholder="pluginImageRepoConfigurationMap.get('imagePullSecret').placeholder" :disabled="pluginImageRepoConfigurationMap.get('imagePullSecret').disabled" class="inputWidth" />
          </dd>
        </dl>
      </div>
    </div>
    <teleport v-if="isMounted && !viewPluginImageRepo" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Submit" :disabled="disableSubmit" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import usePlugins from "@/composables/usePlugins";
import { useMounted } from "@vueuse/core";
import { computed, defineEmits, defineProps, nextTick, reactive, ref } from "vue";

import Button from "primevue/button";
import Chips from "primevue/chips";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import _ from "lodash";

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: null,
  },
  viewPluginImageRepo: {
    type: Boolean,
    required: false,
    default: false,
  },
  closeDialogCommand: {
    type: Object,
    default: null,
  },
});

const pluginRepoConfig = {
  imageRepositoryBase: null,
  pluginGroupIds: null,
  imagePullSecret: null,
};

const { viewPluginImageRepo, closeDialogCommand } = reactive(props);
const rowData = ref(Object.assign({}, props.rowDataProp || pluginRepoConfig));
const emit = defineEmits(["reloadPluginRepos"]);
const { savePluginImageRepository } = usePlugins();
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

const pluginImageRepoConfigurationMap = new Map([
  ["imageRepositoryBase", { header: "Image Repository Base", placeholder: "e.g. docker.io/deltafi/", type: "string", disabled: viewPluginImageRepo }],
  ["pluginGroupIds", { header: "Plugin Group Ids", placeholder: "e.g. org.deltafi.passthrough", type: "string", disabled: viewPluginImageRepo }],
  ["imagePullSecret", { header: "Image Pull Secret", placeholder: "e.g. docker-secret", type: "string", disabled: viewPluginImageRepo }],
]);

const isMounted = ref(useMounted());

const submit = async () => {
  await updatePluginImageRepo();

  if (errorsList.value.length === 0) {
    closeDialogCommand.command();
  }
  emit("reloadPluginRepos");
};

const updatePluginImageRepo = async () => {
  clearErrors();
  if (!model.value.imageRepositoryBase || !model.value.pluginGroupIds) {
    errorsList.value.push("You must provide a Image Repository Base and Plugin Group Ids");
    await nextTick();
    return;
  }

  await savePluginImageRepository(model.value.imageRepositoryBase, model.value.pluginGroupIds, model.value.imagePullSecret);
};

const addPluginGroupID = (event) => {
  model.value.pluginGroupIds = event.value;
};

const clearErrors = () => {
  errorsList.value = [];
};

const disableSubmit = computed(() => {
  return _.isEqual(model.value, originalModel) || !Object.values(model.value).every((x) => !_.isEmpty(x));
});
</script>

<style lang="scss">
@import "@/styles/components/plugin-repo-configuration-dialog.scss";
</style>
