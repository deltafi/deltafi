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
  <span class="cursor-pointer">
    <span v-if="!_.isEmpty(subscribersByState)" @click="showOverlayPanel($event)">
      <span class="text-muted">→ </span>
      <span v-for="(icon, state) in stateIcons" :key="state">
        <span v-if="subscribersByState[state]?.length">
          <span :class="stateBadgeClass[state]">{{ subscribersByState[state].length }}</span>
        </span>
      </span>
      <OverlayPanel ref="subscribesOverlayPanel" class="subscribersOverlayPanel">
        <DataTable removableSort :value="topic.subscribers" size="small">
          <ColumnGroup type="header">
            <Row>
              <Column header="Downstream Subscribers" field="name" />
              <Column field="state" sortable />
            </Row>
          </ColumnGroup>
          <Column field="name">
            <template #body="{ data }">
              <i :class="typeIcons[data.type]"></i>
              {{ data.name }}
            </template>
          </Column>
          <Column>
            <template #body="{ data }">
              <span class="fa-stack fa-fw">
                <i v-if="data.state === 'PAUSED'" class="fa-solid fa-circle fa-stack-1x"></i>
                <i :class="stateIcons[data.state]"></i>
              </span>
            </template>
          </Column>
        </DataTable>
      </OverlayPanel>
    </span>
  </span>
</template>

<script setup>
import OverlayPanel from "primevue/overlaypanel";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import ColumnGroup from "primevue/columngroup";
import Row from "primevue/row";
import useTopics from "@/composables/useTopics";
import { computed } from "vue";
import { onMounted, ref } from "vue";
import _ from "lodash";

const props = defineProps({
  topicName: {
    type: String,
    required: true,
  },
});

const { getTopic } = useTopics();
const topic = ref(null);

const stateIcons = {
  RUNNING: "fas fa-play-circle text-success mr-1 fa-stack-1x",
  PAUSED: "fas fa-pause-circle text-warning mr-1 fa-stack-1x",
  STOPPED: "fas fa-stop-circle text-secondary mr-1 fa-stack-1x",
  ERROR: "fas fa-exclamation-circle text-warning mr-1 fa-stack-1x",
};

const stateBadgeClass = {
  RUNNING: "badge badge-success badge-pill mr-1",
  PAUSED: "badge badge-warning badge-pill mr-1",
  STOPPED: "badge badge-secondary badge-pill mr-1",
  ERROR: "badge badge-danger badge-pill mr-1",
};

const typeIcons = {
  REST_DATA_SOURCE: "fas fa-file-import fa-fw text-muted mr-1",
  TIMED_DATA_SOURCE: "fas fa-file-import fa-fw text-muted mr-1",
  TRANSFORM: "fas fa-project-diagram fa-fw text-muted mr-1",
  DATA_SINK: "fas fa-file-export fa-fw text-muted mr-1",
};

onMounted(async () => {
  topic.value = await getTopic(props.topicName);
});

const subscribersByState = computed(() => _.groupBy(topic.value?.subscribers || [], "state"));

const subscribesOverlayPanel = ref();
const showOverlayPanel = (event) => {
  subscribesOverlayPanel.value.show(event);
};
</script>

<style>
.subscribersOverlayPanel {
  .p-overlaypanel-content {
    padding: 0.75rem;
  }
  .p-datatable-sm td {
    padding: 0.1rem;
  }
  .p-datatable-sm th {
    padding: 0.4rem;
    color: var(--text-color-secondary);
  }
}
</style>
