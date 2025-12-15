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

<!-- ABOUTME: Shared search options panel for DeltaFile filtering. -->
<!-- ABOUTME: Used by both DeltaFileSearchPage and FleetSearchPage. -->

<template>
  <div class="deltafile-search-options">
    <CollapsiblePanel :collapsed="collapsed">
      <template #header>
        <span class="align-advanced-options-header-title">
          <span class="d-flex">
            <span class="p-panel-title align-advanced-options-header">{{ title }}</span>
            <span>
              <Button v-tooltip.right="{ value: `Clear Options`, disabled: !activeAdvancedOptions }" rounded :class="`ml-2 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${activeAdvancedOptions ? 'p-column-filter-menu-button-active' : null}`" :disabled="!activeAdvancedOptions" @click="onClearOptions">
                <i class="pi pi-filter" style="font-size: 1rem" />
              </Button>
            </span>
          </span>
        </span>
      </template>
      <div class="search-options-wrapper">
        <div class="flex-row">
          <div v-for="columnNumber in 3" :key="columnNumber" :class="`flex-column ${_.isEqual(columnNumber, 2) ? 'flex-column-small' : ''}`">
            <template v-for="[i, formInfo] of _.orderBy(_.filter(advanceOptionsPanelInfo, ['column', columnNumber]), ['order'], ['asc']).entries()" :key="formInfo">
              <span class="dflex btn-group align-items-center">
                <label :for="`${formInfo.field}` + 'Id'" :class="!_.isEqual(i, 0) ? 'mt-2' : ''">{{ formInfo.label }}</label>
                <span @click="toggleAnnotationHelp">
                  <i v-if="_.get(formInfo, 'description', false)" class="fas fa-info-circle fa-fw ml-1" />
                </span>
              </span>
              <InputText v-if="_.isEqual(formInfo.componentType, 'InputText')" :id="`${formInfo.field}` + 'Id'" :model-value="modelValue[formInfo.field]" :placeholder="formInfo.placeholder" :class="formInfo.class" @update:model-value="updateField(formInfo.field, $event)" />
              <MultiSelect v-if="_.isEqual(formInfo.componentType, 'MultiSelect')" :id="`${formInfo.field}` + 'Id'" :model-value="modelValue[formInfo.field]" :options="formInfo.options" :placeholder="formInfo.placeholder" :option-group-label="formInfo.optionGroupLabel" :option-group-children="formInfo.optionGroupChildren" :option-label="formInfo.optionLabel" :option-value="formInfo.optionValue" :filter="formInfo.filter" :class="formInfo.class" display="chip" @update:model-value="updateField(formInfo.field, $event)" />
              <div v-if="_.isEqual(formInfo.componentType, 'SizeUnit')" class="size-container">
                <Dropdown :model-value="modelValue.sizeType" :options="sizeTypesOptions" style="width: 8rem" class="deltafi-input-field mr-2" @update:model-value="updateField('sizeType', $event)" />
                <InputNumber :model-value="modelValue.sizeMin" class="p-inputnumber input-area-height" :input-style="{ width: '6rem' }" placeholder="Min" @update:model-value="updateField('sizeMin', $event)" /> -
                <InputNumber :model-value="modelValue.sizeMax" class="p-inputnumber input-area-height" :input-style="{ width: '6rem' }" placeholder="Max" @update:model-value="updateField('sizeMax', $event)" />
                <Dropdown :model-value="modelValue.sizeUnit" :options="[...sizeUnitsOptionsMap.keys()]" class="deltafi-input-field ml-2" @update:model-value="updateField('sizeUnit', $event)" />
              </div>
              <Dropdown v-if="_.isEqual(formInfo.componentType, 'Dropdown')" :id="`${formInfo.field}` + 'Id'" :model-value="modelValue[formInfo.field]" :placeholder="formInfo.placeholder" :options="formInfo.options" :show-clear="formInfo.showClear" :class="formInfo.class" @update:model-value="updateField(formInfo.field, $event)">
                <template #value="slotProps">
                  <div v-if="slotProps.value != null" class="flex align-items-center">
                    <div>{{ formatOption(formInfo.formatOptions, slotProps.value) }}</div>
                  </div>
                  <span v-else>
                    {{ slotProps.placeholder }}
                  </span>
                </template>
                <template #option="slotProps">
                  <div class="flex align-items-center">
                    <div>{{ formatOption(formInfo.formatOptions, slotProps.option) }}</div>
                  </div>
                </template>
              </Dropdown>
              <InputNumber v-if="_.isEqual(formInfo.componentType, 'InputNumber')" :id="`${formInfo.field}` + 'Id'" :model-value="modelValue[formInfo.field]" :input-style="{ width: '6rem' }" :placeholder="formInfo.placeholder" :class="formInfo.class" @update:model-value="updateField(formInfo.field, $event)" />
              <div v-if="_.isEqual(formInfo.componentType, 'Annotations')" :id="`${formInfo.field}` + 'Id'" class="annotations-chips">
                <Chip v-for="item in modelValue.validatedAnnotations" :key="item" v-tooltip.top="{ value: invalidAnnotationTooltip(item.key), disabled: item.valid }" removable class="mr-2 mb-1" :class="{ 'invalid-chip': !item.valid, 'valid-chip': item.valid }" @remove="removeAnnotationItem(item)"> {{ item.key }}: {{ item.value }} </Chip>
                <Chip class="add-annotations-btn" @click="showAnnotationsOverlay">
                  &nbsp;
                  <i class="pi pi-plus" />
                  &nbsp;
                </Chip>
              </div>
            </template>
          </div>
          <OverlayPanel ref="annotationsOverlay">
            <Dropdown v-model="newAnnotationKey" placeholder="Key" :options="annotationsKeysOptions" option-label="key" style="width: 13rem" @keyup.enter="addAnnotationItemEvent" /> :
            <InputText v-model="newAnnotationValue" placeholder="Value" style="width: 13rem" @keyup.enter="addAnnotationItemEvent" />
          </OverlayPanel>
        </div>
      </div>
    </CollapsiblePanel>
    <OverlayPanel ref="annotationHelpOverlay" class="annotations-help">
      <div v-html="markdownIt.render(annotationSearchOptions)" />
    </OverlayPanel>
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import { computed, ref } from "vue";

