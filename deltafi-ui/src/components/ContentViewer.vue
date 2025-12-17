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
<!-- ABOUTME: Component for viewing DeltaFile content with syntax highlighting. -->
<!-- ABOUTME: Accepts a ContentPointer and fetches/displays content from the API. -->

<template>
  <div class="content-viewer">
    <div class="content-viewer-container">
      <div class="content-viewer-section">
        <Toolbar>
          <template #start>
            <Dropdown v-model="selectedRenderFormat" :options="renderFormats" option-label="name" class="mr-3" style="min-width: 12rem" />
            <slot name="toolbar-start" />
          </template>
          <template #end>
            <span class="mr-3">
              <ContentTag v-for="tag in content.tags" :key="tag" :value="tag" class="ml-2" />
            </span>
            <Divider v-if="content.tags?.length > 0" layout="vertical" />
            <Button :label="content.mediaType" class="p-button-text p-button-secondary" disabled />
            <Divider layout="vertical" />
            <Button class="p-button-text p-button-secondary" disabled>
              <FormattedBytes :bytes="content.totalSize" />
            </Button>
            <Divider layout="vertical" />
            <ContentViewerMenu :model="items" />
          </template>
        </Toolbar>
        <Message v-for="error in errors" :key="error" severity="error" :closable="true" class="mb-0 mt-0">
          {{ error }}
        </Message>
        <Message v-for="warning in warnings" :key="warning" severity="warn" :closable="true" class="mb-0 mt-0">
          {{ warning }}
        </Message>
        <div class="scrollable-content content-viewer-content">
          <div class="content-wrapper">
            <HighlightedCode v-if="loadingContent" :highlight="false" code="Loading..." />
            <div v-else-if="contentLoaded">
              <HighlightedCode :highlight="highlightCode && selectedRenderFormat.highlight" :code="displayedContent" :language="language" />
            </div>
          </div>
          <ScrollTop target="parent" :threshold="100" icon="pi pi-arrow-up" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import ContentTag from "@/components/ContentTag.vue";
import ContentViewerMenu from "@/components/ContentViewerMenu.vue";
import FormattedBytes from "@/components/FormattedBytes.vue";
import HighlightedCode from "@/components/HighlightedCode.vue";
import useContent from "@/composables/useContent";
import useNotifications from "@/composables/useNotifications";
import useUiConfig from "@/composables/useUiConfig";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { prettyPrint } from "@/workers/prettyPrint.worker";
import { computed, onMounted, ref, toRefs, watch, reactive } from "vue";
import { useClipboard } from "@vueuse/core";

import hexy from "hexy";
import _ from "lodash";
import { Buffer } from "buffer";

import Button from "primevue/button";
import Divider from "primevue/divider";
import Dropdown from "primevue/dropdown";
import Message from "primevue/message";
import Toolbar from "primevue/toolbar";
import ScrollTop from "primevue/scrolltop";

const props = defineProps({
  content: {
    type: Object,
    required: true,
  },
});

const { content } = toRefs(props);
const { downloadURL, loading: loadingContent, fetch: fetchContent, errors, data } = useContent();
const { formattedBytes } = useUtilFunctions();
const { copy: copyToClipboard } = useClipboard();
const notify = useNotifications();
const { uiConfig } = useUiConfig();

const contentLoaded = ref(false);
const highlightCode = ref(true);
const contentBuffer = ref(new ArrayBuffer());
const contentAs = reactive({});
const decoder = new TextDecoder("utf-8");
const encoder = new TextEncoder("utf-8");
const prettyPrintFormats = ["json", "xml"];

watch(contentBuffer, () => processContent());

const processContent = async () => {
  // Hex
  const buffer = Buffer.from(contentBuffer.value);
  contentAs.hex = hexy.hexy(buffer, {
    format: "twos",
    width: 16,
  });

  // UTF-8
  contentAs.utf8 = decoder.decode(contentBuffer.value);

  // Formatted JSON/XML
  if (prettyPrintFormats.includes(language.value)) {
    contentAs[language.value] = "Loading...";
    contentAs[language.value] = await prettyPrint(contentAs.utf8, language.value);
  }

  if (renderFormats.value.find((f) => f.id == language.value)) {
    selectedRenderFormat.value = renderFormats.value.find((f) => f.id === language.value);
  } else {
    selectedRenderFormat.value = renderFormats.value.find((f) => f.id === "utf8");
  }
};

const displayedContent = computed(() => {
  return contentAs[selectedRenderFormat.value.id];
});

