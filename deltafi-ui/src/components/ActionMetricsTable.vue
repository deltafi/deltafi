<template>
  <div>
    <CollapsiblePanel :header="title" class="metrics-panel">
      <DataTable :value="tableData" striped-rows class="p-datatable-sm p-datatable-gridlines" :loading="loading" sort-field="action_name" :sort-order="1">
        <template #empty>
          No {{ title }} available.
        </template>
        <template #loading>
          Loading {{ title }} data. Please wait.
        </template>
        <Column header="Action Name" field="action_name" :sortable="true" />
        <Column v-for="col of columns" :key="col.field" :field="col.field" :header="col.header" :sortable="true" class="metric-column">
          <template #body="row">
            {{ formatMatricValue(row) }}
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import * as filesize from "filesize";

export default {
  name: "ActionMetricsTable",
  components: {
    DataTable,
    Column,
    CollapsiblePanel
  },
  props: {
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
  },
  computed: {
    title() {
      let title = "Action Metrics";
      if (this.family) {
        const family =
          this.family.charAt(0).toUpperCase() +
          this.family.slice(1).toLowerCase();
        title = `${family} ${title}`;
      }
      return title;
    },
    rows() {
      if (this.actions.length === 0) return [];
      const actions = this.actions;
      return Object.keys(actions).map(function (action_name) {
        let metrics = actions[action_name];
        metrics["action_name"] = action_name;
        return metrics;
      });
    },
    columns() {
      if (this.actions.length === 0) return [];
      let metricNames = new Set();
      this.rows.forEach((row) => {
        Object.keys(row).forEach((key) => {
          if (key !== "action_name") metricNames.add(key);
        });
      });
      return Array.from(metricNames)
        .sort()
        .map((metricName) => {
          return {
            field: metricName,
            header: this.metricHeader(metricName),
          };
        });
    },
    tableData() {
      return this.rows.length > 0 && this.columns.length > 0 ? this.rows : [];
    },
  },
  created() {},
  methods: {
    metricHeader(metricName) {
      // Metrics names should always be snake case
      const words = metricName.split("_").map((word) => {
        return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
      });
      return words.join(" ");
    },
    formatMatricValue(row) {
      const field = row.column.key;
      const value = row.data[field] || 0;
      if (value === undefined || value === null) {
        return null;
      } else if (field.includes("bytes")) {
        return this.formattedBytes(value);
      } else {
        return value;
      }
    },
    formattedBytes(bytes) {
      return filesize(bytes || 0, { base: 10 });
    },
  },
};
</script>
