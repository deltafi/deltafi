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
  <div class="sentence-fill" :style="{ '--blank-min': minBlankWidth + 'px', '--blank-h': '1.8em' }">
    <Card :pt="{ body: { class: 'py-0' } }">
      <template #content>
        <p class="preview" aria-live="polite">
          <template v-for="(part, i) in sentenceTokens" :key="i">
            <span v-if="part.type === 'text'">{{ part.value }}</span>
            <span
              v-else
              class="blank"
              :class="{
                filled: hasFieldValue(part.name),
                empty: !hasFieldValue(part.name),
                invalid: shouldShowError(part.name),
                editing: editingFieldName === part.name,
              }"
              role="button"
              tabindex="0"
              :aria-label="getFieldLabel(part.name)"
              @click.stop="beginEditing(part.name)"
              @keydown.enter.prevent="beginEditing(part.name)"
            >
              <template v-if="editingFieldName === part.name">
                <InputText v-if="getFieldType(part.name) === 'text'" v-model="formValues[part.name]" class="inline-editor" :placeholder="getFieldLabel(part.name)" :ref="(el) => registerInlineRef(part.name, el)" @update:modelValue="immediateValidate" @keydown.enter.stop.prevent="commitEdit()" @keydown.esc.stop.prevent="cancelEdit()" @keydown.tab.prevent="focusNextBlank(part.name)" @blur="commitEdit()" />

                <InputNumber v-else-if="getFieldType(part.name) === 'number'" v-model="formValues[part.name]" class="inline-editor" :placeholder="getFieldLabel(part.name)" :useGrouping="false" :ref="(el) => registerInlineRef(part.name, el)" @update:modelValue="immediateValidate" @keydown.enter.stop.prevent="commitEdit()" @keydown.esc.stop.prevent="cancelEdit()" @keydown.tab.prevent="focusNextBlank(part.name)" @blur="commitEdit()" />

                <Dropdown v-else v-model="formValues[part.name]" class="inline-editor" :options="getOptionsForField(part.name)" optionLabel="label" optionValue="value" showClear appendTo="body" :placeholder="getFieldLabel(part.name)" :ref="(el) => registerInlineRef(part.name, el)" @update:modelValue="onDropdownChange()" @change="onDropdownChange()" @clear="onDropdownChange()" @hide="commitEdit()" />
              </template>
              <template v-else>
                <span v-if="hasFieldValue(part.name)">{{ getPreviewValue(part.name) }}</span>
                <span v-else class="placeholder">{{ getFieldLabel(part.name) }}</span>
              </template>
            </span>
          </template>
        </p>
      </template>
    </Card>
  </div>
</template>

<script setup>
import { computed, reactive, watch, ref, nextTick } from "vue";
import Card from "primevue/card";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";
import InputNumber from "primevue/inputnumber";

const props = defineProps({
  template: { type: String, default: "Limit {name} to {maxAmount} {unit} every {durationSeconds} seconds." },
  modelValue: { type: Object, default: () => ({}) },
  fields: { type: Object, default: () => ({}) },
  minBlankWidth: { type: Number, default: 100 },
});
const emit = defineEmits(["update:modelValue", "update:valid", "validity"]);

const PLACEHOLDER_REGEX = /\{(\w+)\}/g;

const sentenceTokens = computed(() => {
  const parts = [];
  let last = 0;
  for (const match of props.template.matchAll(PLACEHOLDER_REGEX)) {
    const idx = match.index ?? 0;
    if (idx > last) parts.push({ type: "text", value: props.template.slice(last, idx) });
    parts.push({ type: "blank", name: match[1] });
    last = idx + match[0].length;
  }
  if (last < props.template.length) parts.push({ type: "text", value: props.template.slice(last) });
  return parts;
});

const placeholderNames = computed(() => Array.from(new Set(sentenceTokens.value.filter((t) => t.type === "blank").map((t) => t.name))));

const getFieldConfig = (name) => props.fields?.[name] ?? {};

const getFieldType = (name) => getFieldConfig(name).type ?? "text";

const getFieldLabel = (name) => getFieldConfig(name).label ?? name.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());

const getOptionsForField = (name) => {
  const raw = getFieldConfig(name).options ?? [];
  return raw.map((o) => (typeof o === "string" ? { label: o, value: o } : o));
};

