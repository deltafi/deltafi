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
<!-- ABOUTME: Test panel for evaluating SpEL expressions against sample data. -->
<!-- ABOUTME: Auto-runs tests when expression or test data changes. -->

<template>
  <div class="spel-test-panel">
    <!-- Load from DID -->
    <div class="load-from-did">
      <InputText
        v-model="loadDid"
        placeholder="Enter DID to load test data..."
        class="did-input"
        @keyup.enter="loadFromDid"
      />
      <Button
        label="Load"
        icon="pi pi-download"
        class="p-button-sm"
        :loading="loadingDid"
        :disabled="!loadDid"
        @click="loadFromDid"
      />
    </div>
    <div v-if="availableFlows.length > 0" class="flow-picker">
      <label>Flow:</label>
      <Dropdown
        v-model="selectedFlow"
        :options="availableFlows"
        option-label="label"
        placeholder="Select a flow"
        class="flow-dropdown"
        @change="onFlowSelected"
      />
    </div>
    <div v-if="loadError" class="load-error">
      <i class="pi pi-exclamation-triangle"></i>
      {{ loadError }}
    </div>

    <div class="test-data-grid">
      <!-- Metadata Section -->
      <div class="test-data-section">
        <div class="section-header">
          <span>Metadata</span>
          <Button
            icon="pi pi-plus"
            class="p-button-text p-button-sm"
            @click="addMetadata"
          />
        </div>
        <div class="metadata-list">
          <div v-for="(item, index) in testData.metadata" :key="index" class="metadata-item">
            <InputText
              v-model="item.key"
              placeholder="key"
              class="metadata-key"
            />
            <span class="metadata-separator">=</span>
            <InputText
              v-model="item.value"
              placeholder="value"
              class="metadata-value"
            />
            <Button
              icon="pi pi-times"
              class="p-button-text p-button-sm p-button-danger"
              @click="removeMetadata(index)"
            />
          </div>
          <div v-if="testData.metadata.length === 0" class="empty-message">
            No metadata. Click + to add.
          </div>
        </div>
      </div>

      <!-- Content Section -->
      <div class="test-data-section">
        <div class="section-header">
          <span>Content</span>
          <Button
            icon="pi pi-plus"
            class="p-button-text p-button-sm"
            @click="addContent"
          />
        </div>
        <div class="content-list">
          <div v-if="testData.content.length > 0" class="content-header">
            <span class="content-name">name</span>
            <span class="content-media-type">mediaType</span>
            <span class="content-size">size</span>
            <span class="content-actions"></span>
          </div>
          <div v-for="(item, index) in testData.content" :key="index" class="content-item">
            <InputText
              v-model="item.name"
              placeholder="name"
              class="content-name"
            />
            <InputText
              v-model="item.mediaType"
              placeholder="mediaType"
              class="content-media-type"
            />
            <InputNumber
              v-model="item.size"
              class="content-size"
              :min="0"
            />
            <Button
              icon="pi pi-times"
              class="p-button-text p-button-sm p-button-danger"
              @click="removeContent(index)"
            />
          </div>
          <div v-if="testData.content.length === 0" class="empty-message">
            No content. Click + to add.
          </div>
        </div>
      </div>
    </div>

    <!-- Template-specific fields -->
    <div v-if="mode === 'TEMPLATE'" class="template-fields">
      <div class="template-field">
        <label>deltaFileName:</label>
        <InputText v-model="testData.deltaFileName" placeholder="test-file.dat" />
      </div>
      <div class="template-field">
        <label>did:</label>
        <InputText v-model="testData.did" placeholder="00000000-0000-0000-0000-000000000000" />
      </div>
    </div>

    <!-- Result -->
    <div v-if="loading" class="test-result loading">
      <i class="pi pi-spin pi-spinner"></i>
      <span>Evaluating...</span>
    </div>
    <div v-else-if="!expression" class="test-result empty">
      <i class="pi pi-info-circle"></i>
      <span>Enter an expression to see the result</span>
    </div>
    <div v-else-if="testResult" class="test-result" :class="{ success: testResult.valid, error: !testResult.valid }">
      <div class="result-header">
        <i :class="testResult.valid ? 'pi pi-check-circle' : 'pi pi-times-circle'"></i>
        <span>{{ testResult.valid ? 'Result' : 'Error' }}</span>
      </div>
      <div class="result-value">
        <code>{{ testResult.valid ? testResult.result : testResult.errorMessage }}</code>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue';
