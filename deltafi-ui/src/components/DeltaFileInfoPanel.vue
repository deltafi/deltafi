<template>
  <div>
    <CollapsiblePanel header="Information" class="information-panel">
      <div class="row">
        <div v-for="(value, key) in infoFields" :key="key" class="col-12 col-md-6 col-xl-3 pb-0">
          <dl>
            <dt>{{ key }}</dt>
            <dd :class="{ monospace: key === 'DID' }">
              {{ value }}
              <span v-if="key === 'Stage'">
                <ErrorAcknowledgedBadge v-if="deltaFile.errorAcknowledged" :reason="deltaFile.errorAcknowledgedReason" :timestamp="deltaFile.errorAcknowledged" />
              </span>
            </dd>
          </dl>
        </div>
      </div>
    </CollapsiblePanel>
  </div>
</template>

<script>
import { computed, reactive } from "vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ErrorAcknowledgedBadge from "@/components/ErrorAcknowledgedBadge.vue";

export default {
  name: "DeltaFileInfoPanel",
  components: {
    CollapsiblePanel,
    ErrorAcknowledgedBadge,
  },
  props: {
    deltaFileData: {
      type: Object,
      required: true,
    },
  },
  setup(props) {
    const deltaFile = reactive(props.deltaFileData);
    const { formattedBytes } = useUtilFunctions();

    const originalFileSize = computed(() => {
      const ingressAction = deltaFile.protocolStack.find((p) => {
        return p.action === "IngressAction";
      });
      if (!ingressAction) return "N/A";
      return formattedBytes(ingressAction.contentReference.size);
    });

    const infoFields = computed(() => {
      let fields = {};
      fields["DID"] = deltaFile.did;
      fields["Original Filename"] = deltaFile.sourceInfo.filename;
      fields["Original File Size"] = originalFileSize.value;
      fields["Ingress Flow"] = deltaFile.sourceInfo.flow;
      fields["Stage"] = deltaFile.stage;
      fields["Created"] = deltaFile.created;
      fields["Modified"] = deltaFile.modified;
      return fields;
    });

    return { deltaFile, infoFields };
  },
};
</script>

<style lang="scss">
@import "@/styles/components/deltafile-info-panel.scss";
</style>