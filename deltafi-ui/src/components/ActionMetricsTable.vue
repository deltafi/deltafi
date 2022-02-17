<template>
  <div>
    <CollapsiblePanel :header="title" class="metrics-panel table-panel">
      <DataTable :value="tableData.value" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" :loading="loading" sort-field="action_name" :sort-order="1">
        <template #empty>No {{ title }} available.</template>
        <template #loading>Loading {{ title }} data. Please wait.</template>
        <Column header="Action Name" field="action_name" :sortable="true" />
        <Column v-for="col of columns" :key="col.field" :field="col.field" :header="col.header" :sortable="true" class="metric-column">
          <template #body="row">{{ formatMatricValue(row) }}</template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, defineProps } from "vue";

const props = defineProps({
  family: {
    type: String,
    required: false,
    default: null,
  },
  actions: {
    type: Object,
    required: true,
  },
  loading: {
    type: Boolean,
    required: true,
  },
});

const { formattedBytes } = useUtilFunctions();
const title = computed(() => {
  let title = "Action Metrics";
  if (props.family) {
    const family = props.family.charAt(0).toUpperCase() + props.family.slice(1).toLowerCase();
    title = `${family} ${title}`;
  }
  return title;
});
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
      if (key !== "action_name") metricNames.add(key);
    });
  });
  return Array.from(metricNames)
    .sort()
    .map((metricName) => {
      return {
        field: metricName,
        header: metricHeader(metricName),
      };
    });
});
const tableData = computed(() => {
  return rows.value.length > 0 && columns.value.length > 0 ? rows : [];
});
const metricHeader = (metricName) => {
  // Metrics names should always be snake case
  const words = metricName.split("_").map((word) => {
    return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
  });
  return words.join(" ");
};
const formatMatricValue = (row) => {
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
