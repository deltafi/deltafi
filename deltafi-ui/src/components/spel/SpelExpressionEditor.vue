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
<!-- ABOUTME: Rich SpEL expression editor with validation, quick insert buttons, and testing. -->
<!-- ABOUTME: Supports both condition and template expression modes with live feedback. -->

<template>
  <div class="spel-expression-editor">
    <!-- Expression Input -->
    <div class="expression-input-container">
      <Textarea
        ref="textareaRef"
        v-model="expressionValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :class="['expression-textarea', { 'p-invalid': !validationResult.valid && expressionValue }]"
        :aria-invalid="!validationResult.valid && !!expressionValue"
        aria-label="SpEL expression"
        rows="3"
        @blur="validateOnBlur"
        @input="onInput"
      />
      <div class="expression-controls">
        <Button
          v-tooltip.top="'Show reference'"
          icon="pi pi-question-circle"
          class="p-button-text p-button-sm"
          @click="showReference = !showReference"
        />
        <Button
          v-if="showTestPanel !== false"
          v-tooltip.top="'Test expression'"
          icon="pi pi-play"
          class="p-button-text p-button-sm"
          @click="showTest = !showTest"
        />
      </div>
    </div>

    <!-- Validation Status -->
    <div v-if="expressionValue" class="validation-status">
      <span v-if="validating" class="validating">
        <i class="pi pi-spin pi-spinner"></i> Validating...
      </span>
      <span v-else-if="validationResult.valid" class="valid">
        <i class="pi pi-check-circle"></i> Valid expression
      </span>
      <span v-else class="invalid">
        <i class="pi pi-times-circle"></i> {{ validationResult.errorMessage }}
      </span>
    </div>

    <!-- Warning for comparison operators -->
    <div v-if="warning" class="validation-status">
      <span class="warning">
        <i class="pi pi-exclamation-triangle"></i> {{ warning }}
      </span>
    </div>

    <!-- Quick Insert Buttons -->
    <div class="quick-insert-buttons">
      <span class="quick-insert-label">Insert:</span>
      <template v-if="mode === 'CONDITION'">
        <Button label="metadata['']" class="p-button-text p-button-sm" @click="insertAtCursor(`metadata['']`, 10)" />
        <Button label="hasMediaType('')" class="p-button-text p-button-sm" @click="insertAtCursor(`hasMediaType('')`, 13)" />
        <Button label="containsKey('')" class="p-button-text p-button-sm" @click="insertAtCursor(`metadata.containsKey('')`, 22)" />
        <Button label="content.size()" class="p-button-text p-button-sm" @click="insertAtCursor('content.size()')" />
        <Button label="&&" class="p-button-text p-button-sm" @click="insertAtCursor(' && ')" />
        <Button label="||" class="p-button-text p-button-sm" @click="insertAtCursor(' || ')" />
        <Button label="!" class="p-button-text p-button-sm" @click="insertAtCursor('!')" />
      </template>
      <template v-else>
        <Button label="{{ metadata[''] }}" class="p-button-text p-button-sm" @click="insertAtCursor(`{{ metadata[''] }}`, 14)" />
        <Button label="{{ deltaFileName }}" class="p-button-text p-button-sm" @click="insertAtCursor('{{ deltaFileName }}')" />
        <Button label="{{ did }}" class="p-button-text p-button-sm" @click="insertAtCursor('{{ did }}')" />
        <Button label="{{ now() }}" class="p-button-text p-button-sm" @click="insertAtCursor('{{ now() }}')" />
      </template>
    </div>

    <!-- Reference Panel -->
    <Panel v-if="showReference" header="SpEL Reference" toggleable class="reference-panel">
      <SpelReference :mode="mode" @insert="insertAtCursor" />
    </Panel>

    <!-- Test Panel -->
    <Panel v-if="showTest" header="Test Expression" toggleable class="test-panel">
      <SpelTestPanel
        :expression="expressionValue"
        :mode="mode"
      />
    </Panel>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useDebounceFn } from '@vueuse/core';
import Button from 'primevue/button';
import Panel from 'primevue/panel';
import Textarea from 'primevue/textarea';
import SpelReference from './SpelReference.vue';
import SpelTestPanel from './SpelTestPanel.vue';
import useSpelTest, { SpelExpressionMode, SpelTestResult } from '@/composables/useSpelTest';

