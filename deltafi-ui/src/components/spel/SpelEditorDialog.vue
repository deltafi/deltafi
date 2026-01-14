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
<!-- ABOUTME: Dialog wrapper for SpelExpressionEditor providing a spacious editing experience. -->
<!-- ABOUTME: Used by condition fields to open SpEL editor in a modal dialog with preview. -->

<template>
  <div class="spel-editor-dialog-trigger p-inputgroup">
    <div class="value-preview" :class="{ 'has-value': hasValue }" @click="openDialog">
      <code v-if="hasValue" class="preview-text">{{ truncatedValue }}</code>
      <span v-else class="placeholder-text">{{ emptyPlaceholder }}</span>
    </div>
    <Button
      v-tooltip.top="buttonTooltip"
      :icon="hasValue ? 'pi pi-pencil' : 'fas fa-code'"
      class="spel-trigger-button"
      :disabled="disabled"
      @click="openDialog"
    />
  </div>

  <Dialog
    v-model:visible="dialogVisible"
    :header="dialogHeader"
    modal
    :style="{ width: '70vw', maxWidth: '900px' }"
    :content-style="{ overflow: 'visible' }"
  >
    <SpelExpressionEditor
      v-model="localValue"
      :mode="mode"
      :placeholder="editorPlaceholder"
      :disabled="disabled"
      :show-test-panel="true"
    />

    <template #footer>
      <Button label="Cancel" class="p-button-text" @click="cancel" />
      <Button label="Apply" @click="apply" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import SpelExpressionEditor from './SpelExpressionEditor.vue';
import type { SpelExpressionMode } from '@/composables/useSpelTest';

const props = withDefaults(defineProps<{
  modelValue: string;
  mode: SpelExpressionMode;
  placeholder?: string;
  emptyPlaceholder?: string;
  disabled?: boolean;
  label?: string;
}>(), {
  placeholder: '',
  emptyPlaceholder: 'No value',
  disabled: false,
  label: '',
});

const emit = defineEmits<{
  'update:modelValue': [value: string | null];
}>();

const dialogVisible = ref(false);
const localValue = ref('');

const hasValue = computed(() => !!props.modelValue);

const truncatedValue = computed(() => {
  const val = props.modelValue || '';
  if (val.length > 60) {
    return val.substring(0, 57) + '...';
  }
  return val;
});

const dialogHeader = computed(() => {
  if (props.label) {
    return `Edit ${props.label}`;
  }
  return props.mode === 'CONDITION' ? 'Edit Condition' : 'Edit Template Expression';
});

const buttonTooltip = computed(() => {
  if (hasValue.value) {
    return 'Edit expression';
  }
  return props.mode === 'CONDITION' ? 'Add condition' : 'Add template expression';
});

const editorPlaceholder = computed(() => {
  if (props.placeholder) {
    return props.placeholder;
  }
  return props.mode === 'CONDITION'
    ? 'Enter SpEL condition...'
    : 'Enter value with {{ expressions }}...';
});

const openDialog = () => {
  if (props.disabled) return;
  localValue.value = props.modelValue || '';
  dialogVisible.value = true;
};

const cancel = () => {
  dialogVisible.value = false;
};

const apply = () => {
  emit('update:modelValue', localValue.value || null);
  dialogVisible.value = false;
};
</script>

<style scoped>
.spel-editor-dialog-trigger {
  display: flex;
  align-items: stretch;
  width: 100%;
}

.value-preview {
  flex: 1;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--surface-300);
  border-right: none;
  border-radius: 4px 0 0 4px;
  background: var(--surface-0);
  cursor: pointer;
  min-height: 2.5rem;
  display: flex;
  align-items: center;
  transition: border-color 0.15s;
}

.value-preview:hover {
  border-color: var(--primary-color);
}

.value-preview.has-value {
  background: var(--surface-50);
}

.preview-text {
  font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
  font-size: 0.9em;
  color: var(--text-color);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.placeholder-text {
  color: var(--text-color-secondary);
  font-style: italic;
}

.spel-trigger-button {
  background: var(--primary-color) !important;
  border-color: var(--primary-color) !important;
  color: white !important;
  border-radius: 0 4px 4px 0 !important;
}

.spel-trigger-button:hover {
  background: var(--primary-700) !important;
  border-color: var(--primary-700) !important;
}
</style>
