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
<!-- ABOUTME: Reference documentation panel for SpEL expressions. -->
<!-- ABOUTME: Shows available variables, methods, and example expressions. -->

<template>
  <div class="spel-reference">
    <TabView>
      <TabPanel header="Variables">
        <DataTable :value="variables" class="p-datatable-sm">
          <Column field="name" header="Variable" style="width: 30%">
            <template #body="{ data }">
              <code class="clickable" @click="insert(data.insertText || data.name)">{{ data.name }}</code>
            </template>
          </Column>
          <Column field="type" header="Type" style="width: 25%" />
          <Column field="description" header="Description" />
        </DataTable>
      </TabPanel>

      <TabPanel header="Methods">
        <DataTable :value="methods" class="p-datatable-sm">
          <Column field="name" header="Method" style="width: 35%">
            <template #body="{ data }">
              <code class="clickable" @click="insert(data.insertText || data.name)">{{ data.name }}</code>
            </template>
          </Column>
          <Column field="returns" header="Returns" style="width: 15%" />
          <Column field="description" header="Description" />
        </DataTable>
      </TabPanel>

      <TabPanel header="Operators">
        <DataTable :value="operators" class="p-datatable-sm">
          <Column field="operator" header="Operator" style="width: 15%">
            <template #body="{ data }">
              <code class="clickable" @click="insert(data.insertText || data.operator)">{{ data.operator }}</code>
            </template>
          </Column>
          <Column field="description" header="Description" style="width: 35%" />
          <Column field="example" header="Example">
            <template #body="{ data }">
              <code class="clickable" @click="insert(data.example)">{{ data.example }}</code>
            </template>
          </Column>
        </DataTable>
      </TabPanel>

      <TabPanel header="Examples">
        <div class="examples-list">
          <div v-for="example in examples" :key="example.expression" class="example-item clickable" @click="insert(example.expression)">
            <div class="example-expression">
              <code>{{ example.expression }}</code>
            </div>
            <div class="example-description">{{ example.description }}</div>
          </div>
        </div>
      </TabPanel>
    </TabView>
    <div class="click-hint">
      <i class="pi pi-info-circle"></i> Click any code to insert it into the editor
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import Column from 'primevue/column';
import DataTable from 'primevue/datatable';
import TabPanel from 'primevue/tabpanel';
import TabView from 'primevue/tabview';
import type { SpelExpressionMode } from '@/composables/useSpelTest';

const props = defineProps<{
  mode: SpelExpressionMode;
}>();

const emit = defineEmits<{
  insert: [text: string, cursorOffset?: number];
}>();

const insert = (text: string) => {
  emit('insert', text);
};

const conditionVariables = [
  { name: 'metadata', type: 'Map<String, String>', description: 'DeltaFile metadata key-value pairs' },
  { name: "metadata['key']", type: 'String', description: 'Get metadata value by key' },
  { name: 'content', type: 'List<Content>', description: 'List of content items' },
  { name: 'content[0].name', type: 'String', description: 'Name of first content item' },
  { name: 'content[0].mediaType', type: 'String', description: 'Media type of first content item' },
  { name: 'content[0].getSize()', type: 'long', description: 'Size in bytes of first content item' },
];

const templateVariables = [
  { name: 'metadata', type: 'Map<String, String>', description: 'DeltaFile metadata key-value pairs' },
  { name: "metadata['key']", type: 'String', description: 'Get metadata value by key' },
  { name: 'deltaFileName', type: 'String', description: 'Name of the DeltaFile' },
  { name: 'did', type: 'UUID', description: 'DeltaFile ID' },
  { name: 'content', type: 'List<Content>', description: 'List of content items' },
  { name: 'content[0].name', type: 'String', description: 'Name of first content item' },
  { name: 'content[0].mediaType', type: 'String', description: 'Media type of first content item' },
  { name: 'content[0].getSize()', type: 'long', description: 'Size in bytes of first content item' },
  { name: 'actionContext.flowName', type: 'String', description: 'Name of current flow' },
  { name: 'actionContext.actionName', type: 'String', description: 'Name of current action' },
  { name: 'actionContext.dataSource', type: 'String', description: 'Name of data source' },
];

const variables = computed(() =>
  props.mode === 'CONDITION' ? conditionVariables : templateVariables
);

const conditionMethods = [
  { name: "hasMediaType('type')", returns: 'boolean', description: 'Check if any content has the given media type' },
  { name: "metadata.containsKey('key')", returns: 'boolean', description: 'Check if metadata contains key' },
  { name: 'content.size()', returns: 'int', description: 'Number of content items' },
  { name: 'content.isEmpty()', returns: 'boolean', description: 'Check if content list is empty' },
  { name: '.gt(number)', returns: 'boolean', description: 'Check if value is greater than number' },
  { name: '.gte(number)', returns: 'boolean', description: 'Check if value is greater than or equal to number' },
  { name: '.lt(number)', returns: 'boolean', description: 'Check if value is less than number' },
  { name: '.lte(number)', returns: 'boolean', description: 'Check if value is less than or equal to number' },
  { name: ".toLowerCase()", returns: 'String', description: 'Convert string to lowercase' },
  { name: ".toUpperCase()", returns: 'String', description: 'Convert string to uppercase' },
  { name: ".contains('str')", returns: 'boolean', description: 'Check if string contains substring' },
  { name: ".startsWith('str')", returns: 'boolean', description: 'Check if string starts with prefix' },
  { name: ".endsWith('str')", returns: 'boolean', description: 'Check if string ends with suffix' },
  { name: ".matches('regex')", returns: 'boolean', description: 'Match against regular expression' },
];

