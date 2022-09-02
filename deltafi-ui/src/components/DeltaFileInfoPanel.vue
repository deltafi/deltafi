<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
              <span v-else-if="['Original File Size', 'Total File Size'].includes(key)">
                <FormattedBytes :bytes="value" />
              </span>
              <span v-else>{{ value }}</span>
              <span v-if="key === 'Stage'">
                <ErrorAcknowledgedBadge v-if="deltaFile.errorAcknowledged" :reason="deltaFile.errorAcknowledgedReason" :timestamp="deltaFile.errorAcknowledged" class="ml-2" />
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
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import FormattedBytes from "@/components/FormattedBytes.vue";
import ErrorAcknowledgedBadge from "@/components/errors/AcknowledgedBadge.vue";
import Timestamp from "@/components/Timestamp.vue";

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const deltaFile = reactive(props.deltaFileData);

const originalFileSize = computed(() => {
  const ingressAction = deltaFile.protocolStack.find((p) => {
    return p.action === "IngressAction";
  });
  if (!ingressAction) return "N/A";
  return ingressAction.content[0].contentReference.size;
});

const infoFields = computed(() => {
  let fields = {};
  fields["DID"] = deltaFile.did;
  fields["Original Filename"] = deltaFile.sourceInfo.filename;
  fields["Original File Size"] = originalFileSize.value;
  fields["Total File Size"] = deltaFile.totalBytes;
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