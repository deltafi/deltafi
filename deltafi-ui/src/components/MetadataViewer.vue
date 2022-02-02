<template>
  <div class="metadata-viewer">
    <span @click="showDialog()">
      <slot />
    </span>
    <Dialog v-model:visible="dialogVisible" position="top" header="Metadata" :style="{width: '75vw'}" :maximizable="true" :modal="true" :dismissable-mask="true">
      <div class="metadata-viewer">
        <div v-for="(metadataArray, actionName) in metadataReferences" :key="actionName">
          <CollapsiblePanel :header="actionName" class="table-panel mb-3">
            <DataTable responsive-layout="scroll" :value="metadataArray" striped-rows class="p-datatable-sm">
              <Column field="key" header="Key" :style="{width:'25%'}" />
              <Column field="value" header="Value" :style="{width:'75%'}" />
            </DataTable>
          </CollapsiblePanel>
        </div>
      </div>
    </Dialog>
  </div>
</template>

<script>
import Dialog from "primevue/dialog";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";

export default {
  name: "MetadataViewer",
  components: {
    Dialog,
    DataTable,
    Column,
    CollapsiblePanel,
  },
  props: {
    metadataReferences: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      dialogVisible: false,
    };
  },
  methods: {
    showDialog() {
      this.dialogVisible = true;
    },
  }
}
</script>
