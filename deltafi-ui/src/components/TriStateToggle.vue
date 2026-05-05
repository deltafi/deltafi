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
  <button
    type="button"
    class="tri-state-toggle"
    :class="[stateClass, { disabled }]"
    role="switch"
    :aria-checked="ariaChecked"
    :disabled="disabled"
    @click="onTrackClick"
    @keydown.left.prevent="setValue(false)"
    @keydown.right.prevent="setValue(true)"
    @keydown.space.prevent="cycleNext"
  >
    <span class="track">
      <span class="thumb" />
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(defineProps<{
  modelValue: boolean | null;
  disabled?: boolean;
}>(), {
  disabled: false,
});

const emit = defineEmits<{
  'update:modelValue': [value: boolean | null];
}>();

const stateClass = computed(() => {
  if (props.modelValue === true) return 'state-true';
  if (props.modelValue === false) return 'state-false';
  return 'state-null';
});

const ariaChecked = computed(() => {
  if (props.modelValue === true) return 'true';
  if (props.modelValue === false) return 'false';
  return 'mixed';
});

const setValue = (val: boolean | null) => {
  if (props.disabled) return;
  emit('update:modelValue', val);
};

const onTrackClick = (e: MouseEvent) => {
  if (props.disabled) return;
  const target = e.currentTarget as HTMLElement;
  const rect = target.getBoundingClientRect();
  const clickX = e.clientX - rect.left;
  setValue(clickX > rect.width / 2);
};

const cycleNext = () => {
  if (props.modelValue === null) setValue(true);
  else if (props.modelValue === true) setValue(false);
  else setValue(null);
};
</script>

<style scoped>
.tri-state-toggle {
  background: transparent;
  border: none;
  padding: 0;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
}

.tri-state-toggle:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}

.tri-state-toggle.disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.track {
  position: relative;
  width: 3rem;
  height: 1.5rem;
  border-radius: 999px;
  background: var(--surface-300);
  transition: background-color 0.2s, border-color 0.2s;
  box-sizing: border-box;
}

.thumb {
  position: absolute;
  top: 0.125rem;
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  transition: left 0.2s, background-color 0.2s, border-color 0.2s, opacity 0.2s;
  box-sizing: border-box;
}

/* False — thumb at left, neutral track */
.state-false .track {
  background: var(--surface-400);
}
.state-false .thumb {
  left: 0.125rem;
}

/* True — thumb at right, primary track */
.state-true .track {
  background: var(--primary-color);
}
.state-true .thumb {
  left: calc(100% - 1.375rem);
}

/* Null (unset) — thumb centered, diagonal-stripe track to read as indeterminate */
.state-null .track {
  background-color: var(--surface-200);
  background-image: repeating-linear-gradient(
    -45deg,
    var(--surface-400) 0,
    var(--surface-400) 2px,
    transparent 2px,
    transparent 6px
  );
}
.state-null .thumb {
  left: calc(50% - 0.625rem);
  background: #ffffff;
}
</style>
