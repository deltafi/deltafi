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
  <div class="plugin-configuration-dialog">
    <div class="install-plugin-panel">
      <div class="pb-0">
        <div v-if="!_.isEmpty(errorsList)" class="pt-2">
          <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
            <ul>
              <div v-for="(error, key) in errorsList" :key="key">
                <li class="text-wrap text-break">
                  {{ error }}
                </li>
              </div>
            </ul>
          </Message>
        </div>
        <dl>
          <dt>{{ pluginConfigurationMap.get("imageName").header }}</dt>
          <dd>
            <InputText v-model.trim="model.imageName" :placeholder="pluginConfigurationMap.get('imageName').placeholder" class="inputWidth" />
          </dd>
          <dt>{{ pluginConfigurationMap.get("imageTag").header }}</dt>
          <dd>
            <InputText v-model.trim="model.imageTag" :placeholder="pluginConfigurationMap.get('imageTag').placeholder" class="inputWidth" />
          </dd>
          <dt>
            {{ pluginConfigurationMap.get("imagePullSecret").header }}
            <i v-tooltip="pluginConfigurationMap.get('imagePullSecret').tooltip" class="ml-0 text-muted fas fa-info-circle fa-fw" />
          </dt>
          <dd>
            <InputText v-model.trim="model.imagePullSecret" :placeholder="pluginConfigurationMap.get('imagePullSecret').placeholder" class="inputWidth" />
          </dd>
        </dl>
      </div>
    </div>
    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Submit" :disabled="disableSubmit" :loading="submitLoad" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import useNotifications from "@/composables/useNotifications";
import usePlugins from "@/composables/usePlugins";
import { useMounted } from "@vueuse/core";
import { computed, nextTick, ref } from "vue";

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
});

const pluginInstallDetails = {
  image: null,
  imagePullSecret: null,
};

const notify = useNotifications();
const submitLoad = ref(false);
const { errors, installPlugin } = usePlugins();
const rowData = ref(Object.assign({}, props.rowDataProp || pluginInstallDetails));
const emit = defineEmits(["refreshAndClose"]);
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
      _.mapValues(newValue, (v) => (v === "" ? null : v)),
      { image: `${newValue.imageName}:${newValue.imageTag || "latest"}` }
    );
  },
});
const originalModel = Object.assign({}, model.value);

const pluginConfigurationMap = new Map([
  ["imageName", { header: "Image Name*", placeholder: "e.g. docker.io/deltafi/deltafi-passthrough" }],
  ["imageTag", { header: "Image Tag*", placeholder: "e.g. latest" }],
  ["imagePullSecret", { header: "Image Pull Secret", placeholder: "e.g. docker-secret", tooltip: "Optional - the name of the secret that holds the credentials necessary to pull this image" }],
]);

const clearErrors = () => {
  errorsList.value = [];
};

const submit = async () => {
  submitLoad.value = true;
  await pluginUpdateFlow();
  submitLoad.value = false;

  if (errorsList.value.length === 0) {
    emit("refreshAndClose");
  }
};

const pluginUpdateFlow = async () => {
  clearErrors();
  // Check to see if all required fields have values, is not return out

  if (_.isEmpty(model.value.image)) {
    errorsList.value.push("You must provide an image");
    await nextTick();
    return;
  }

  notify.info(`Installing plugin ${model.value.image}`, `Attempting to install ${model.value.image}.`, 4000);

  // Install the plugin
  const response = await installPlugin(model.value.image, model.value.imagePullSecret);
  const responseErrors = _.get(response?.installPlugin, "errors", null) ?? errors.value;
  if (!_.isEmpty(responseErrors)) {
    for (const errorMessage of responseErrors) {
      if (Object.hasOwn(errorMessage, "message")) {
        errorsList.value.push(errorMessage.message);
      } else {
        errorsList.value.push(_.trim(errorMessage));
      }
    }
    notify.error(`Installing ${model.value.image} failed`, `Plugin ${model.value.image} was not installed.`, 4000);
    return;
  } else {
    notify.success(`Installed ${model.value.image}`, `Successfully installed ${model.value.image}.`, 4000);
  }
};

const disableSubmit = computed(() => {
  return _.isEqual(model.value, originalModel) || _.isEmpty(model.value.image);
});
</script>

<style>
.plugin-configuration-dialog {
  width: 98%;

  .install-plugin-panel {
    dt {
      margin-bottom: 0rem;
    }

    dd {
      margin-bottom: 1.2rem;
    }

    dl {
      margin-bottom: 1rem;
    }

    .p-panel-content {
      padding-bottom: 0.25rem !important;
    }

    i.text-muted {
      color: rgb(165, 165, 165) !important;
    }
  }

  .inputWidth {
    width: 100% !important;
  }
}
</style>