import _ from "lodash";

import Button from "primevue/button";
import Chip from "primevue/chip";
import Dropdown from "primevue/dropdown";
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import MultiSelect from "primevue/multiselect";
import OverlayPanel from "primevue/overlaypanel";

import MarkdownIt from "markdown-it";

const markdownIt = new MarkdownIt({
  html: true,
});

const props = defineProps({
  modelValue: { type: Object, required: true },
  activeAdvancedOptions: { type: Boolean, required: true },
  formattedDataSourceNames: { type: Array, default: () => [] },
  dataSinkOptions: { type: Array, default: () => [] },
  transformOptions: { type: Array, default: () => [] },
  topicOptions: { type: Array, default: () => [] },
  stageOptions: { type: Array, default: () => [] },
  annotationKeysOptions: { type: Array, default: () => [] },
  sizeUnitsOptionsMap: { type: Map, required: true },
  sizeTypesOptions: { type: Array, required: true },
  title: { type: String, default: "Advanced Search Options" },
  collapsed: { type: Boolean, default: true },
  annotationValidationFn: { type: Function, default: () => true },
});

const emit = defineEmits(["update:modelValue", "clearOptions", "addAnnotation", "removeAnnotation"]);

const annotationsOverlay = ref();
const annotationHelpOverlay = ref();
const newAnnotationKey = ref(null);
const newAnnotationValue = ref(null);

const booleanOptions = [true, false];

const annotationsKeysOptions = computed(() => props.annotationKeysOptions);