// Menu Buttons
const highlightBtnEnbl = computed(() => {
  return contentBuffer.value.byteLength > 0 && selectedRenderFormat.value.highlight;
});
const copyBtnEnbl = computed(() => contentBuffer.value.byteLength > 0);
const downloadBtnEnbl = computed(() => contentBuffer.value.byteLength > 0);
const items = ref([
  {
    label: "Enable Highlighting",
    icon: "fas fa-highlighter",
    alternateLabel: "Disable Highlighting",
    alternateIcon: "fas fa-ban",
    isEnabled: highlightBtnEnbl,
    disabledLabel: "Highlighting Disabled",
    toggled: false,
    command: () => {
      onToggleHighlightCodeClick();
    },
  },
  {
    label: "Copy to Clipboard",
    icon: "fas fa-copy",
    isEnabled: copyBtnEnbl,
    disabledLabel: "Nothing to Copy",
    command: () => {
      copyToClipboard(displayedContent.value);
      notify.info("Copied to clipboard", "Content copied to clipboard.", 3000);
    },
  },
  {
    label: "Download",
    icon: "fas fa-download",
    isEnabled: downloadBtnEnbl,
    disabledLabel: "Nothing to Download",
    command: () => {
      download();
    },
  },
]);

const language = computed(() => {
  let lang;
  try {
    lang = content.value.mediaType.split("/")[1];
  } catch {
    lang = null;
  }

  if (prettyPrintFormats.includes(lang)) return lang;

  if (contentAs.utf8 && contentAs.utf8.slice(0, 5) === "<?xml") return "xml";

  try {
    JSON.parse(contentAs.utf8);
    return "json";
  } catch {
    // Not JSON. Do nothing.
  }

  return "utf8";
});

const renderFormats = computed(() => {
  const formats = [
    { name: "Hexdump", id: "hex", highlight: false },
    { name: "UTF-8", id: "utf8", highlight: true },
  ];

  if (prettyPrintFormats.includes(language.value)) {
    formats.push({
      name: `Formatted ${language.value.toUpperCase()}`,
      id: language.value,
      highlight: true,
    });
  }

  return _.sortBy(formats, "name");
});

const selectedRenderFormat = ref(renderFormats.value.find((f) => f.id === language.value));

onMounted(() => {
  loadContent();
});

watch(
  () => content.value,
  () => loadContent()
);

const partialContent = computed(() => content.value.totalSize > uiConfig.contentPreviewSize);

const embeddedContent = computed(() => "content" in content.value);

const formattedMaxPreviewSize = computed(() => formattedBytes(uiConfig.contentPreviewSize));

const warnings = computed(() => {
  const warnings = [];
  if (contentLoaded.value && contentBuffer.value.byteLength == 0) warnings.push("No content to display.");
  if (partialContent.value) warnings.push(`Content size is over the preview limit. Only showing the first ${formattedMaxPreviewSize.value}.`);
  return warnings;
});

const onToggleHighlightCodeClick = () => {
  highlightCode.value = !highlightCode.value;
};

const download = () => {
  if (embeddedContent.value) {
    downloadEmbeddedContent();
  } else {
    const url = downloadURL(content.value);
    window.open(url);
  }
};

const loadContent = async () => {
  contentLoaded.value = false;
  if (embeddedContent.value) {
    loadEmbeddedContent();
    return;
  }
  const request = {
    ...content.value,
    size: partialContent.value ? uiConfig.contentPreviewSize : undefined,
  };
  try {
    await fetchContent(request);
  } catch {
    return;
  }
  contentBuffer.value = await data.value.arrayBuffer();
  contentLoaded.value = true;
};

const loadEmbeddedContent = () => {
  contentBuffer.value = encoder.encode(content.value.content);
  content.value.totalSize = content.value.content.length;
  contentLoaded.value = true;
};

const downloadEmbeddedContent = () => {
  const link = document.createElement("a");
  const downloadFileName = content.value.filename || content.value.name;
  link.download = downloadFileName.toLowerCase();
  const blob = new Blob([content.value.content], {
    type: content.value.mediaType,
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};
</script>

<style>
.content-viewer {
  height: 100%;

  .p-toolbar {
    border-radius: 4px 4px 0 0;
    padding: 0.5rem 0.75rem;
  }

  .p-message {
    border-radius: 0;

    .p-message-wrapper {
      padding: 0.5rem 0.75rem;
    }
  }

  .content-viewer-container {
    display: flex;
    flex-direction: column;
    height: 100%;

    .content-viewer-section {
      flex-grow: 1;
      display: flex;
      flex-direction: column;
      min-height: 0;
    }

    .scrollable-content {
      background-color: #303030;
      font-family: "Courier New", Courier, monospace;
      flex-grow: 1;
      overflow: auto;
      min-height: 0;
    }
  }
}
</style>
