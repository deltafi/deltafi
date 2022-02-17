<template>
  <div>
    <CollapsiblePanel :header="propertySet.displayName" class="property-set mb-3 table-panel">
      <template #header>
        <span class="p-panel-title">
          {{ propertySet.displayName }}
          <i v-if="propertySet.description" v-tooltip.right="propertySet.description" class="ml-2 text-muted fas fa-info-circle fa-fw" />
        </span>
      </template>
      <DataTable responsive-layout="scroll" sort-field="key" :sort-order="1" :value="visibleProperties" edit-mode="cell" class="p-datatable-sm table-striped p-datatable-gridlines" @cell-edit-complete="onCellEditComplete">
        <template #empty>No properties in this property set.</template>
        <Column header="Key" field="key" :sortable="true">
          <template #body="property">
            <span :class="{ 'text-muted': !property.data.editable }">{{ property.data.key }}</span>
            <i v-if="tooltipText(property.data)" v-tooltip.right="tooltipText(property.data)" class="ml-2 text-muted fas fa-info-circle fa-fw" />
          </template>
        </Column>
        <Column header="Value" field="value" :sortable="true">
          <template #body="property">
            <span :class="{ 'text-muted': !property.data.editable }">{{ property.data.value }}</span>
          </template>
          <template #editor="{ data, field }">
            <InputText v-if="data.editable" v-model="data[field]" class="p-inputtext-sm" autofocus />
            <span v-else class="text-muted">{{ data.value }}</span>
          </template>
        </Column>
        <Column header="Source" field="propertySource" :sortable="true">
          <template #body="property">
            <span :class="{ 'text-muted': !property.data.editable }">{{ property.data.propertySource }}</span>
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
  </div>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import CollapsiblePanel from "@/components/CollapsiblePanel";
import useNotifications from "@/composables/useNotifications";
import usePropertySets from "@/composables/usePropertySets";
import { computed, defineProps, defineEmits } from "vue";

const propertySet = defineProps({
  propSet: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(["updated"]);

const notify = useNotifications();
const { data: propertySetData, update } = usePropertySets();
const visibleProperties = computed(() => propertySet.propSet.properties.filter((p) => !p.hidden));

const tooltipText = (property) => {
  let parts = [];
  if (property.description) parts.push(property.description);
  if (!property.editable) parts.push("(Read-only)");
  return parts.join(" ");
};

const updateProperty = async (setId, key, value, refreshable) => {
  await update([
    {
      propertySetId: setId,
      key: key,
      value: value,
    },
  ]);
  if (propertySetData.value.updateProperties > 0) {
    emit("updated");
    if (refreshable) {
      notify.success("Property update successful", key);
    } else {
      notify.warn("Property update successful", "System restart required for change to take effect!");
    }
  } else {
    notify.error("Property update failed");
  }
};

const onCellEditComplete = (event) => {
  let { data, newValue, value } = event;
  if (value !== newValue) {
    updateProperty(propertySet.propSet.id, data.key, newValue, data.refreshable);
    data.value = newValue;
  }
};
</script>
