<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
              <span v-else-if="key.includes('Size')">
                <FormattedBytes :bytes="value" />
              </span>
              <span v-else>{{ value }}</span>
              <span v-if="key === 'Stage'">
                <ErrorAcknowledgedBadge v-if="deltaFile.errorAcknowledged" :reason="deltaFile.errorAcknowledgedReason" :timestamp="deltaFile.errorAcknowledged" class="ml-2" />
                <AutoResumeBadge v-if="deltaFile.stage === 'ERROR' && deltaFile.nextAutoResume !== null" :timestamp="deltaFile.nextAutoResume" :reason="deltaFile.nextAutoResumeReason" class="ml-2" />
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
import AutoResumeBadge from "./errors/AutoResumeBadge.vue";
import Timestamp from "@/components/Timestamp.vue";

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const deltaFile = reactive(props.deltaFileData);

const infoFields = computed(() => {
  let fields = {};
  fields["DID"] = deltaFile.did;
  fields["Filename"] = deltaFile.sourceInfo.filename;
  fields["Ingress Size"] = deltaFile.ingressBytes;
  fields["Total Size"] = deltaFile.totalBytes;
  fields["Ingress Flow"] = deltaFile.sourceInfo.flow;
  fields["Stage"] = deltaFile.stage;
  fields["Created"] = deltaFile.created;
  fields["Modified"] = deltaFile.modified;
  fields["Processing Type"] = deltaFile.sourceInfo.processingType;
  return fields;
});
</script>

<style lang="scss">
@import "@/styles/components/deltafile-info-panel.scss";
</style>
