<template>
  <div>
    <Panel :header="propSet.displayName" :toggleable="true" class="property-set mb-3">
      <template #header>
        <span class="p-panel-title">
          {{ propSet.displayName }}
          <i v-if="propSet.description" v-tooltip.right="propSet.description" class="ml-2 text-muted pi pi-info-circle" />
        </span>
      </template>
      <DataTable sort-field="key" :sort-order="1" :value="visibleProperties" edit-mode="cell" class="p-datatable-sm table-striped" @cell-edit-complete="onCellEditComplete">
        <template #empty>
          No properties in this property set.
        </template>
        <Column header="Key" field="key" :sortable="true">
          <template #body="property">
            <span :class="{'text-muted': !property.data.editable}">{{ property.data.key }}</span>
            <i v-if="tooltipText(property.data)" v-tooltip.right="tooltipText(property.data)" class="ml-2 text-muted pi pi-info-circle" />
          </template>
        </Column>
        <Column header="Value" field="value" :sortable="true">
          <template #body="property">
            <span :class="{'text-muted': !property.data.editable}">{{ property.data.value }}</span>
          </template>
          <template #editor="{ data, field }">
            <InputText v-if="data.editable" v-model="data[field]" class="p-inputtext-sm" autofocus />
            <span v-else class="text-muted">{{ data.value }}</span>
          </template>
        </Column>
        <Column header="Source" field="propertySource" :sortable="true">
          <template #body="property">
            <span :class="{'text-muted': !property.data.editable}">{{ property.data.propertySource }}</span>
          </template>
        </Column>
      </DataTable>
    </Panel>
    <Toast position="bottom-right" />
  </div>
</template>

<script>
import GraphQLService from "../service/GraphQLService";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Panel from "primevue/panel";
import InputText from "primevue/inputtext";
import Toast from 'primevue/toast';

export default {
  name: "PropertySet",
  components: {
    DataTable,
    Column,
    Panel,
    InputText,
    Toast
  },
  props: {
    propSet: {
      type: Object,
      required: true,
    }
  },
  computed: {
    visibleProperties() {
      return this.propSet.properties.filter((p) => !p.hidden);
    }
  },
  created() {
    this.graphQLService = new GraphQLService();
  },
  methods: {
    updateProperty(setId, key, value, refreshable) {
      this.graphQLService.updateProperties([
        {
          propertySetId: setId,
          key: key,
          value: value,
        },
      ]).then((response) => {
        this.$store.dispatch("fetchPropertySets");
        if (response.data.updateProperties > 0) {
          if (refreshable) {
            this.$toast.add({
              severity: "success",
              summary: "Property update successful",
              life: 3000,
            });
          } else {
            this.$toast.add({
              severity: "warn",
              summary: "Property update successful",
              detail: "System restart required for change to take effect!",
              life: 6000,
            });
          }
        } else {
          this.$toast.add({
            severity: "error",
            summary: "Property update failed",
            life: 3000,
          });
        }
      })
    },
    tooltipText(property) {
      let parts = [];
      if (property.description) parts.push(property.description);
      if (!property.editable) parts.push("(Read-only)");
      return parts.join(" ");
    },
    onCellEditComplete(event) {
      let { data, newValue, value} = event;
      if (value !== newValue) {
        this.updateProperty(this.propSet.id, data.key, newValue, data.refreshable)
        data.value = newValue
      }
    },
  },
  graphQLService: null,
};
</script>
