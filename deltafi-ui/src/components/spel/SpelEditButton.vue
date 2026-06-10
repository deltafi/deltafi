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

<!--
  A reusable "edit this value with a SpEL expression" affordance: an icon button that opens a dialog
  containing the SpEL expression editor. Designed to be appended next to an existing literal control
  (input, toggle, etc.) so a field keeps its normal editing while gaining optional expression entry.
  Shared by the string, boolean, and map (additionalProperties) renderers so the markup/state/CSS
  live in one place.
-->
<template>
  <Button v-tooltip.top="tooltip" icon="fas fa-code" :class="variant === 'inline' ? 'spel-button-inline' : 'spel-button'" :text="variant === 'inline'" :rounded="variant === 'inline'" :disabled="disabled" @click="open" />

  <Dialog v-model:visible="visible" :header="'Edit ' + label" modal :style="{ width: '70vw', maxWidth: '900px' }">
    <SpelExpressionEditor v-if="visible" v-model="draft" :mode="mode" :placeholder="placeholder" :disabled="disabled" />
    <template #footer>
      <Button label="Cancel" text @click="visible = false" />
      <Button label="Apply" @click="apply" />
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import _ from "lodash";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import SpelExpressionEditor from "./SpelExpressionEditor.vue";

const props = withDefaults(
  defineProps<{
    modelValue?: unknown;
    disabled?: boolean;
    label?: string;
    mode?: "TEMPLATE" | "CONDITION";
    placeholder?: string;
    tooltip?: string;
    // "append" renders a solid button flush in an input group; "inline" renders a subtle icon button
    // suited to sitting next to a standalone control (e.g. a toggle).
    variant?: "append" | "inline";
  }>(),
  {
    modelValue: "",
    disabled: false,
    label: "value",
    mode: "TEMPLATE",
    placeholder: "Enter value with {{ expressions }}...",
    tooltip: "Edit with expression editor",
    variant: "append",
  }
);

const emit = defineEmits<{ (e: "update:modelValue", value: string): void }>();

const visible = ref(false);
const draft = ref("");

const open = () => {
  draft.value = _.isString(props.modelValue) ? props.modelValue : "";
  visible.value = true;
};

const apply = () => {
  emit("update:modelValue", draft.value);
  visible.value = false;
};
</script>

<style scoped>
.spel-button {
  background: var(--primary-color) !important;
  border-color: var(--primary-color) !important;
  color: white !important;
  border-top-left-radius: 0 !important;
  border-bottom-left-radius: 0 !important;
  flex-shrink: 0;
}

.spel-button:hover {
  background: var(--primary-700) !important;
  border-color: var(--primary-700) !important;
}

.spel-button-inline {
  color: var(--text-color-secondary) !important;
}
</style>
