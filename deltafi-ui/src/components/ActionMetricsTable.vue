<template>
  <div>
    <CollapsiblePanel header="Actions" class="metrics-panel table-panel">
      <DataTable v-model:filters="filters" :value="tableData.value" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" :loading="loading" sort-field="action_name" :sort-order="1" filter-display="row">
        <template #empty>No Action Metrics available.</template>
        <template #loading>Loading Action Metrics data. Please wait.</template>
        <Column header="Action Name" field="action_name" :sortable="true">
          <template #filter="{ filterModel, filterCallback }">
            <InputText v-model="filterModel.value" type="text" class="p-inputtext-sm p-column-filter" placeholder="Filter by Action Name" @focus="pauseTimer(true)" @blur="pauseTimer(false)" @input="filterCallback()" />
          </template>
        </Column>
        <Column header="Type" field="family_type" :sortable="true" class="type-column">
          <template #body="{ data }">{{ sentenceCaseString(data.family_type) }}</template>
          <template #filter="{ filterModel, filterCallback }">
            <MultiSelect v-model="filterModel.value" type="text" class="p-inputtext-sm deltafi-input-field p-column-filter" placeholder="Filter by Type" :options="familyTypes" @before-show="pauseTimer(true)" @before-hide="pauseTimer(false)" @change="filterCallback()" />
          </template>
        </Column>
        <Column v-for="col of columns" :key="col.field" :field="col.field" :header="col.header" :sortable="true" class="metric-column">
          <template #body="row">{{ formatMetricValue(row) }}</template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import MultiSelect from "primevue/multiselect";
import { FilterMatchMode } from "primevue/api";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, defineProps, ref, defineEmits } from "vue";

const emit = defineEmits(["pauseTimer"]);

const props = defineProps({
  actions: {
    type: Object,
    required: true,
  },
  loading: {
    type: Boolean,
    required: true,
  }
});

const { sentenceCaseString, formattedBytes } = useUtilFunctions();

const filters = ref({
  action_name: { value: null, matchMode: FilterMatchMode.CONTAINS },
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
}

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
        header: sentenceCaseString(metricName),
      };
    });
});
const tableData = computed(() => {
  return rows.value.length > 0 && columns.value.length > 0 ? rows : [];
});

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