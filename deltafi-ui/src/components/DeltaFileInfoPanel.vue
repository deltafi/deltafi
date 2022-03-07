<template>
  <div>
    <CollapsiblePanel header="Information" class="information-panel">
      <div class="row">
        <div v-for="(value, key) in infoFields" :key="key" class="col-12 col-md-6 col-xl-3 pb-0">
          <dl>
            <dt>{{ key }}</dt>
            <dd :class="{ monospace: key === 'DID' }">
              <span v-if="['Modified', 'Created'].includes(key)">
                <Timestamp :timestamp="value" />
              </span>
              <span v-else>{{ value }}</span>
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

<script setup>
import { computed, reactive, defineProps } from "vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import ErrorAcknowledgedBadge from "@/components/ErrorAcknowledgedBadge.vue";
import Timestamp from "@/components/Timestamp.vue";

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

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
</script>

<style lang="scss">
@import "@/styles/components/deltafile-info-panel.scss";
</style>