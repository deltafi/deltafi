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

<!-- ABOUTME: PrimeVue MultiSelect wrapper that pins the currently-selected -->
<!-- ABOUTME: items into a "Selected" group at the top of the dropdown when -->
<!-- ABOUTME: opened, removing them from their original groups (no duplicates). -->

<template>
  <MultiSelect
    ref="msInner"
    :model-value="modelValue"
    :options="effectiveOptions"
    :option-group-label="optionGroupLabel"
    :option-group-children="optionGroupChildren"
    :option-label="optionLabel"
    :option-value="optionValue"
    @update:model-value="(value) => emit('update:modelValue', value)"
    @before-show="onBeforeShow"
    @show="onShow"
    @hide="onHide"
  />
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import MultiSelect from "primevue/multiselect";

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  options: { type: Array, default: () => [] },
  // Optional. When provided, options are treated as already grouped: a
  // synthetic "Selected" group is prepended and selected items are removed
  // from their original groups (groups that empty out are dropped). When
  // omitted, options are treated as flat and selected items are simply
  // floated to the top of the list (no synthetic group header).
  optionGroupLabel: { type: String, default: null },
  optionGroupChildren: { type: String, default: null },
  optionLabel: { type: String, default: null },
  optionValue: { type: String, default: null },
  selectedGroupLabel: { type: String, default: "Selected" },
});

const emit = defineEmits(["update:modelValue", "before-show", "show", "hide"]);

// Snapshot is captured the moment the overlay opens and stays frozen for the
// duration of the open session, so toggling items doesn't reshuffle the list
// under the user's cursor. A fresh snapshot is taken on each open.
const msInner = ref(null);
const isOpen = ref(false);
const snapshot = ref([]);
let suppressInitialScroll = false;

function onBeforeShow(event) {
  isOpen.value = true;
  snapshot.value = Array.isArray(props.modelValue) ? [...props.modelValue] : [];
  suppressInitialScroll = true;
  emit("before-show", event);
}

// PrimeVue calls scrollInView twice during open: once synchronously from
// onFocus (before the overlay is in the DOM, harmless no-op) and once from
// onOverlayEnter (the visible scroll). Hold the suppression flag across both
// by clearing it only after the open animation completes.
function onShow(event) {
  suppressInitialScroll = false;
  emit("show", event);
}

function onHide(event) {
  isOpen.value = false;
  snapshot.value = [];
  suppressInitialScroll = false;
  emit("hide", event);
}

// Patch the inner MultiSelect's scrollInView. PrimeVue computes its target
// index synchronously during show() against `visibleOptions`, but our
// `effectiveOptions` (with the Selected group prepended) hasn't reactively
// flushed yet — so PrimeVue's target points into the old list and its
// scroll-into-view (queued in $nextTick from onOverlayEnter) lands mid-list.
// Doing the correction post-show produces a visible flash + jump. By
// intercepting this one call and replacing it with a scroll-to-top, we get
// a single clean paint with the Selected group in view. Keyboard navigation
// still calls scrollInView later — those calls pass through unchanged.
onMounted(() => {
  const inner = msInner.value;
  if (!inner || typeof inner.scrollInView !== "function") return;
  const original = inner.scrollInView;
  inner.scrollInView = function (...args) {
    if (suppressInitialScroll) {
      // Don't reset the flag here — it gets cleared on `show` (open animation
      // complete). PrimeVue calls this method more than once during open and
      // we need to suppress all of them, not just the first.
      this.$nextTick(() => {
        const container = this.list?.parentElement;
        if (container) container.scrollTop = 0;
      });
      return;
    }
    return original.apply(this, args);
  };
});

function valueOf(item) {
  if (!props.optionValue) return item;
  return item[props.optionValue];
}

const isGrouped = computed(() => Boolean(props.optionGroupLabel && props.optionGroupChildren));

const effectiveOptions = computed(() => {
  if (!isOpen.value || snapshot.value.length === 0) return props.options;

  const snapshotSet = new Set(snapshot.value);

  if (isGrouped.value) {
    // Walk the original groups in order, pulling snapshotted items into a
    // "Selected" bucket and dropping any group that empties out.
    const selectedItems = [];
    const remainingGroups = [];

    for (const group of props.options) {
      const remaining = [];
      for (const item of group[props.optionGroupChildren] || []) {
        if (snapshotSet.has(valueOf(item))) {
          selectedItems.push(item);
        } else {
          remaining.push(item);
        }
      }
      if (remaining.length > 0) {
        remainingGroups.push({ ...group, [props.optionGroupChildren]: remaining });
      }
    }

    if (selectedItems.length === 0) return props.options;
    return [
      { [props.optionGroupLabel]: props.selectedGroupLabel, [props.optionGroupChildren]: selectedItems },
      ...remainingGroups,
    ];
  }

  // Flat options: float selected items to the top in their original order.
  const selected = [];
  const remaining = [];
  for (const item of props.options) {
    if (snapshotSet.has(valueOf(item))) selected.push(item);
    else remaining.push(item);
  }
  if (selected.length === 0) return props.options;
  return [...selected, ...remaining];
});
</script>
