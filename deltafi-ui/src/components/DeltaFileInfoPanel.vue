<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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
              <span v-else-if="key.includes('Size') || key.includes('Bytes')">
                <FormattedBytes :bytes="value" />
              </span>
              <span v-else>{{ value }}</span>
              <span v-if="key === 'Stage'">
                <ErrorAcknowledgedBadge v-if="latestErrorFlow && latestErrorFlow.errorAcknowledged" :reason="latestErrorFlow.errorAcknowledgedReason" :timestamp="latestErrorFlow.errorAcknowledged" class="ml-2" />
                <AutoResumeBadge v-if="deltaFile.stage === 'ERROR' && nextActionWithAutoResume" :timestamp="nextActionWithAutoResume.nextAutoResume" :reason="nextActionWithAutoResume.nextAutoResumeReason" class="ml-2" />
                <PendingAnnotationsBadge v-if="!_.isEmpty(deltaFile.pendingAnnotationsForFlows)" :pending-annotations="deltaFile.pendingAnnotations" class="ml-2" />
              </span>
            </dd>
          </dl>
        </div>
      </div>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import { computed, reactive } from "vue";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import FormattedBytes from "@/components/FormattedBytes.vue";
import ErrorAcknowledgedBadge from "@/components/errors/AcknowledgedBadge.vue";
import AutoResumeBadge from "./errors/AutoResumeBadge.vue";
import PendingAnnotationsBadge from "@/components/PendingAnnotationsBadge.vue";
import Timestamp from "@/components/Timestamp.vue";
import _ from "lodash";

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
});

const deltaFile = reactive(props.deltaFileData);

const infoFields = computed(() => {
  const fields = {};
  fields["DID"] = deltaFile.did;
  fields["Data Source"] = deltaFile.dataSource;
  fields["Name"] = deltaFile.name;
  fields["Ingress Size"] = deltaFile.ingressBytes;
  fields["Reference Bytes"] = deltaFile.referencedBytes;
  fields["Total Size"] = deltaFile.totalBytes;
  fields["Stage"] = deltaFile.stage;
  fields["Created"] = deltaFile.created;
  fields["Modified"] = deltaFile.modified;
  return fields;
});

const latestErrorFlow = computed(() => {
  return _.chain(deltaFile.flows)
    .filter((flow) => flow.state === "ERROR")
    .sortBy(["modified"])
    .last()
    .value();
});

const nextActionWithAutoResume = computed(() => {
  return _.chain(deltaFile.flows)
    .map((flow) => flow.actions)
    .flatten()
    .filter((action) => action.state === "ERROR" && action.nextAutoResume !== null)
    .sortBy(["nextAutoResume"])
    .value()[0];
});
</script>

<style>
.information-panel {
  dd {
    margin-bottom: 0;
    overflow-wrap: anywhere;
  }

  dl {
    margin-bottom: 1rem;
  }

  .p-panel-content {
    padding-bottom: 0.25rem !important;
  }
}
</style>
