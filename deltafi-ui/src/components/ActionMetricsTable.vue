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
    <DataTable v-model:filters="filters" :value="tableData.value" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines action-metrics-table" :loading="loading" sort-field="action_name" :sort-order="1" :filter-display="props.filterType">
      <template #empty>No Action Metrics available.</template>
      <template #loading>Loading Action Metrics data. Please wait.</template>
      <Column header="Action Name" field="action_name" :sortable="true" :hidden="props.hiddenColumn">
        <template #body="{ data }">
          {{ data.action_name }}
          <a v-tooltip.top="`View logs`" class="cursor-pointer" style="color: black" :href="actionLogLink(data.action_name)" target="_blank" rel="noopener noreferrer">
            <i class="ml-1 text-muted fa-regular fa-chart-bar" />
          </a>
        </template>
        <template #filter="{ filterModel, filterCallback }">
          <InputText v-model="filterModel.value" type="text" class="p-inputtext-sm p-column-filter" placeholder="Filter by Action Name" @focus="pauseTimer(true)" @blur="pauseTimer(false)" @input="filterCallback()" />
        </template>
      </Column>
      <Column header="Type" field="family_type" :sortable="true" class="type-column" :hidden="props.hiddenColumn">
        <template #body="{ data }">{{ _.startCase(data.family_type) }}</template>
        <template #filter="{ filterModel, filterCallback }">
          <MultiSelect v-model="filterModel.value" type="text" class="p-inputtext-sm deltafi-input-field p-column-filter" placeholder="Filter by Type" :options="familyTypes" @before-show="pauseTimer(true)" @before-hide="pauseTimer(false)" @change="filterCallback()" />
        </template>
      </Column>
      <Column v-for="col of columns" :key="col.field" :field="col.field" :header="col.header" :sortable="true" class="metric-column">
        <template #body="row">{{ formatMetricValue(row) }}</template>
      </Column>
    </DataTable>
  </div>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import MultiSelect from "primevue/multiselect";
import { FilterMatchMode } from "primevue/api";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, defineEmits, defineProps, inject, ref } from "vue";

import _ from "lodash";

const emit = defineEmits(["pauseTimer"]);
const uiConfig = inject("uiConfig");

const props = defineProps({
  actions: {
    type: Object,
    required: true,
  },
  loading: {
    type: Boolean,
    required: true,
  },
  filterType: {
    type: String || null,
    required: false,
    default: "row",
  },
  filterBy: {
    type: String || null,
    required: false,
    default: null,
  },
  hiddenColumn: {
    type: Boolean,
    required: false,
    default: false,
  },
});

const { formattedBytes } = useUtilFunctions();

const filters = ref({
  action_name: { value: props.filterBy, matchMode: FilterMatchMode.CONTAINS },
  family_type: { value: null, matchMode: FilterMatchMode.IN },
});

const familyTypes = computed(() => {
  if (props.actions.length === 0) return [];
  const familyTypesArray = new Set();

  for (const [actionsKey] of Object.entries(props.actions)) {
    for (const [actionKey, actionValue] of Object.entries(props.actions[actionsKey])) {
      if (actionKey == "family_type") {
        familyTypesArray.add(actionValue);
      }
    }
  }
  return Array.from(familyTypesArray).sort();
});

// We have to pause the polling timer due to with each time a polling occurs the filter looses focus and clears out if you havent submitted it yet.
const pauseTimer = (value) => {
  emit("pauseTimer", value);
};

const rows = computed(() => {
  if (props.actions.length === 0) return [];
  const actions = props.actions;
  return Object.keys(actions).map((action_name) => {
    let metrics = actions[action_name];
    metrics["action_name"] = action_name;
    return metrics;
  });
});
const columns = computed(() => {
  if (props.actions.length === 0) return [];
  let metricNames = new Set();
  rows.value.forEach((row) => {
    Object.keys(row).forEach((key) => {
      if (key !== "action_name" && key !== "family_type") metricNames.add(key);
    });
  });
  return Array.from(metricNames)
    .sort()
    .map((metricName) => {
      return {
        field: metricName,
        header: _.startCase(metricName),
      };
    });
});

const tableData = computed(() => {
  return rows.value.length > 0 && columns.value.length > 0 ? rows : [];
});

const actionLogLink = (actionNameForLink) => {
  return `https://metrics.${uiConfig.domain}/d/action-log-viewer/action-log-viewer?var-datasource=Loki&var-searchable_pattern=&var-action_name=${actionNameForLink}`;
};

const formatMetricValue = (row) => {
  const field = row.column.key;
  const value = row.data[field] || 0;
  if (value === undefined || value === null) {
    return null;
  } else if (field.includes("bytes")) {
    return formattedBytes(value);
  } else {
    return value;
  }
};
</script>

<style lang="scss">
@import "@/styles/components/action-metrics-table.scss";
</style>
