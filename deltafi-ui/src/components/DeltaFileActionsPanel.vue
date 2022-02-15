<template>
  <div>
    <CollapsiblePanel header="Actions" class="actions-panel table-panel">
      <DataTable responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines" striped-rows :value="actions" :row-class="rowClass" @row-click="rowClick">
        <Column field="name" header="Action" :sortable="true" />
        <Column field="state" header="State" class="state-column" :sortable="true" />
        <Column field="created" header="Created" class="timestamp-column" :sortable="true" />
        <Column field="modified" header="Modified" class="timestamp-column" :sortable="true" />
        <Column field="elapsed" header="Elapsed" class="elapsed-column" :sortable="true">
          <template #body="action">
            {{ action.data.elapsed }}
          </template>
        </Column>
        <Column header="Content" class="content-column">
          <template #body="action">
            <span v-if="contentReferences.hasOwnProperty(action.data.name)">
              <ContentViewer :content-reference="contentReferences[action.data.name]">
                <Button icon="far fa-window-maximize" label="View" class="content-button p-button-link" />
              </ContentViewer>
            </span>
          </template>
        </Column>
        <Column header="Metadata" class="metadata-column">
          <template #body="action">
            <span v-if="metadataReferences.hasOwnProperty(action.data.name)">
              <MetadataViewer :metadata-references="actionMetadata(action.data.name)">
                <Button icon="fas fa-table" label="View" class="content-button p-button-link" />
              </MetadataViewer>
            </span>
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
    <ErrorViewer v-model:visible="errorViewer.visible" :action="errorViewer.action" />
  </div>
</template>

<script>
import { computed, reactive } from "vue";

import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Button from "primevue/button";

import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ContentViewer from "@/components/ContentViewer.vue";
import MetadataViewer from "@/components/MetadataViewer.vue";
import ErrorViewer from "@/components/ErrorViewer.vue";

import useUtilFunctions from "@/composables/useUtilFunctions";

export default {
  name: "DeltaFileActionsPanel",
  components: {
    CollapsiblePanel,
    Column,
    DataTable,
    Button,
    ContentViewer,
    ErrorViewer,
    MetadataViewer,
  },
  props: {
    deltaFileData: {
      type: Object,
      required: true,
    },
  },
  setup(props) {
    const { duration } = useUtilFunctions();
    const deltaFile = reactive(props.deltaFileData);
    const errorViewer = reactive({
      visible: false,
      action: {},
    });

    const actions = computed(() => {
      return deltaFile.actions.map((action) => {
        const timeElapsed = new Date(action.modified) - new Date(action.created);
        action.created = new Date(action.created).toISOString();
        action.modified = new Date(action.modified).toISOString();
        return {
          ...action,
          elapsed: duration(timeElapsed),
        };
      });
    });

    const contentReferences = computed(() => {
      if (Object.keys(deltaFile).length === 0) return {};
      let layers = deltaFile.protocolStack.concat(deltaFile.formattedData);
      return layers.reduce((content, layer) => {
        let actions = [layer.action, layer.formatAction, layer.egressActions].flat().filter((n) => n);
        for (const action of actions) {
          let filename = action === "IngressAction" ? deltaFile.sourceInfo.filename : layer.filename || `${deltaFile.did}-${layer.action}`;
          content[action] = {
            ...layer.contentReference,
            filename: filename,
          };
        }
        return content;
      }, {});
    });

    const metadataReferences = computed(() => {
      if (Object.keys(deltaFile).length === 0) return {};
      let layers = deltaFile.protocolStack.concat(deltaFile.formattedData);
      return layers.reduce((content, layer) => {
        let actions = [layer.action, layer.formatAction].flat().filter((n) => n);
        for (const action of actions) {
          let metadata = action === "IngressAction" ? deltaFile.sourceInfo.metadata : layer.metadata || `${deltaFile.did}-${layer.action}`;
          if (metadata.length > 0) {
            content[action] = metadata;
          }
        }
        return content;
      }, {});
    });

    const rowClass = (action) => {
      if (action.state === "ERROR") return "table-danger action-error";
      if (action.state === "RETRIED") return "table-warning action-error";
    };

    const rowClick = (event) => {
      let action = event.data;
      if (!["ERROR", "RETRIED"].includes(action.state)) return;

      errorViewer.visible = true;
      errorViewer.action = action;
    };

    const actionMetadata = (actionName) => {
      return Object.fromEntries(Object.entries(metadataReferences.value).filter(([key]) => key.includes(actionName)));
    };

    return {
      deltaFile,
      actions,
      contentReferences,
      metadataReferences,
      actionMetadata,
      rowClass,
      rowClick,
      errorViewer,
    };
  },
};
</script>

<style lang="scss">
@import "@/styles/components/deltafile-actions-panel.scss";
</style>