import { useDebounceFn } from '@vueuse/core';
import Button from 'primevue/button';
import Dropdown from 'primevue/dropdown';
import InputNumber from 'primevue/inputnumber';
import InputText from 'primevue/inputtext';
import useSpelTest, { SpelExpressionMode, SpelTestResult, SpelTestDataInput } from '@/composables/useSpelTest';
import useDeltaFiles from '@/composables/useDeltaFiles';
import { computeOutputMetadata, computeOutputContent } from '@/utils/deltaFileUtils';

type FlowOption = {
  label: string;
  value: any;
};

const props = defineProps<{
  expression: string;
  mode: SpelExpressionMode;
}>();

const { testExpression } = useSpelTest();
const { data: deltaFileData, getDeltaFile } = useDeltaFiles();
const loading = ref(false);
const loadingDid = ref(false);
const loadDid = ref('');
const loadError = ref<string | null>(null);
const testResult = ref<SpelTestResult | null>(null);
const availableFlows = ref<FlowOption[]>([]);
const selectedFlow = ref<FlowOption | null>(null);

const testData = reactive<{
  metadata: Array<{ key: string; value: string }>;
  content: Array<{ name: string; mediaType: string; size: number }>;
  deltaFileName: string;
  did: string;
}>({
  metadata: [
    { key: 'type', value: 'order' },
    { key: 'customer', value: 'acme-corp' }
  ],
  content: [
    { name: 'data.json', mediaType: 'application/json', size: 1024 }
  ],
  deltaFileName: 'test-file.dat',
  did: '00000000-0000-0000-0000-000000000000'
});

const addMetadata = () => {
  testData.metadata.push({ key: '', value: '' });
};

const removeMetadata = (index: number) => {
  testData.metadata.splice(index, 1);
};

const addContent = () => {
  testData.content.push({ name: '', mediaType: 'application/octet-stream', size: 0 });
};

const removeContent = (index: number) => {
  testData.content.splice(index, 1);
};

const loadFromDid = async () => {
  if (!loadDid.value) return;

  loadingDid.value = true;
  loadError.value = null;
  availableFlows.value = [];
  selectedFlow.value = null;

  try {
    await getDeltaFile(loadDid.value.trim());

    const flows = (deltaFileData as any).flows;
    if (!flows || flows.length === 0) {
      loadError.value = 'DeltaFile has no flows';
      return;
    }

    // Build flow options with metadata count info (using computed output values)
    availableFlows.value = flows.map((flow: any) => {
      const metadata = computeOutputMetadata(flow);
      const content = computeOutputContent(flow);
      return {
        label: `${flow.type}: ${flow.name} (${Object.keys(metadata).length} metadata, ${content.length} content)`,
        value: flow
      };
    });

    // Auto-select first flow
    if (availableFlows.value.length > 0) {
      selectedFlow.value = availableFlows.value[0];
      loadFlowData(availableFlows.value[0].value);
    }

  } catch (e) {
    loadError.value = 'Failed to load DeltaFile. Check that the DID exists.';
  } finally {
    loadingDid.value = false;
  }
};

const onFlowSelected = () => {
  if (selectedFlow.value) {
    loadFlowData(selectedFlow.value.value);
  }
};