const advanceOptionsPanelInfo = computed(() => {
  return [
    // First Column fields
    { field: "fileName", column: 1, order: 1, componentType: "InputText", label: "Filename:", placeholder: "Filename", class: "p-inputtext input-area-height responsive-width" },
    { field: "dataSources", column: 1, order: 3, componentType: "MultiSelect", label: "Data Sources:", placeholder: "Select a Data Source", options: props.formattedDataSourceNames, optionGroupLabel: "group", optionGroupChildren: "sources", optionLabel: "label", optionValue: "label", filter: true, class: "deltafi-input-field responsive-width" },
    { field: "transforms", column: 1, order: 4, componentType: "MultiSelect", label: "Transforms:", placeholder: "Select a Transform", options: props.transformOptions, class: "deltafi-input-field responsive-width" },
    { field: "dataSinks", column: 1, order: 5, componentType: "MultiSelect", label: "Data Sinks:", placeholder: "Select a Data Sink", options: props.dataSinkOptions, class: "deltafi-input-field responsive-width" },
    { field: "topics", column: 1, order: 6, componentType: "MultiSelect", label: "Topics:", placeholder: "Select a Topic", options: props.topicOptions, class: "deltafi-input-field responsive-width" },
    { field: "size", column: 1, order: 7, componentType: "SizeUnit", label: "Size:" },
    // 2nd Column fields
    { field: "testMode", column: 2, order: 1, componentType: "Dropdown", label: "Test Mode:", placeholder: "Select if in Test Mode", options: booleanOptions, formatOptions: true, showClear: true, class: "deltafi-input-field min-width" },
    { field: "replayable", column: 2, order: 2, componentType: "Dropdown", label: "Replayable:", placeholder: "Select if Replayable", options: booleanOptions, formatOptions: true, showClear: true, class: "deltafi-input-field min-width" },
    { field: "terminalStage", column: 2, order: 3, componentType: "Dropdown", label: "Terminal Stage:", placeholder: "Select if Terminal Stage", options: booleanOptions, formatOptions: true, showClear: true, class: "deltafi-input-field min-width" },
    { field: "egressed", column: 2, order: 4, componentType: "Dropdown", label: "Egressed:", placeholder: "Select if Egressed", options: booleanOptions, formatOptions: true, showClear: true, class: "deltafi-input-field min-width" },
    { field: "paused", column: 2, order: 5, componentType: "Dropdown", label: "Paused:", placeholder: "Select if Paused", options: booleanOptions, formatOptions: true, showClear: true, class: "deltafi-input-field min-width" },
    { field: "filtered", column: 2, order: 6, componentType: "Dropdown", label: "Filtered:", placeholder: "Select if Filtered", options: booleanOptions, formatOptions: true, showClear: true, class: "deltafi-input-field min-width" },
    { field: "warnings", column: 2, order: 7, componentType: "Dropdown", label: "Warnings:", placeholder: "Select if has warnings", options: booleanOptions, formatOptions: true, showClear: true, class: "deltafi-input-field min-width" },
    // 3rd Column fields
    { field: "filteredCause", column: 3, order: 1, componentType: "InputText", label: "Filtered Cause:", placeholder: "Filtered Cause", class: "deltafi-input-field min-width" },
    { field: "requeueMin", column: 3, order: 2, componentType: "InputNumber", label: "Requeue Count:", placeholder: "Min", class: "p-inputnumber input-area-height" },
    { field: "stage", column: 3, order: 3, componentType: "Dropdown", label: "Stage:", placeholder: "Select a Stage", options: props.stageOptions, formatOptions: false, showClear: true, class: "deltafi-input-field min-width" },
    { field: "pinned", column: 3, order: 4, componentType: "Dropdown", label: "Pinned:", placeholder: "Select if DeltaFile is pinned", options: booleanOptions, formatOptions: true, showClear: true, class: "deltafi-input-field min-width" },
    { field: "pendingAnnotations", column: 3, order: 5, componentType: "Dropdown", label: "Pending Annotations:", placeholder: "Select if Pending Annotations", options: booleanOptions, formatOptions: true, showClear: true, class: "deltafi-input-field min-width" },
    { field: "annotations", column: 3, order: 6, componentType: "Annotations", label: "Annotations:", description: "Annotations" },
  ];
});

