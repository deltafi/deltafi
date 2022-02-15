<template>
  <div class="domains-panel">
    <CollapsiblePanel header="Domains" class="links-panel pl-0">
      <div v-if="domainContentReferences.length == 0" class="d-flex w-100 justify-content-between no-data-panel-content">
        <span class="p-2">No Domain Data Available</span>
      </div>
      <div v-else class="list-group list-group-flush">
        <div v-for="item in domainContentReferences" :key="item" class="list-group-item list-group-item-action">
          <ContentViewer :content-reference="item" :header="`Domain: ${item.name}`">
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

<script>
import { computed, reactive } from "vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ContentViewer from "@/components/ContentViewer.vue";

export default {
  name: "DeltaFileDomainsPanel",
  components: {
    CollapsiblePanel,
    ContentViewer,
  },
  props: {
    deltaFileData: {
      type: Object,
      required: true,
    },
  },
  setup(props) {
    const { formattedBytes } = useUtilFunctions();
    const deltaFile = reactive(props.deltaFileData);

    const domainContentReferences = computed(() => {
      return deltaFile.domains.map((domain) => {
        const content = domain.value || "";
        return {
          ...domain,
          did: deltaFile.did,
          content: content,
          filename: `${deltaFile.did}-domain-${domain.name}`,
          size: content.length,
        };
      });
    });

    return { deltaFile, domainContentReferences, formattedBytes };
  },
};
</script>

<style lang="scss">
@import "@/styles/components/deltafile-domains-panel.scss";
</style>