const loadFlowData = (flow: any) => {
  // Use shared utilities for metadata/content resolution
  const metadata = computeOutputMetadata(flow);
  testData.metadata = Object.entries(metadata)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => ({
      key,
      value: String(value)
    }));

  const content = computeOutputContent(flow);
  testData.content = content.map((c: any) => ({
    name: c.name || '',
    mediaType: c.mediaType || 'application/octet-stream',
    size: c.size || 0
  }));

  // Load deltaFileName and did
  testData.deltaFileName = (deltaFileData as any).name || '';
  testData.did = loadDid.value.trim();
};

const runTest = async () => {
  if (!props.expression) {
    testResult.value = null;
    return;
  }

  loading.value = true;

  const data: SpelTestDataInput = {
    metadata: testData.metadata.filter(m => m.key),
    content: testData.content.filter(c => c.name),
    deltaFileName: testData.deltaFileName || undefined,
    did: testData.did || undefined
  };

  try {
    testResult.value = await testExpression(props.expression, props.mode, data);
  } catch (e) {
    testResult.value = { valid: false, errorMessage: 'Test failed' };
  } finally {
    loading.value = false;
  }
};

const debouncedRunTest = useDebounceFn(runTest, 500);

// Watch expression changes
watch(() => props.expression, () => {
  debouncedRunTest();
}, { immediate: true });

// Watch test data changes (deep watch)
watch(testData, () => {
  debouncedRunTest();
}, { deep: true });
</script>

<style scoped>
.spel-test-panel {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.load-from-did {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.did-input {
  flex: 1;
  font-size: 0.85rem;
}

.load-error {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--red-600);
  font-size: 0.85rem;
  padding: 0.5rem;
  background: var(--red-50);
  border-radius: 4px;
}

.flow-picker {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.flow-picker label {
  font-size: 0.85rem;
  color: var(--text-color-secondary);
}

.flow-dropdown {
  flex: 1;
  font-size: 0.85rem;
}

.test-data-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

@media (max-width: 768px) {
  .test-data-grid {
    grid-template-columns: 1fr;
  }
}

.test-data-section {
  border: 1px solid var(--surface-200);
  border-radius: 4px;
  padding: 0.75rem;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
  font-weight: 600;
  font-size: 0.9rem;
}

.metadata-list,
.content-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.metadata-item,
.content-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.metadata-key,
.metadata-value {
  flex: 1;
  font-size: 0.85rem;
}

.metadata-separator {
  color: var(--text-color-secondary);
}

.content-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  padding-bottom: 0.25rem;
  border-bottom: 1px solid var(--surface-200);
  margin-bottom: 0.25rem;
}

.content-name {
  flex: 2;
  font-size: 0.85rem;
}

.content-media-type {
  flex: 2;
  font-size: 0.85rem;
}

.content-size {
  flex: 1;
  min-width: 70px;
  font-size: 0.85rem;
}

.content-actions {
  width: 32px;
}

.content-size :deep(.p-inputnumber-input) {
  width: 100%;
}

.empty-message {
  color: var(--text-color-secondary);
  font-size: 0.85rem;
  font-style: italic;
  padding: 0.5rem 0;
}

.template-fields {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.template-field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.template-field label {
  font-size: 0.85rem;
  color: var(--text-color-secondary);
}

.template-field :deep(.p-inputtext) {
  font-size: 0.85rem;
}

.test-result {
  padding: 1rem;
  border-radius: 4px;
}

.test-result.loading,
.test-result.empty {
  background: var(--surface-100);
  color: var(--text-color-secondary);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.test-result.success {
  background: var(--green-50);
  border: 1px solid var(--green-200);
}

.test-result.error {
  background: var(--red-50);
  border: 1px solid var(--red-200);
}

.result-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.test-result.success .result-header {
  color: var(--green-700);
}

.test-result.error .result-header {
  color: var(--red-700);
}

.result-value code {
  font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
  font-size: 0.9rem;
  background: rgba(255, 255, 255, 0.5);
  padding: 0.25rem 0.5rem;
  border-radius: 3px;
}
</style>