const templateMethods = [
  { name: 'now()', returns: 'String', description: 'Current timestamp in ISO format' },
  { name: ".toLowerCase()", returns: 'String', description: 'Convert string to lowercase' },
  { name: ".toUpperCase()", returns: 'String', description: 'Convert string to uppercase' },
  { name: ".replace('old', 'new')", returns: 'String', description: 'Replace substring' },
  { name: ".substring(start, end)", returns: 'String', description: 'Extract substring' },
];

const methods = computed(() =>
  props.mode === 'CONDITION' ? conditionMethods : templateMethods
);

const operators = [
  { operator: '==', description: 'Equal', example: "metadata['type'] == 'order'" },
  { operator: '!=', description: 'Not equal', example: "metadata['type'] != 'test'" },
  { operator: '<, >, <=, >=', description: 'Comparison', example: 'content.size() > 1' },
  { operator: '&&', description: 'Logical AND', example: "a == 'x' && b == 'y'" },
  { operator: '||', description: 'Logical OR', example: "a == 'x' || a == 'y'" },
  { operator: '!', description: 'Logical NOT', example: "!metadata.containsKey('x')" },
  { operator: '?:', description: 'Elvis (null-safe)', example: "metadata['x'] ?: 'default'" },
  { operator: '?.', description: 'Safe navigation', example: "metadata['x']?.toLowerCase()" },
  { operator: '.?[cond]', description: 'Collection filter', example: "content.?[name.endsWith('.xml')]" },
  { operator: '.![expr]', description: 'Collection projection', example: 'content.![name]' },
];

const conditionExamples = [
  { expression: "metadata['type'] == 'order'", description: 'Check if metadata type equals "order"' },
  { expression: "metadata.containsKey('priority')", description: 'Check if priority key exists' },
  { expression: "hasMediaType('application/json')", description: 'Check if any content is JSON' },
  { expression: 'content.size() > 1', description: 'Check if there are multiple content items' },
  { expression: "metadata['priority'].gt(5)", description: 'Check if priority is greater than 5' },
  { expression: "metadata['count'].gte(10) && metadata['count'].lte(100)", description: 'Check if count is between 10 and 100' },
  { expression: "metadata['type'] == 'order' && hasMediaType('application/json')", description: 'Combined conditions with AND' },
  { expression: "!content.?[name.endsWith('.xml')].isEmpty()", description: 'Check if any content has .xml extension' },
  { expression: "metadata['name'].toLowerCase().contains('test')", description: 'Case-insensitive substring check' },
];

const templateExamples = [
  { expression: "{{ metadata['customer'] }}", description: 'Insert customer metadata value' },
  { expression: '{{ deltaFileName }}', description: 'Insert the DeltaFile name' },
  { expression: '{{ did }}', description: 'Insert the DeltaFile ID' },
  { expression: '{{ now() }}', description: 'Insert current timestamp' },
  { expression: "output/{{ metadata['customer'] }}/{{ deltaFileName }}", description: 'Build dynamic path' },
  { expression: '{{ deltaFileName.toLowerCase() }}', description: 'Insert lowercase filename' },
  { expression: '{{ content[0].name }}', description: 'Insert name of first content item' },
  { expression: '{{ content[0].mediaType }}', description: 'Insert media type of first content' },
];

const examples = computed(() =>
  props.mode === 'CONDITION' ? conditionExamples : templateExamples
);
</script>

<style scoped>
.spel-reference {
  font-size: 0.9rem;
}

.spel-reference :deep(.p-datatable) {
  font-size: 0.85rem;
}

.spel-reference :deep(.p-datatable .p-datatable-thead > tr > th) {
  padding: 0.5rem;
}

.spel-reference :deep(.p-datatable .p-datatable-tbody > tr > td) {
  padding: 0.5rem;
}

.spel-reference code {
  background: var(--surface-100);
  padding: 0.15rem 0.35rem;
  border-radius: 3px;
  font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
  font-size: 0.85em;
}

.examples-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.example-item {
  padding: 0.75rem;
  background: var(--surface-50);
  border-radius: 4px;
}

.example-expression {
  margin-bottom: 0.5rem;
}

.example-expression code {
  font-size: 0.9em;
}

.example-description {
  color: var(--text-color-secondary);
  font-size: 0.85rem;
}

.clickable {
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}

.clickable:hover {
  background: var(--primary-color);
  color: var(--primary-color-text);
}

.example-item.clickable:hover {
  background: var(--surface-100);
}

.example-item.clickable:hover code {
  background: var(--primary-color);
  color: var(--primary-color-text);
}

.click-hint {
  margin-top: 1rem;
  padding: 0.5rem;
  font-size: 0.8rem;
  color: var(--text-color-secondary);
  text-align: center;
  border-top: 1px solid var(--surface-200);
}

.click-hint i {
  margin-right: 0.25rem;
}
</style>
