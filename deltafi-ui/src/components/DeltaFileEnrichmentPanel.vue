<template>
  <div class="enrichment-panel">
    <CollapsiblePanel header="Enrichment" class="links-panel pl-0">
      <div v-if="!enrichmentContentReferences.length" class="d-flex w-100 justify-content-between no-data-panel-content">
        <span class="p-2">No Enrichment Data Available</span>
      </div>
      <div v-else class="list-group list-group-flush">
        <div v-for="item in enrichmentContentReferences" :key="item" class="list-group-item list-group-item-action">
          <ContentViewer :content-reference="item" :header="`Enrichment: ${item.name}`">
            <div class="content-viewer-button">
              <div class="d-flex w-100 justify-content-between">
                <strong class="mb-0">{{ item.name }}</strong>
                <i class="far fa-window-maximize" />
              </div>
              <small class="mb-1 text-muted d-flex w-100 justify-content-between">
                <span>{{ item.mediaType }}</span>
                <span>{{ formattedBytes(item.size) }}</span>
              </small>
            </div>
          </ContentViewer>
        </div>
      </div>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import { computed, reactive, defineProps } from "vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ContentViewer from "@/components/ContentViewer.vue";

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const { formattedBytes } = useUtilFunctions();
const deltaFile = reactive(props.deltaFileData);

const enrichmentContentReferences = computed(() => {
  return deltaFile.enrichment.map((enrichment) => {
    const content = enrichment.value || "";
    return {
      ...enrichment,
      did: deltaFile.did,
      content: content,
      filename: `${deltaFile.did}-enrichment-${enrichment.name}`,
      size: content.length,
    };
  });
});
</script>

<style lang="scss">
@import "@/styles/components/deltafile-enrichment-panel.scss";
</style>