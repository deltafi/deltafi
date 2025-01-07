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
  <div class="annotations-viewer">
    <div v-if="!_.isEmpty(pendingAnnotations)">
      <Message severity="warn" :closable="false" class="mb-2 mt-0"> Expected Read Receipts: {{ pendingAnnotations }}</Message>
    </div>
    <div v-for="(annotationsArray, actionName) in props.annotations" :key="actionName">
      <DataTable responsive-layout="scroll" :value="annotationsArray" striped-rows sort-field="key" :sort-order="1" class="p-datatable-sm" scroll-height="500px" data-key="key">
        <Column header="Key" field="key" :style="{ width: '25%' }" :sortable="true" />
        <Column header="Value" field="value" :style="{ width: '65%' }" :sortable="true">
          <template #body="{ data }">
            {{ data.value }}
            <router-link :to="{ path: path }" class="pl-2" @click="setSearchableAnnotations(data)">
              <i class="pi pi-search text-muted" style="font-size: 1rem" />
            </router-link>
          </template>
        </Column>
      </DataTable>
    </div>
  </div>
</template>

<script setup>
import { computed, defineProps } from "vue";
import { useStorage, StorageSerializers } from "@vueuse/core";

import DataTable from "primevue/datatable";
import Column from "primevue/column";
import Message from "primevue/message";

import _ from "lodash";

const props = defineProps({
  annotations: {
    type: Object,
    required: true,
  },
  pendingAnnotations: {
    type: String,
    required: false,
    default: null,
  },
});

const path = computed(() => {
  return "/deltafile/search";
});

const panelState = useStorage("search-page-persisted-params", {}, sessionStorage, { serializer: StorageSerializers.object });

const setSearchableAnnotations = (rowData) => {
  let searchableAnnotationsArray = [];
  let searchableAnnotationsObject = {};
  searchableAnnotationsObject["key"] = rowData.key;
  searchableAnnotationsObject["value"] = rowData.value;
  searchableAnnotationsObject["valid"] = true;
  if (!_.isEmpty(_.get(panelState.value, "validatedAnnotations", null))) {
    panelState.value["validatedAnnotations"].push(searchableAnnotationsObject);
  } else {
    searchableAnnotationsArray.push(searchableAnnotationsObject);
    panelState.value["validatedAnnotations"] = searchableAnnotationsArray;
  }
};

const pendingAnnotations = computed(() => {
  return props.pendingAnnotations ? props.pendingAnnotations : null;
});
</script>
