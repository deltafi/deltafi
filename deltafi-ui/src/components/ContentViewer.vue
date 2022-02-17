<template>
  <div class="content-viewer">
    <span @click="showDialog()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" position="top" :header="dialogHeader" :style="{ width: '75vw' }" :maximizable="true" :modal="true" :dismissable-mask="true">
      <div class="content-viewer">
        <Message v-if="partialContent" severity="warn">Content size is over the preview limit. Only showing the first {{ formattedMaxPreviewSize }}.</Message>
        <div v-if="errors.length > 0">
          <Message v-for="error in errors" :key="error" severity="error" :closable="false">{{ error }}</Message>
        </div>
        <div v-else>
          <ProgressBar v-if="loadingContent" mode="indeterminate" style="height: 0.5em" />
          <TabView v-else-if="contentLoaded">
            <TabPanel header="Preview">
              <HighlightedCode :highlight="highlightCode" :code="contentAsString" />
            </TabPanel>
            <TabPanel header="Hexdump">
              <HighlightedCode :highlight="false" :code="contentAsHexdump" />
            </TabPanel>
          </TabView>
        </div>
      </div>
      <template #footer>
        <div class="row">
          <div class="col-6 text-left">
            <ToggleButton v-if="contentLoaded" v-model="highlightCode" on-icon="fas fa-file" on-label="Disable Highlighting" off-icon="fas fa-file-code" off-label="Enable Highlighting" style="width: 12.5rem" />
          </div>
          <div class="col-6 text-right">
            <Button :label="contentSize" class="p-button-text p-button-secondary" disabled @click="download()" />
            <Button label="Download" icon="fa fa-download" @click="download()" />
          </div>
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import useUtilFunctions from "@/composables/useUtilFunctions";
import useContent from "@/composables/useContent";
import HighlightedCode from "@/components/HighlightedCode.vue";
import { computed, ref, toRefs, defineProps } from "vue";

import Button from "primevue/button";
import Dialog from "primevue/dialog";
import Message from "primevue/message";
import ProgressBar from "primevue/progressbar";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";
import ToggleButton from "primevue/togglebutton";

import hexy from "hexy";

const props = defineProps({
  contentReference: {
    type: Object,
    required: true,
  },
  header: {
    type: String,
    required: false,
    default: null,
  },
});

const { formattedBytes } = useUtilFunctions();
const { contentReference, header } = toRefs(props);
const maxPreviewSize = 100000; // 100kB
const contentLoaded = ref(false);
const dialogVisible = ref(false);
const highlightCode = ref(true);
const content = ref(new ArrayBuffer());
const decoder = new TextDecoder("utf-8");
const { downloadURL, loading: loadingContent, fetch: fetchContent, errors, data } = useContent();

const dialogHeader = computed(() => {
  return header.value || contentReference.value.filename;
});

const partialContent = computed(() => {
  return contentReference.value.size > maxPreviewSize;
});

const contentAsString = computed(() => {
  return decoder.decode(new Uint8Array(content.value));
});

const contentAsHexdump = computed(() => {
  let buffer = Buffer.from(content.value);
  return hexy.hexy(buffer, {
    format: "twos",
  });
});

const embededContent = computed(() => {
  return "content" in contentReference.value;
});

const contentSize = computed(() => {
  return formattedBytes(contentReference.value.size);
});

const formattedMaxPreviewSize = computed(() => {
  return formattedBytes(maxPreviewSize);
});

const showDialog = () => {
  if (!contentLoaded.value) loadContent();
  dialogVisible.value = true;
};

const download = () => {
  if (embededContent.value) {
    downloadEmbededContent();
  } else {
    let url = downloadURL(contentReference.value);
    window.open(url);
  }
};

const loadContent = async () => {
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
  let downloadFileName = contentReference.value.filename;
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