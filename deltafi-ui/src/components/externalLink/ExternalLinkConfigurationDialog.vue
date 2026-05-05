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
  <div class="external-link-body">
    <div class="external-link-panel">
      <div v-if="!_.isEmpty(errorsList)" class="pt-2" role="alert" aria-live="assertive">
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
        <dt><label for="el-link-type">{{ linkConfigurationMap.get("linkType").header }}<span v-if="linkConfigurationMap.get('linkType').required" class="required-indicator" aria-hidden="true">*</span></label></dt>
        <dd>
          <Select id="el-link-type" v-model="selectedLinkType" :options="linkConfigurationMap.get('linkType').types" :placeholder="linkConfigurationMap.get('linkType').placeholder" :disabled="linkConfigurationMap.get('linkType').disabled" :aria-required="linkConfigurationMap.get('linkType').required" :show-clear="!linkConfigurationMap.get('linkType').disabled" class="inputWidth" />
        </dd>
        <dt><label for="el-name">{{ linkConfigurationMap.get("name").header }}<span v-if="linkConfigurationMap.get('name').required" class="required-indicator" aria-hidden="true">*</span></label></dt>
        <dd>
          <InputText id="el-name" v-model="model.name" :placeholder="linkConfigurationMap.get('name').placeholder" :disabled="linkConfigurationMap.get('name').disabled" :aria-required="linkConfigurationMap.get('name').required" class="inputWidth" />
        </dd>
        <dt><label for="el-description">{{ linkConfigurationMap.get("description").header }}</label></dt>
        <dd>
          <InputText id="el-description" v-model="model.description" :placeholder="linkConfigurationMap.get('description').placeholder" :disabled="linkConfigurationMap.get('description').disabled" class="inputWidth" />
        </dd>
        <dt><label for="el-url">{{ linkConfigurationMap.get("url").header }}<span v-if="linkConfigurationMap.get('url').required" class="required-indicator" aria-hidden="true">*</span></label></dt>
        <dd>
          <InputText id="el-url" v-model="model.url" :placeholder="linkConfigurationMap.get('url').placeholder" :disabled="linkConfigurationMap.get('url').disabled" :aria-required="linkConfigurationMap.get('url').required" class="inputWidth" />
        </dd>
      </dl>
    </div>
    <teleport v-if="isMounted && !viewLink" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Submit" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import useExternalLinks from "@/composables/useExternalLinks";
import { computed, reactive, ref } from "vue";
import { useMounted } from "@vueuse/core";
import { EnumType } from "json-to-graphql-query";

import Button from "primevue/button";
import Select from "primevue/select";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import _ from "lodash";

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: Object,
  },
  rowLinkType: {
    type: [String, null],
    required: false,
    default: null,
  },
  viewLink: {
    type: Boolean,
    required: false,
    default: false,
  },
  editLink: {
    type: Boolean,
    required: false,
    default: false,
  },
});

const linkTemplate = {
  url: null,
  name: null,
  description: null,
};

const { viewLink, editLink } = reactive(props);
const { saveLink } = useExternalLinks();
const rowData = ref(Object.assign({}, props.rowDataProp || linkTemplate));
const originalData = ref(Object.assign({}, props.rowDataProp || linkTemplate));
const selectedLinkType = ref(props.rowLinkType);
const errorsList = ref([]);

const emit = defineEmits(["refreshAndClose"]);

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
const isMounted = ref(useMounted());

const linkConfigurationMap = new Map([
  ["linkType", { header: "Link Type", required: true, placeholder: "Custom Link Type", types: ["DeltaFile Link", "External Link"], disabled: viewLink || editLink }],
  ["url", { header: "Url", required: true, placeholder: "Url for linked service", disabled: viewLink }],
  ["name", { header: "Name", required: true, placeholder: "Name of linked Service", disabled: viewLink }],
  ["description", { header: "Description", placeholder: "Description of linked service", disabled: viewLink }],
]);

const clearErrors = () => {
  errorsList.value = [];
};

const submit = () => {
  const errorMessages = [];
  if (!selectedLinkType.value) {
    errorMessages.push("Link type is a required field");
  }

  if (_.isEmpty(model.value.name)) {
    errorMessages.push("Name is a required field");
  }

  if (_.isEmpty(model.value.url)) {
    errorMessages.push("URL is a required field");
  }

  if (_.isEqual(model.value, originalData.value)) {
    errorMessages.push("No changes have been made");
  }

  if (errorMessages.length > 0) {
    errorsList.value = errorMessages;
    return;
  }

  if (_.isEqual(selectedLinkType.value, "DeltaFile Link")) {
    model.value.linkType = new EnumType("DELTAFILE_LINK");
  } else if (_.isEqual(selectedLinkType.value, "External Link")) {
    model.value.linkType = new EnumType("EXTERNAL");
  }
  saveLink(model.value);
  emit("refreshAndClose");
};
</script>

<style>
.external-link-body {
  width: 98%;

  .external-link-panel {
    dt {
      margin-bottom: 0rem;
    }

    dd {
      margin-bottom: 1.2rem;
    }

    dl {
      margin-bottom: 1rem;
    }
  }

  .inputWidth {
    width: 100% !important;
  }
}
</style>