const firstAppearanceIndexByName = computed(() => {
  const map = {};
  sentenceTokens.value.forEach((t, i) => {
    if (t.type === "blank" && map[t.name] === undefined) map[t.name] = i;
  });
  return map;
});

const getFieldOrder = (name) => {
  const val = props.fields?.[name]?.order;
  return typeof val === "number" && Number.isFinite(val) ? val : null;
};

const orderedFieldKeys = computed(() => {
  const idx = firstAppearanceIndexByName.value;
  return placeholderNames.value.slice().sort((a, b) => {
    const ao = getFieldOrder(a),
      bo = getFieldOrder(b);
    const aHas = ao !== null,
      bHas = bo !== null;
    if (aHas && bHas && ao !== bo) return ao - bo;
    if (aHas !== bHas) return aHas ? -1 : 1;
    const ta = idx[a] ?? Number.POSITIVE_INFINITY,
      tb = idx[b] ?? Number.POSITIVE_INFINITY;
    if (ta !== tb) return ta - tb;
    return a.localeCompare(b);
  });
});

const formValues = reactive({ ...props.modelValue });
const defaultValueFor = (name) => (getFieldType(name) === "number" ? null : "");

watch(
  placeholderNames,
  (names) => {
    for (const name of names) {
      if (!(name in formValues)) formValues[name] = defaultValueFor(name);
      else if (getFieldType(name) === "number" && (formValues[name] === "" || typeof formValues[name] === "string")) {
        formValues[name] = formValues[name] === "" ? null : Number(formValues[name]);
      }
    }
  },
  { immediate: true }
);

watch(
  () => props.modelValue,
  (nv) => {
    Object.assign(formValues, nv || {});
    for (const name of placeholderNames.value) {
      if (getFieldType(name) === "number" && (formValues[name] === "" || typeof formValues[name] === "string")) {
        formValues[name] = formValues[name] === "" ? null : Number(formValues[name]);
      }
    }
  },
  { deep: true }
);

const getPreviewValue = (name) => {
  const val = formValues[name];
  if (getFieldType(name) === "dropdown") {
    const opt = getOptionsForField(name).find((o) => o.value === val);
    return opt ? opt.label : String(val ?? "");
  }
  return String(val ?? "");
};
const hasFieldValue = (name) => {
  const val = formValues[name];
  return !(val === "" || val === null || val === undefined);
};

const editingFieldName = ref(null);
const inlineRefs = ref({});
const previousValue = ref(null);

const registerInlineRef = (name, el) => {
  if (el) inlineRefs.value[name] = el;
};

const beginEditing = async (name) => {
  if (editingFieldName.value === name) return;
  previousValue.value = formValues[name];
  editingFieldName.value = name;
  await nextTick();
  const comp = inlineRefs.value[name];
  if (!comp) return;

  if (comp instanceof HTMLElement) {
    comp.focus();
    comp.select?.();
  } else {
    const root = comp.$el ?? comp;
    if (typeof comp.focus === "function") comp.focus();
    else root?.querySelector("input")?.focus();
    if (getFieldType(name) === "dropdown" && typeof comp.show === "function") comp.show();
  }
};

const commitEdit = () => {
  editingFieldName.value = null;
  immediateValidate();
};

const cancelEdit = () => {
  if (editingFieldName.value != null) {
    formValues[editingFieldName.value] = previousValue.value;
  }
  editingFieldName.value = null;
  immediateValidate();
};

const onDropdownChange = () => {
  immediateValidate();
  commitEdit();
};

const focusNextBlank = (current) => {
  const list = orderedFieldKeys.value;
  const idx = Math.max(0, list.indexOf(current));
  const next = list[(idx + 1) % list.length];
  beginEditing(next);
};

const validationErrors = ref({});
const isFormValid = computed(() => Object.keys(validationErrors.value).length === 0);
const showAllErrors = ref(false);