const props = withDefaults(defineProps<{
  modelValue: string;
  mode: SpelExpressionMode;
  placeholder?: string;
  disabled?: boolean;
  showTestPanel?: boolean;
}>(), {
  placeholder: 'Enter SpEL expression...',
  disabled: false,
  showTestPanel: true
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
  'validation': [result: SpelTestResult];
}>();

const { validateExpression, loading: validating } = useSpelTest();

const textareaRef = ref<InstanceType<typeof Textarea> | null>(null);
const showReference = ref(false);
const showTest = ref(false);
const validationResult = ref<SpelTestResult>({ valid: true });
const warning = ref<string | null>(null);

// Pattern to detect comparison operators with metadata values
const COMPARISON_PATTERN = /metadata\s*\[\s*['"][^'"]+['"]\s*\]\s*(>=|<=|>|<)\s*-?\d/;

// Pattern to detect empty metadata key lookups
const EMPTY_KEY_PATTERN = /metadata\s*\[\s*['"]{2}\s*\]/;

// Check for comparison operator usage and suggest method alternatives
const checkForComparisonWarning = (expression: string): string | null => {
  if (COMPARISON_PATTERN.test(expression)) {
    const match = expression.match(/(>=|<=|>|<)/);
    const op = match?.[1];
    const methodMap: Record<string, string> = {
      '>': '.gt()',
      '<': '.lt()',
      '>=': '.gte()',
      '<=': '.lte()'
    };
    return `Use ${methodMap[op!]} for numeric comparison. Example: metadata['count'].gt(5)`;
  }
  return null;
};

// Check for empty metadata key lookups
const checkForEmptyKey = (expression: string): string | null => {
  if (EMPTY_KEY_PATTERN.test(expression)) {
    return "Empty metadata key - please specify a key name";
  }
  return null;
};

const expressionValue = computed({
  get: () => props.modelValue || '',
  set: (value: string) => emit('update:modelValue', value)
});

const debouncedValidate = useDebounceFn(async () => {
  if (!expressionValue.value) {
    validationResult.value = { valid: true };
    return;
  }

  // Check for empty metadata keys first (client-side validation)
  const emptyKeyError = checkForEmptyKey(expressionValue.value);
  if (emptyKeyError) {
    validationResult.value = { valid: false, errorMessage: emptyKeyError };
    emit('validation', validationResult.value);
    return;
  }

  try {
    validationResult.value = await validateExpression(expressionValue.value, props.mode);
    emit('validation', validationResult.value);
  } catch (e) {
    validationResult.value = { valid: false, errorMessage: 'Validation failed' };
  }
}, 500);

const onInput = () => {
  debouncedValidate();
};

const validateOnBlur = async () => {
  if (!expressionValue.value) return;

  // Check for empty metadata keys first (client-side validation)
  const emptyKeyError = checkForEmptyKey(expressionValue.value);
  if (emptyKeyError) {
    validationResult.value = { valid: false, errorMessage: emptyKeyError };
    emit('validation', validationResult.value);
    return;
  }

  try {
    validationResult.value = await validateExpression(expressionValue.value, props.mode);
    emit('validation', validationResult.value);
  } catch (e) {
    validationResult.value = { valid: false, errorMessage: 'Validation failed' };
  }
};

const insertAtCursor = (text: string, cursorOffset?: number) => {
  // Access the underlying textarea element from the PrimeVue component
  const textareaComponent = textareaRef.value as any;
  const textarea = textareaComponent?.$el as HTMLTextAreaElement | undefined;
  if (!textarea) return;

  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;
  const before = expressionValue.value.substring(0, start);
  const after = expressionValue.value.substring(end);

  expressionValue.value = before + text + after;

  // Trigger validation after insert
  debouncedValidate();

  // Set cursor position
  const newPos = cursorOffset !== undefined ? start + cursorOffset : start + text.length;
  setTimeout(() => {
    textarea.focus();
    textarea.setSelectionRange(newPos, newPos);
  }, 0);
};

// Check for warnings and validate whenever expression changes
watch(expressionValue, (newValue) => {
  warning.value = checkForComparisonWarning(newValue);
  if (newValue) {
    debouncedValidate();
  } else {
    validationResult.value = { valid: true };
  }
}, { immediate: true });

// Re-validate when mode changes
watch(() => props.mode, () => {
  if (expressionValue.value) {
    debouncedValidate();
  }
});
</script>

<style scoped>
.spel-expression-editor {
  width: 100%;
}

.expression-input-container {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
}

.expression-textarea {
  flex: 1;
  font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
  font-size: 0.9rem;
  min-height: 4rem;
  resize: vertical;
}

.expression-controls {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.validation-status {
  margin-top: 0.5rem;
  font-size: 0.85rem;
}

.validation-status .validating {
  color: var(--text-color-secondary);
}

.validation-status .valid {
  color: var(--green-500);
}

.validation-status .invalid {
  color: var(--red-500);
}

.validation-status .warning {
  color: var(--yellow-600);
}

.quick-insert-buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.25rem;
  margin-top: 0.75rem;
  padding: 0.5rem;
  background: var(--surface-50);
  border-radius: 4px;
}

.quick-insert-label {
  font-size: 0.85rem;
  color: var(--text-color-secondary);
  margin-right: 0.5rem;
}

.quick-insert-buttons :deep(.p-button) {
  font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
  font-size: 0.8rem;
  padding: 0.25rem 0.5rem;
}

.reference-panel,
.test-panel {
  margin-top: 1rem;
}

.reference-panel :deep(.p-panel-content),
.test-panel :deep(.p-panel-content) {
  padding: 1rem;
}
</style>
