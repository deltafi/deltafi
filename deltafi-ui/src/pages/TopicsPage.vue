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
    <PageHeader heading="Topics">
      <div class="d-flex mb-2 btn-toolbar">
        <IconField iconPosition="left">
          <InputIcon class="pi pi-search"> </InputIcon>
          <InputText v-model="filters['global'].value" class="deltafi-input-field mx-1" placeholder="Search" />
        </IconField>
      </div>
    </PageHeader>
    <DataTable v-model:filters="filters" responsive-layout="scroll" :value="searchableTopics" striped-rows class="p-datatable-sm topics-table" :loading="showLoading" data-key="name" :global-filter-fields="['name', 'subscriberNames', 'publisherNames']">
      <template #empty> No topic information available. </template>
      <template #loading> Loading topics. Please wait. </template>
      <Column field="publishers" header="Publishers" :sortable="true">
        <template #body="{ data }">
          <div v-for="publisher in data.publishers" :key="publisher.name" class="my-1">
            <Tag v-tooltip.right="publisher.type" :icon="pubsubIcon(publisher.type)" :value="publisher.name" :class="publisher.state.toLowerCase()" rounded />
          </div>
          <div v-if="data.publishers.length === 0" class="my-1">
            <Tag icon="pi pi-exclamation-triangle" severity="warning" value="No Publishers" rounded />
          </div>
        </template>
      </Column>
      <Column class="arrow">
        <template #body>
          <i class="fa-solid fa-arrow-right" />
        </template>
      </Column>
      <Column field="name" header="Topic" :sortable="true">
        <template #body="{ data }">
          <Tag :value="data.name" class="topic" />
        </template>
      </Column>
      <Column class="arrow">
        <template #body>
          <i class="fa-solid fa-arrow-right" />
        </template>
      </Column>
      <Column field="subscribers" header="Subscribers" :sortable="true">
        <template #body="{ data }">
          <div v-for="subscriber in data.subscribers" :key="subscriber.name" class="my-1">
            <Tag v-tooltip.left="subscriber.type" :icon="pubsubIcon(subscriber.type)" :value="subscriber.name" :class="subscriber.state.toLowerCase()" rounded />
          </div>
          <div v-if="data.subscribers.length === 0" class="my-1">
            <Tag icon="pi pi-exclamation-triangle" severity="warning" value="No Subscribers" rounded />
          </div>
        </template>
      </Column>
    </DataTable>
  </div>
</template>

<script setup>
import PageHeader from "@/components/PageHeader.vue";
import useTopics from "@/composables/useTopics";
import { computed, inject, onMounted, onUnmounted, ref } from "vue";

import _ from "lodash";

import Column from "primevue/column";
import DataTable from "primevue/datatable";
import { FilterMatchMode } from "primevue/api";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";
import Tag from "primevue/tag";

const { topics, getAllTopics, loading, loaded } = useTopics();

let autoRefresh;
const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const showLoading = computed(() => !loaded.value && loading.value);

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

const searchableTopics = computed(() => {
  return _.map(topics.value, (topic) => {
    return {
      subscriberNames: _.map(topic.subscribers, "name"),
      publisherNames: _.map(topic.publishers, "name"),
      ...topic,
    };
  });
});

const pubsubIcon = (type) => {
  switch (type) {
    case "TIMED_DATA_SOURCE":
      return "fas fas fa-file-import fa-fw";
    case "REST_DATA_SOURCE":
      return "fas fas fa-file-import fa-fw";
    case "TRANSFORM":
      return "fas fa-project-diagram fa-fw";
    case "DATA_SINK":
      return "fas fas fa-file-export fa-fw";
  }
};

onMounted(async () => {
  await getAllTopics();
  autoRefresh = setInterval(() => {
    if (!isIdle.value && !loading.value) {
      getAllTopics();
    }
  }, refreshInterval);
});

onUnmounted(() => {
  clearInterval(autoRefresh);
});
</script>

<style>
.topics-table {
  th {
    width: 24%;
  }

  th.arrow {
    width: 14%;
  }

  td {
    padding-top: 0.25rem !important;
    padding-bottom: 0.25rem !important;
  }

  .p-tag {
    font-size: 12px;
    color: #333333;
  }

  .p-tag.running {
    background-color: #c7def0;
  }

  .p-tag.stopped {
    background-color: #d4d4d4;
  }

  .p-tag.invalid {
    background-color: #f5c5c6;
  }

  .p-tag.topic {
    background-color: #d4e5ce;
  }

  .p-tag.p-tag-warning {
    background-color: #ffeec7;
  }
}
</style>