const updateField = (field, value) => {
  const updated = { ...props.modelValue, [field]: value };
  emit("update:modelValue", updated);
};

const invalidAnnotationTooltip = (key) => {
  return `An annotation with the key '${key}' does not currently exist in the system.`;
};

const showAnnotationsOverlay = (event) => {
  annotationsOverlay.value.toggle(event);
};

const removeAnnotationItem = (item) => {
  emit("removeAnnotation", item);
};

const addAnnotationItemEvent = () => {
  if (newAnnotationKey.value && newAnnotationValue.value) {
    emit("addAnnotation", newAnnotationKey.value.key, newAnnotationValue.value);
    newAnnotationKey.value = null;
    newAnnotationValue.value = null;
    annotationsOverlay.value.toggle();
  }
};

const onClearOptions = () => {
  emit("clearOptions");
};

const formatOption = (formatOpt, dropdownOption) => {
  if (formatOpt) {
    return _.capitalize(dropdownOption);
  } else {
    return dropdownOption;
  }
};

const toggleAnnotationHelp = (event) => {
  annotationHelpOverlay.value.toggle(event);
};

const annotationSearchOptions = `
When performing a search using annotations, the following options are supported:
- If the value is *, the query will only check that the key exists (matches any value)
  - E.g., * will match null, "" (empty string), or abcd
- If the value contains *, the query will match if the value is LIKE the provided value, treating * as a wildcard
  - E.g., abc*3 will match abc123, but not abcdef
- If the value starts with !, the query will add a NOT to the comparison
  - E.g., !abc*3 will match abcdef, but not abc123
- When an ! or * is not present, the default behavior is an exact match
  - E.g., abc will only match abc.
`;
</script>

<style>
.deltafile-search-options {
  .align-advanced-options-header-title {
    align-content: flex-start;
  }

  .align-advanced-options-header {
    align-self: center;
  }

  label {
    font-weight: 500;
  }

  .p-multiselect.p-multiselect-chip .p-multiselect-token {
    padding-bottom: 1px;
    padding-top: 1px;
  }

  .input-area-height {
    height: 32px;
  }

  .p-panel-content {
    padding: 1.25rem 0.75rem 1.5rem !important;
  }

  .search-options-wrapper {
    display: flex;
  }

  .flex-row {
    display: flex;
    flex-direction: row;
    flex-wrap: wrap;
    width: 100%;
  }

  .flex-column {
    display: flex;
    flex-direction: column;
    flex-basis: 100%;
    flex: 2;
    margin: 0 0.75rem;
  }

  .flex-column-small {
    flex: 1;
  }

  .invalid-chip {
    background: var(--warning);
    color: var(--black);
  }

  .valid-chip {
    background: var(--info);
    color: var(--white);
  }

  .add-annotations-btn {
    cursor: pointer;
    background: var(--secondary);
    color: var(--primary-color-text);
    padding: 0 0.25rem;
  }

  .size-container {
    > * {
      vertical-align: middle !important;
    }
  }

  @media (min-width: 1024px) {
    .responsive-width {
      min-width: 32rem;
      max-width: 32rem;
    }
  }

  @media (min-width: 1200px) {
    .responsive-width {
      min-width: 36rem;
      max-width: 36rem;
    }
  }

  @media (min-width: 1600px) {
    .responsive-width {
      min-width: 42rem;
      max-width: 42rem;
    }
  }

  @media (min-width: 1900px) {
    .responsive-width {
      min-width: 44rem;
      max-width: 44rem;
    }
  }
}

.annotations-help {
  .p-overlaypanel-content {
    max-width: 30rem !important;
    font-size: 12px !important;
  }
}
</style>
