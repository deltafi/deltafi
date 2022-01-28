<template>
  <div class="data-viewer">
    <span @click="showDialog()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" position="" :header="header()" :style="{width: '75vw'}" :maximizable="true" :modal="true" :dismissable-mask="true">
      <div class="content-viewer">
        <div>
          <TabView v-if="viewerDataLoaded">
            <TabPanel header="Preview">
              <HighlightedCode :highlight="highlightCode" :code="viewerDataAsString" />
            </TabPanel>
            <TabPanel header="Hexdump">
              <HighlightedCode :highlight="false" :code="viewerDataAsHexdump" />
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
            <Button label="Download" icon="fa fa-download" @click="download()" />
          </div>
        </div>
      </template>
    </Dialog>
  </div>
</template>

<script>
import HighlightedCode from "@/components/HighlightedCode.vue";

import Button from "primevue/button";
import Dialog from "primevue/dialog";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";
import ToggleButton from 'primevue/togglebutton';

import hexy from 'hexy';

export default {
  name: "DomainViewer",
  components: {
    Button,
    Dialog,
    HighlightedCode,
    TabPanel,
    TabView,
    ToggleButton
  },
  props: {
    viewerDataReference: {
      type: [Object, String, null],
      default: ""
    },
    dataViewerMetaData : {
      type: Object,
      required: true,
    }
  },
  data() {
    return {
      viewerDataLoaded: false,
      dialogVisible: false,
      highlightCode: false,
      viewerData: '',
    };
  },
  computed: {
    viewerDataAsString() {
      return this.viewerData.toString();
    },
    viewerDataAsHexdump() {
      let buffer = Buffer.from(this.viewerData);
      let hexdump = hexy.hexy(buffer, {
        format: "twos",
      });
      return hexdump;
    },
  },
  created() {
    this.highlightCode = true;
  },
  methods: {
    showDialog() {
      if (!this.viewerDataLoaded) this.loadViewerData();
      this.dialogVisible = true;
    },
    download() {
      let link = document.createElement('a');
      let downloadFileName = `${this.dataViewerMetaData.did}-${this.dataViewerMetaData.viewerType}-${this.dataViewerMetaData.name}`;
      link.download = downloadFileName.toLowerCase();
      let blob = new Blob([this.viewerData], {type: this.dataViewerMetaData.mediaType});
      link.href = URL.createObjectURL(blob);
      link.click();
      URL.revokeObjectURL(link.href);
    },
    loadViewerData() {
      this.viewerData = this.viewerDataReference == null ? "" : this.viewerDataReference;
      this.viewerDataLoaded = true;
    },
    header() {
      return this.dataViewerMetaData.viewerType + ": " + this.dataViewerMetaData.name + ' - ' + this.dataViewerMetaData.did;
    }
  }
}
</script>

<style lang="scss">
  @import "@/styles/components/content-viewer.scss";
</style>