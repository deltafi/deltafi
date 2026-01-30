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
  <control-wrapper v-bind="schemaData.controlWrapper" :styles="schemaData.styles" :is-focused="schemaData.isFocused" :applied-options="schemaData.appliedOptions">
    <dl class="string-field">
      <dt :id="schemaData.control.id + '-input-label'" class="string-label">
        {{ computedLabel }}<span v-if="schemaData.control.required" class="required-indicator">*</span>
        <i v-if="!_.isEmpty(schemaData.control.description)" v-tooltip.right="schemaData.control.description" class="pi pi-info-circle info-icon" />
      </dt>
      <dd class="string-input">
        <template v-if="_.isEqual(schemaData.control.i18nKeyPrefix.split('.').pop(), 'topic')">
          <AutoComplete :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input + ' auto-complete-input-width'" :suggestions="topicList" @complete="search" @change="schemaData.onChange(schemaData.control.data)" />
        </template>
        <template v-else-if="optionList !== undefined">
          <Dropdown :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input + ' inputWidth'" :disabled="!schemaData.control.enabled" :autofocus="schemaData.appliedOptions.focus" :placeholder="schemaData.appliedOptions.placeholder" :options="optionList" :option-label="optionsDisplay" @change="schemaData.onChange(schemaData.control.data)" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
        </template>
        <template v-else-if="schemaData.control.schema.maxLength !== undefined && schemaData.control.schema.maxLength > 80">
          <Textarea :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input + ' inputWidth align-items-center'" :disabled="!schemaData.control.enabled" :autofocus="schemaData.appliedOptions.focus" :placeholder="schemaData.appliedOptions.placeholder" rows="10" cols="80" @input="schemaData.onChange(undefinedStringCheck(schemaData.control.data))" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
        </template>
        <template v-else>
          <div class="p-inputgroup inputWidth">
            <InputText :id="schemaData.control.id + '-input'" v-model="schemaData.control.data" :class="schemaData.styles.control.input" :disabled="!schemaData.control.enabled" :autofocus="schemaData.appliedOptions.focus" :placeholder="schemaData.appliedOptions.placeholder" @input="schemaData.onChange(undefinedStringCheck(schemaData.control.data))" @focus="schemaData.isFocused = true" @blur="schemaData.isFocused = false" />
            <Button
              v-tooltip.top="'Edit with expression editor'"
              icon="fas fa-code"
              class="spel-button"
              @click="openSpelDialog"
            />
          </div>
        </template>
      </dd>
    </dl>
  </control-wrapper>

  <Dialog
    v-model:visible="spelDialogVisible"
    :header="'Edit ' + fieldLabel"
    modal
    :style="{ width: '70vw', maxWidth: '900px' }"
  >
    <SpelExpressionEditor
      v-model="spelDialogValue"
      mode="TEMPLATE"
      placeholder="Enter value with {{ expressions }}..."
      :disabled="!schemaData.control.enabled"
    />
    <template #footer>
      <Button label="Cancel" class="p-button-text" @click="spelDialogVisible = false" />
      <Button label="Apply" @click="applySpelValue" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import useSchemaComposition from "@/components/jsonSchemaRenderers/defaultRenderers/util/useSchemaComposition";
import { ControlElement } from "@jsonforms/core";
import { computed, reactive, ref } from "vue";
import { rendererProps, useJsonFormsControl } from "@jsonforms/vue";
import { default as ControlWrapper } from "./ControlWrapper.vue";
import { SpelExpressionEditor } from "@/components/spel";

import _ from "lodash";
import MarkdownIt from "markdown-it";

import AutoComplete from "primevue/autocomplete";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";
import Textarea from "primevue/textarea";

const markdownIt = new MarkdownIt({
  html: true,
});

const { useControl } = useSchemaComposition();

const props = defineProps({
  ...rendererProps<ControlElement>(),
});

const schemaData = reactive(useControl(useJsonFormsControl(props), (value) => value || undefined, 300));
const topicList = ref<any[] | undefined>([]);

// SpEL dialog state
const spelDialogVisible = ref(false);
const spelDialogValue = ref('');

const fieldLabel = computed(() => {
  return schemaData.control.i18nKeyPrefix?.split('.').pop() || 'Value';
});

const openSpelDialog = () => {
  spelDialogValue.value = schemaData.control.data || '';
  spelDialogVisible.value = true;
};

const applySpelValue = () => {
  schemaData.onChange(spelDialogValue.value || null);
  spelDialogVisible.value = false;
};

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
    const topics = schemaData.control.schema.enum ?? [];
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

const computedLabel = computed(() => {
  return schemaData.control.config.defaultLabels ? schemaData.control.label : schemaData.control.i18nKeyPrefix.split(".").pop();
});

const optionsDisplay = computed(() => {
  if (schemaData.control.config.defaultLabels) {
    return (option: any) => _.startCase(_.lowerCase(option));
  }

  return (option: any) => option;
});
</script>

<style scoped>
.string-field {
  margin-bottom: 0.75rem;
}

.string-label {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--text-color);
  margin-bottom: 0.25rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.string-input {
  margin: 0;
}

.info-icon {
  font-size: 0.8rem;
  color: var(--text-color-secondary);
  cursor: help;
}

.required-indicator {
  color: var(--red-500);
  margin-left: 0.125rem;
}
</style>

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

/* Style dropdown trigger to match AutoComplete dropdown button */
.p-dropdown .p-dropdown-trigger {
  background: var(--primary-color);
  color: white;
  border-top-right-radius: 4px;
  border-bottom-right-radius: 4px;
}

/* Style SpEL button to match dropdown triggers */
.spel-button {
  background: var(--primary-color) !important;
  border-color: var(--primary-color) !important;
  color: white !important;
  border-top-left-radius: 0 !important;
  border-bottom-left-radius: 0 !important;
}

.spel-button:hover {
  background: var(--primary-700) !important;
  border-color: var(--primary-700) !important;
}
</style>
