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
<!-- ABOUTME: Component for selecting content from a list and viewing it. -->
<!-- ABOUTME: Builds ContentPointer from flow/action context and passes to ContentViewer. -->

<template>
  <div class="content-selector-container">
    <div v-if="showListbox" class="left-column">
      <Listbox v-model="selectedItem" :options="listboxItems" filter option-label="name">
        <template #option="{ option }">
          <div class="listbox-item">
            <span class="listbox-item-name">{{ option.name }}</span>
            <span v-if="option.tags?.length" class="listbox-item-tags">
              <span class="badge badge-pill badge-primary tag-badge">{{ truncateTag(option.tags[0]) }}</span>
              <span v-if="option.tags.length > 1" class="badge badge-pill badge-secondary tag-badge">+{{ option.tags.length - 1 }}</span>
            </span>
          </div>
        </template>
      </Listbox>
    </div>
    <div class="right-column">
      <ContentViewer :content="selectedPointer">
        <template v-if="$slots['toolbar-start']" #toolbar-start>
          <slot name="toolbar-start" />
        </template>
      </ContentViewer>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from "vue";
import Listbox from "primevue/listbox";
import ContentViewer from "@/components/ContentViewer.vue";

const emit = defineEmits(["contentSelected"]);
const props = defineProps({
  did: {
    type: String,
    required: true,
  },
  flowNumber: {
    type: Number,
    required: true,
  },
  actionIndex: {
    type: Number,
    default: undefined,
  },
  contentIndex: {
    type: Number,
    default: undefined,
  },
  content: {
    type: Array,
    required: true,
  },
});

const { did, flowNumber, actionIndex, contentIndex, content } = reactive(props);
const showListbox = computed(() => content.length > 1);
const listboxItems = computed(() => {
  return content.map((c, index) => {
    return {
      index: index,
      name: `${index} : ${c.name}`,
      tags: c.tags,
    };
  });
});

const truncateTag = (tag, maxLength = 12) => {
  if (!tag || tag.length <= maxLength) return tag;
  return tag.substring(0, maxLength) + "…";
};
const selectedItem = ref(listboxItems.value[0]);

const selectedPointer = computed(() => {
  const c = content[selectedItem.value.index];
  return {
    did: did,
    flowNumber: flowNumber,
    actionIndex: actionIndex,
    contentIndex: contentIndex ?? selectedItem.value.index,
    name: c.name,
    mediaType: c.mediaType,
    totalSize: c.size,
    tags: c.tags,
  };
});

watch(selectedItem, (newItem, oldValue) => {
  if (newItem === null) selectedItem.value = oldValue;
  emit("contentSelected", selectedPointer.value);
});
</script>

<style>
.content-selector-container {
  display: flex;
  height: 100%;

  .p-listbox {
    border-radius: 4px 4px 0 0;
    height: 100%;
    max-height: 100%;
    overflow-y: auto;
  }

  .left-column {
    flex: 2;
    padding-right: 1rem;
  }

  .right-column {
    flex: 5;
  }

  .listbox-item {
    display: grid;
    grid-template-columns: 1fr auto;
    align-items: center;
    width: 100%;
    gap: 0.5rem;
  }

  .listbox-item-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .listbox-item-tags {
    display: flex;
    justify-content: flex-end;
    gap: 0.25rem;
  }

  .tag-badge {
    font-size: 0.7rem;
    padding: 0.15rem 0.4rem;
  }

  .p-listbox-item.p-highlight .tag-badge.badge-primary {
    background-color: white !important;
    color: var(--primary-color, #007bff) !important;
  }

  .p-listbox-item.p-highlight .tag-badge.badge-secondary {
    background-color: white !important;
    color: #6c757d !important;
  }
}
</style>
