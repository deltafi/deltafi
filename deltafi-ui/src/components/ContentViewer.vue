<template>
  <div class="content-viewer">
    <span @click="showDialog()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" position="" :header="contentReference.filename" :style="{width: '75vw'}" :maximizable="true" :modal="true" :dismissable-mask="true">
      <div class="content-viewer">
        <Message v-if="partialContent" severity="warn">
          Content size is over the preview limit. Only showing the first {{ formattedBytes(maxPreviewSize) }}.
        </Message>
        <Message v-if="error" severity="error" :closable="false">
          {{ error }}
        </Message>
        <div v-else>
          <ProgressBar v-if="loadingContent" mode="indeterminate" style="height: .5em" />
          <TabView v-if="contentLoaded">
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
            <ToggleButton v-model="highlightCode" on-icon="fas fa-file" on-label="Disable Highlighting" off-icon="fas fa-file-code" off-label="Enable Highlighting" style="width: 12.5rem" />
          </div>
          <div class="col-6 text-right">
            <Button :label="filesize" class="p-button-text p-button-secondary" disabled @click="download()" />
            <Button label="Download" icon="fa fa-download" @click="download()" />
          </div>
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script>
import ApiService from "@/service/ApiService";
import HighlightedCode from "@/components/HighlightedCode.vue";

import Button from "primevue/button";
import Dialog from "primevue/dialog";
import Message from "primevue/message"
import ProgressBar from 'primevue/progressbar';
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";
import ToggleButton from 'primevue/togglebutton';

import * as filesize from "filesize";
import hexy from 'hexy';

export default {
  name: "ContentViewer",
  components: {
    Button,
    Dialog,
    HighlightedCode,
    Message,
    TabPanel,
    TabView,
    ProgressBar,
    ToggleButton
  },
  props: {
    contentReference: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      maxPreviewSize: 100000, // 100kB
      loadingContent: false,
      contentLoaded: false,
      dialogVisible: false,
      highlightCode: false,
      content: new ArrayBuffer(),
      error: null
    };
  },
  computed: {
    partialContent() {
      return this.contentReference.size > this.maxPreviewSize;
    },
    contentAsString() {
      let enc = new TextDecoder("utf-8");
      let arr = new Uint8Array(this.content);
      return enc.decode(arr);
    },
    contentAsHexdump() {
      let buffer = Buffer.from(this.content);
      let hexdump = hexy.hexy(buffer, {
        format: "twos",
      });
      return hexdump;
    },
    filesize() {
      return this.formattedBytes(this.contentReference.size);
    }
  },
  created() {
    this.apiService = new ApiService();
    this.highlightCode = true;
  },
  methods: {
    showDialog() {
      if (!this.contentLoaded) this.loadContent();
      this.dialogVisible = true;
    },
    download() {
      let url = this.apiService.contentUrl(this.contentReference);
      console.debug(url)
      window.open(url);
    },
    formattedBytes(bytes) {
      return filesize(bytes, {base:10})
    },
    async loadContent() {
      this.loadingContent = true;
      try {
        let request = {
          ...this.contentReference,
          size: this.partialContent ? this.maxPreviewSize : this.contentReference.size
        }
        let response = await this.apiService.getContent(request);
        if (response.status == 200) {
          let blob = await response.blob();
          this.content = await blob.arrayBuffer();
          this.contentLoaded = true;
        } else {
          let body = await response.json()
          throw new Error(body.error);
        }
      } catch(error) {
        this.error = error.message;
      }
      this.loadingContent = false;
    },
  },
  apiService: null
};
</script>

<style lang="scss">
  @import "@/styles/components/content-viewer.scss";
</style>