<template>
  <div class="content-viewer">
    <div class="content-viewer-container" :style="`height: ${maxHeight}; max-height: ${maxHeight}`">
      <div class="content-viewer-section">
        <Toolbar>
          <template #left>
            <Dropdown v-model="selectedRenderFormat" :options="renderFormats" option-label="name" class="mr-3" style="min-width: 12rem;" />
            <Button label="Highlight" :icon="toggleButtonIcon" class="p-button p-button-outlined p-button-secondary" :disabled="selectedRenderFormat.name !== 'UTF-8'" @click="onToggleClick" />
            <Button :label="contentReference.mediaType" class="p-button-text p-button-secondary" disabled />
          </template>
          <template #right>
            <Button :label="contentSize" class="p-button-text p-button-secondary" disabled />
            <Button label="Download" icon="fa fa-download" @click="download()" />
          </template>
        </Toolbar>
        <Message v-if="partialContent" severity="warn" class="m-0">Content size is over the preview limit. Only showing the first {{ formattedMaxPreviewSize }}.</Message>
        <div class="scrollable-content content-viewer-content">
          <div v-if="errors.length > 0">
            <Message v-for="error in errors" :key="error" severity="error" :closable="false" class="mb-3 mt-0">{{ error }}</Message>
          </div>
          <div v-else>
            <HighlightedCode v-if="loadingContent" :highlight="false" code="Loading..." />
            <div v-else-if="contentLoaded">
              <Message v-if="!contentAsString" severity="warn" class="m-0">No content to display.</Message>
              <HighlightedCode v-if="selectedRenderFormat.name === 'UTF-8'" :highlight="highlightCode" :code="contentAsString" :language="language" />
              <HighlightedCode v-if="selectedRenderFormat.name === 'Hexdump'" :highlight="false" :code="contentAsHexdump" />
            </div>
          </div>
          <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import HighlightedCode from "@/components/HighlightedCode.vue";
import useContent from "@/composables/useContent";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, ref, toRefs, defineProps, onMounted, watch } from "vue";

import Button from "primevue/button";
import Dropdown from 'primevue/dropdown';
import Message from "primevue/message";
import Toolbar from 'primevue/toolbar';
import ScrollTop from 'primevue/scrolltop';

import hexy from "hexy";

const props = defineProps({
  contentReference: {
    type: Object,
    required: true,
  },
  filename: {
    type: String,
    required: false,
    default: null
  },
  maxHeight: {
    type: String,
    required: false,
    default: '100%'
  }
});

const { formattedBytes } = useUtilFunctions();
const { contentReference, maxHeight, filename } = toRefs(props);
const maxPreviewSize = 100000; // 100kB
const contentLoaded = ref(false);
const highlightCode = ref(true);
const content = ref(new ArrayBuffer());
const decoder = new TextDecoder("utf-8");
const { downloadURL, loading: loadingContent, fetch: fetchContent, errors, data } = useContent();

const language = computed(() => {
  try {
    return contentReference.value.mediaType.split('/')[1];
  } catch {
    return null;
  }
})

const toggleButtonIcon = computed(() => highlightCode.value ? 'far fa-check-square' : 'far fa-square')
const onToggleClick = () => {
  highlightCode.value = !highlightCode.value;
};

const partialContent = computed(() => contentReference.value.size > maxPreviewSize);

const renderFormats = ref([
  { name: 'Hexdump' },
  { name: 'UTF-8' }
])
const selectedRenderFormat = ref(renderFormats.value[1])

const contentAsString = computed(() => decoder.decode(new Uint8Array(content.value)));

const contentAsHexdump = computed(() => {
  let buffer = Buffer.from(content.value);
  return hexy.hexy(buffer, {
    format: "twos",
    width: 16
  });
});

const embededContent = computed(() => "content" in contentReference.value);

const contentSize = computed(() => formattedBytes(contentReference.value.size));
const formattedMaxPreviewSize = computed(() => formattedBytes(maxPreviewSize));

const download = () => {
  if (embededContent.value) {
    downloadEmbededContent();
  } else {
    let url = downloadURL({
      ...contentReference.value,
      filename: filename.value
    });
    window.open(url);
  }
};

const loadContent = async () => {
  contentLoaded.value = false;
  if (embededContent.value) {
    loadEmbededContent();
    return;
  }
  let request = {
    ...contentReference.value,
    size: partialContent.value ? maxPreviewSize : contentReference.value.size,
  };
  try {
    await fetchContent(request);
  } catch {
    return;
  }
  content.value = await data.value.arrayBuffer();
  contentLoaded.value = true;
};

const loadEmbededContent = () => {
  const str = contentReference.value.content;
  content.value = new ArrayBuffer(str.length * 2);
  const bufView = new Uint16Array(content.value);
  for (var i = 0, strLen = str.length; i < strLen; i++) {
    bufView[i] = str.charCodeAt(i);
  }
  contentReference.value.size = str.length;
  contentLoaded.value = true;
};

const downloadEmbededContent = () => {
  let link = document.createElement("a");
  let downloadFileName = filename.value;
  link.download = downloadFileName.toLowerCase();
  let blob = new Blob([contentReference.value.content], {
    type: contentReference.value.mediaType,
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};

onMounted(() => {
  loadContent();
});

watch(() => contentReference.value.uuid, () => {
  loadContent();
})
</script>

<style lang="scss">
@import "@/styles/components/content-viewer.scss";
</style>