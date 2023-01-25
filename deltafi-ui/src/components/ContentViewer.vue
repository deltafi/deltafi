<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div class="content-viewer">
    <div class="content-viewer-container" :style="`height: ${maxHeight}; max-height: ${maxHeight}`">
      <div class="content-viewer-section">
        <Toolbar>
          <template #start>
            <Dropdown v-model="selectedRenderFormat" :options="renderFormats" option-label="name" class="mr-3" style="min-width: 12rem" />
          </template>
          <template #end>
            <Button :label="contentReference.mediaType" class="p-button-text p-button-secondary" disabled />
            <Divider layout="vertical" />
            <Button class="p-button-text p-button-secondary" disabled>
              <FormattedBytes :bytes="contentReference.size" />
            </Button>
            <Divider layout="vertical" />
            <ContentViewerMenu :model="items" />
          </template>
        </Toolbar>
        <span v-if="!_.isEmpty(metadata) && _.isEqual(viewMetadata, true)">
          <DataTable responsive-layout="scroll" :value="metadata" striped-rows sort-field="key" :sort-order="1" class="p-datatable-sm p-datatable-gridlines">
            <Column field="key" header="Key" :style="{ width: '25%' }" :sortable="true" />
            <Column field="value" header="Value" :style="{ width: '75%' }" :sortable="true" />
          </DataTable>
        </span>
        <Message v-for="error in errors" :key="error" severity="error" :closable="true" class="mb-0 mt-0">{{ error }}</Message>
        <Message v-for="warning in warnings" :key="warning" severity="warn" :closable="true" class="mb-0 mt-0">{{ warning }}</Message>
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
import HighlightedCode from "@/components/HighlightedCode.vue";
import ContentViewerMenu from "@/components/ContentViewerMenu.vue";
import useContent from "@/composables/useContent";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, defineProps, onMounted, ref, toRefs, watch, reactive } from "vue";
import { useClipboard } from "@vueuse/core";
import useNotifications from "@/composables/useNotifications";
import FormattedBytes from "@/components/FormattedBytes.vue";
import { prettyPrint } from "@/workers/prettyPrint.worker";

import Button from "primevue/button";
import Divider from "primevue/divider";
import Dropdown from "primevue/dropdown";
import Message from "primevue/message";
import Toolbar from "primevue/toolbar";
import ScrollTop from "primevue/scrolltop";
import DataTable from "primevue/datatable";
import Column from "primevue/column";

import hexy from "hexy";
import _ from "lodash";

const props = defineProps({
  contentReference: {
    type: Object,
    required: true,
  },
  metadata: {
    type: Object,
    required: false,
    default: null,
  },
  filename: {
    type: String,
    required: false,
    default: null,
  },
  maxHeight: {
    type: String,
    required: false,
    default: "100%",
  },
});

const { contentReference, maxHeight, filename, metadata } = toRefs(props);
const { downloadURL, loading: loadingContent, fetch: fetchContent, errors, data } = useContent();
const { formattedBytes } = useUtilFunctions();
const { copy: copyToClipboard } = useClipboard();
const notify = useNotifications();

const maxPreviewSize = 100000; // 100kB
const contentLoaded = ref(false);
const highlightCode = ref(true);
const viewMetadata = ref(false);
const content = ref(new ArrayBuffer());
const contentAs = reactive({});
const decoder = new TextDecoder("utf-8");
const encoder = new TextEncoder("utf-8");
const prettyPrintFormats = ["json", "xml"];

watch(content, () => processContent());

const processContent = async () => {
  // Hex
  const buffer = Buffer.from(content.value);
  contentAs.hex = hexy.hexy(buffer, {
    format: "twos",
    width: 16,
  });

  // UTF-8
  contentAs.utf8 = decoder.decode(content.value);

  // Formatted JSON/XML
  if (prettyPrintFormats.includes(language.value)) {
    contentAs.formatted = "Loading...";
    contentAs.formatted = await prettyPrint(contentAs.utf8, language.value);
  }
};

const displayedContent = computed(() => {
  return contentAs[selectedRenderFormat.value.id];
});

// Menu Buttons
const highlightBtnEnbl = computed(() => {
  return content.value.byteLength > 0 && selectedRenderFormat.value.highlight;
});
const metadataBtnEnbl = computed(() => !_.isEmpty(metadata.value));
const copyBtnEnbl = computed(() => content.value.byteLength > 0);
const downloadBtnEnbl = computed(() => content.value.byteLength > 0);
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
      onToggleHiglightCodeClick();
    },
  },
  {
    label: "View Metadata",
    icon: "fas fa-table",
    alternateLabel: "Hide Metadata",
    alternateIcon: "transparent-icon",
    isEnabled: metadataBtnEnbl,
    toggled: true,
    disabledLabel: "No Metadata",
    command: () => {
      onToggleMetadataClick();
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
  try {
    return contentReference.value.mediaType.split("/")[1];
  } catch {
    return null;
  }
});

const renderFormats = computed(() => {
  const formats = [
    { name: "Hexdump", id: "hex", highlight: false },
    { name: "UTF-8", id: "utf8", highlight: true },
  ];

  if (prettyPrintFormats.includes(language.value)) {
    formats.push({
      name: `Formatted ${language.value.toUpperCase()}`,
      id: "formatted",
      highlight: true,
    });
  }

  return _.sortBy(formats, "name");
});

const selectedRenderFormat = ref(renderFormats.value.find((f) => f.id === "utf8"));

onMounted(() => {
  loadContent();
});

watch(
  () => contentReference.value,
  () => {
    loadContent();
    if (renderFormats.value.find((f) => f.id == selectedRenderFormat.value.id)) {
      selectedRenderFormat.value = renderFormats.value.find((f) => f.id === selectedRenderFormat.value.id);
    } else {
      selectedRenderFormat.value = renderFormats.value.find((f) => f.id === "utf8");
    }
  }
);

const partialContent = computed(() => contentReference.value.size > maxPreviewSize);

const embededContent = computed(() => "content" in contentReference.value);

const formattedMaxPreviewSize = computed(() => formattedBytes(maxPreviewSize));

const warnings = computed(() => {
  let warnings = [];
  if (contentLoaded.value && content.value.byteLength == 0) warnings.push("No content to display.");
  if (partialContent.value) warnings.push(`Content size is over the preview limit. Only showing the first ${formattedMaxPreviewSize.value}.`);
  return warnings;
});

const onToggleHiglightCodeClick = () => {
  highlightCode.value = !highlightCode.value;
};

const onToggleMetadataClick = () => {
  viewMetadata.value = !viewMetadata.value;
};

const download = () => {
  if (embededContent.value) {
    downloadEmbededContent();
  } else {
    let url = downloadURL({
      ...contentReference.value,
      filename: filename.value,
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
  content.value = encoder.encode(contentReference.value.content);
  contentReference.value.size = contentReference.value.content.length;
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
</script>

<style lang="scss">
@import "@/styles/components/content-viewer.scss";
</style>