const requiredMessage = (name) => `${getFieldLabel(name)} is required.`;
const computeValidationErrors = () => {
  const next = {};
  for (const name of placeholderNames.value) {
    const cfg = getFieldConfig(name);
    const val = formValues[name];
    if (cfg.required && (val === "" || val === null || val === undefined)) {
      next[name] = requiredMessage(name);
      continue;
    }
    if (getFieldType(name) === "number") {
      if (val !== "" && val !== null && val !== undefined && Number.isNaN(Number(val))) {
        next[name] = `${getFieldLabel(name)} must be a number.`;
        continue;
      }
    }
  }
  validationErrors.value = next;
};

const shouldShowError = (name) => {
  const has = hasFieldValue(name);
  return !!validationErrors.value[name] && (has || showAllErrors.value);
};

const emitSnapshot = () => {
  emit("update:modelValue", { ...formValues });
  emit("update:valid", isFormValid.value);
  emit("validity", { isValid: isFormValid.value, errors: { ...validationErrors.value } });
};

const immediateValidate = async () => {
  await nextTick();
  computeValidationErrors();
  emitSnapshot();
};

watch(
  formValues,
  () => {
    computeValidationErrors();
    emitSnapshot();
  },
  { deep: true }
);

computeValidationErrors();

const validate = (opts) => {
  showAllErrors.value = !!(opts && opts.touchAll);
  computeValidationErrors();
  emitSnapshot();
  return { isValid: isFormValid.value, errors: { ...validationErrors.value } };
};
defineExpose({ validate, isValid: isFormValid, errors: validationErrors });
</script>

<style scoped>
.sentence-fill {
  display: grid;
  gap: 1rem;
}

.preview {
  font-size: 1.1rem;
  line-height: 1.9;
  word-wrap: anywhere;
}

.blank {
  --pad-x: 0.35rem;
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;

  min-width: var(--blank-min, 100px);
  height: var(--blank-h, 1.8em);
  min-height: var(--blank-h, 1.8em);

  padding: 0 var(--pad-x);
  margin: 0 0.12rem;
  cursor: text;

  font: inherit;
  line-height: 1.6;
}
.blank::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  bottom: -0.18em;
  background: #cfcfcf;
  transition: background-color 0.15s ease;
}
.blank.filled::after {
  background: #65b36b;
}
.blank:hover::after,
.blank.editing::after,
.blank:focus-visible::after {
  background: #7aa7ff;
}
.blank.invalid::after {
  background: #ef4444;
}
.blank.empty {
  justify-content: center;
  text-align: center;
}

.placeholder {
  max-width: 24ch;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #9aa0a6;
  font-style: italic;
  line-height: 1.2;
}

.inline-editor {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  height: var(--blank-h, 1.8em);
  line-height: var(--blank-h, 1.8em);
  font: inherit;
}

:deep(.inline-editor.p-inputtext),
:deep(.inline-editor .p-inputtext) {
  background: transparent;
  border: none;
  box-shadow: none;
  padding: 0;
  height: var(--blank-h, 1.8em);
  line-height: var(--blank-h, 1.8em);
  font: inherit;
}

:deep(.inline-editor.p-inputnumber) {
  background: transparent;
  border: none;
  box-shadow: none;
  padding: 0;
  height: var(--blank-h, 1.8em);
  display: inline-flex;
  align-items: center;
  width: 140px;
}
:deep(.inline-editor.p-inputnumber-input) {
  background: transparent;
  border: none;
  box-shadow: none;
  padding: 0;
  height: var(--blank-h, 1.8em);
  line-height: var(--blank-h, 1.8em);
  font: inherit;
  width: 140px;
}

:deep(.inline-editor.p-dropdown) {
  background: transparent;
  border: none;
  box-shadow: none;
  height: var(--blank-h, 1.8em);
  min-height: var(--blank-h, 1.8em);
  padding: 0;
  align-items: center;
  width: 100%;
}
:deep(.inline-editor .p-dropdown-label) {
  padding: 0;
  height: var(--blank-h, 1.8em);
  line-height: var(--blank-h, 1.8em);
  font: inherit;
}
:deep(.inline-editor .p-dropdown-trigger) {
  width: 12;
  padding: 0;
}
:deep(.inline-editor .p-dropdown-trigger-icon) {
  margin: 0;
}

:deep(.inline-editor input),
:deep(.inline-editor .p-dropdown-label) {
  vertical-align: middle;
}

.blank.editing .placeholder {
  visibility: hidden;
}
</style>
