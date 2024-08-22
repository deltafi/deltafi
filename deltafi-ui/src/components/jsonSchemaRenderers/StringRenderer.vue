<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
  <control-wrapper v-bind="schemaData.controlWrapper" :styles="schemaData.styles" :is-focused="schemaData.isFocused" :applied-options="schemaData.appliedOptions">
    <dl>
      <dt v-if="!_.isEmpty(schemaData.computedLabel)" :id="schemaData.control.id + '-input-label'">{{ schemaData.control.i18nKeyPrefix.split(".").pop() }}</dt>
      <dd>
        <template v-if="_.isEqual(schemaData.control.i18nKeyPrefix.split('.').pop(), 'topic')">
          <AutoComplete :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input + ' auto-complete-input-width'" :suggestions="topicList" @complete="search" @change="schemaData.onChange(schemaData.control.data)" />
        </template>
        <template v-else-if="optionList !== undefined">
          <Dropdown :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input + ' inputWidth'" :disabled="!schemaData.control.enabled" :autofocus="schemaData.appliedOptions.focus" :placeholder="schemaData.appliedOptions.placeholder" :options="optionList" @change="schemaData.onChange(schemaData.control.data)" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
        </template>
        <template v-else>
          <InputText :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input + ' inputWidth align-items-center'" :disabled="!schemaData.control.enabled" :autofocus="schemaData.appliedOptions.focus" :placeholder="schemaData.appliedOptions.placeholder" @input="schemaData.onChange(undefinedStringCheck(schemaData.control.data))" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
        </template>
        <div>
          <small :id="schemaData.control.id + '-input-help'">{{ schemaData.control.description }}</small>
        </div>
      </dd>
    </dl>
  </control-wrapper>
</template>

<script setup lang="ts">
import useSchemaComposition from "@/components/jsonSchemaRenderers/util/useSchemaComposition";
import { ControlElement } from "@jsonforms/core";
import { computed, defineProps, reactive, ref } from "vue";
import { rendererProps, useJsonFormsControl } from "@jsonforms/vue";
import { default as ControlWrapper } from "./ControlWrapper.vue";

import _ from "lodash";

import AutoComplete from "primevue/autocomplete";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";

const { useControl } = useSchemaComposition();

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useControl(useJsonFormsControl(props), (value) => value || undefined, 300));
const topicList = ref<any[] | undefined>([]);

const optionList = computed(() => {
  const optionList = schemaData.control.schema.enum;
  if (optionList === undefined || !_.isArray(optionList) || !_.every(optionList, _.isString)) {
    // check for incorrect data
    return undefined;
  }
  return optionList;
});

const search = async (event: any) => {
  setTimeout(() => {
    let topics = schemaData.control.schema.enum ?? [];
    if (!event.query.trim().length) {
      topicList.value = topics;
    } else {
      topicList.value = topics.filter((topic) => {
        return topic.toLowerCase().includes(event.query.toLowerCase());
      });
    }
  }, 300);
};

const undefinedStringCheck = (value: any) => {
  if (_.isEmpty(value)) {
    return null;
  }
  return value;
};
</script>

<style>
.auto-complete-input-width {
  width: 100% !important;
  > .p-inputtext {
    width: 100% !important;
  }
}

.inputWidth {
  width: 90% !important;
}
</style>
