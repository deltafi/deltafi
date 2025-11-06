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
  <div class="lookup-tables-results-page">
    <PageHeader>
      <template #header>
        <div class="d-flex align-items-center">
          <router-link class="text-dark" :to="{ path: '/admin/lookup-tables/' }">
            <h2 class="mb-0">Lookup Tables</h2>
          </router-link>
          <i class="fas fa-angle-right fa-fw" style="font-size: 1.5rem"></i>
          <h2 class="mb-0">{{ route.params.lookupTableName ? route.params.lookupTableName : null }}</h2>
        </div>
      </template>
    </PageHeader>
    <label>Filter:</label>
    <div class="btn-toolbar border-bottom mb-3 pb-3 align-items-center">
      <Button v-tooltip.right="{ value: `Clear Filters`, disabled: _.isEmpty(columnValueInputs) }" rounded :class="`mr-1 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${!_.isEmpty(columnValueInputs) ? 'p-column-filter-menu-button-active' : null}`" :disabled="_.isEmpty(columnValueInputs)" @click="clearFilterOptions()">
        <i class="pi pi-filter" style="font-size: 1rem" />
      </Button>

      <div id="columnValueId" class="annotations-chips">
        <Chip v-for="item in columnValueInputs" :key="item" removable class="mr-2 mb-1" @remove="removeColumnValueItem(item)"> {{ item.column }}: {{ item.value }} </Chip>
        <Chip class="add-column-value-btn" @click="showColumnValuesOverlay">
          &nbsp;
          <i class="pi pi-plus" />
          &nbsp;
        </Chip>
      </div>
      <OverlayPanel ref="columnValueOverlay" @hide="hideColumnValuesOverlay">
        <Dropdown v-model="newColumnKey" placeholder="Key" :options="filterColumnOptions" option-label="column" style="width: 13rem" @keyup.enter="addColumnValueItemEvent" /> :
        <AutoComplete v-model.trim="newColumnValue" placeholder="Value" :suggestions="items" :disabled="_.isEmpty(newColumnKey)" @complete="search($event, newColumnKey)" @keyup.enter="addColumnValueItemEvent" @item-select="addColumnValueItemEvent" />
      </OverlayPanel>
    </div>
    <Panel header="Results" class="table-panel results">
      <ContextMenu ref="menu" :model="menuItems" />
      <template #icons>
        <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
          <span class="fas fa-bars" />
        </Button>
        <Menu ref="menu" :model="menuItems" :popup="true" />
        <Paginator v-if="lookupDataTable.length > 0" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :first="offset" :rows="rowsPerPage" :total-records="totalRecords" :rows-per-page-options="[1, 10, 20, 50, 100, 1000]" style="float: left" @page="onPage" />
      </template>
      <DataTable v-model:selection="selectedRows" selection-mode="multiple" v-model:editingRows="editingRows" editMode="row" :value="lookupDataTable" :data-key="getDataKey" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" loading-icon="pi pi-spinner" @row-edit-save="onRowEditSave">
        <template #header>
          <div class="btn-toolbar align-items-center" style="text-align: left">
            <Button v-tooltip.right="{ value: `Clear Filters`, disabled: _.isEqual(selectedColumns, nonKeyColumnsArray) }" rounded :class="`mr-1 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${!_.isEqual(selectedColumns, nonKeyColumnsArray) ? 'p-column-filter-menu-button-active' : null}`" :disabled="_.isEqual(selectedColumns, nonKeyColumnsArray)" @click="clearMultiSelectOptions()">
              <i class="pi pi-filter" style="font-size: 1rem" />
            </Button>
            <MultiSelect :model-value="selectedColumns" :options="nonKeyColumnsArray" option-label="header" placeholder="Select Columns" style="width: 22rem" @update:model-value="onToggleSelectableColumn" />
          </div>
        </template>
        <template #empty> No rows data found </template>
        <Column v-for="(col, index) of viewableTableData" :key="col.field + '_' + index" :field="col.field" :header="col.header" :sortable="true" style="width: rem">
          <template v-if="_.isEqual(col.field, 'last_updated')" #body="{ data }">
            <Timestamp :timestamp="data[col.field]" />
          </template>
          <template v-else #body="{ data }">
            {{ data[col.field] }}
          </template>
          <template #editor="{ data, field }">
            <InputText v-model="data[field]" />
          </template>
        </Column>
        <Column :rowEditor="true" style="width: 8rem; min-width: 8rem" bodyStyle="text-align:center"></Column>
        <Column style="width: 3rem">
          <template #body="{ data, index }">
            <LookupTableRowRemoveButton :all-data-prop="lookupTableData" :row-data-prop="data" :index="index" @reload-lookup-tables="refresh" />
          </template>
        </Column>
      </DataTable>
    </Panel>
    <DialogTemplate ref="addLookupTableRow" component-name="lookupTable/LookupTableAddRow" header="Add Lookup Table Row" dialog-width="30vw" :row-data-prop="lookupTableData" edit-lookup-table @refresh-page="refresh" />
  </div>
</template>

<script setup>
import DialogTemplate from "@/components/DialogTemplate.vue";
import LookupTableRowRemoveButton from "@/components/lookupTable/LookupTableRowRemoveButton.vue";
import Timestamp from "@/components/Timestamp.vue";
import useLookupTable from "@/composables/useLookupTable";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { useStorage, StorageSerializers } from "@vueuse/core";

import _ from "lodash";

import AutoComplete from "primevue/autocomplete";
import Button from "primevue/button";
import Chip from "primevue/chip";
import Column from "primevue/column";
import ContextMenu from "primevue/contextmenu";
import DataTable from "primevue/datatable";
import Dropdown from "primevue/dropdown";
import InputText from "primevue/inputtext";
import Menu from "primevue/menu";
import MultiSelect from "primevue/multiselect";
import OverlayPanel from "primevue/overlaypanel";
import Paginator from "primevue/paginator";
import Panel from "primevue/panel";

const notify = useNotifications();
const hasPermission = inject("hasPermission");
const emit = defineEmits(["refreshAndClose"]);

const storageKey = "user-selected-columns-by-lookup-table-name-storage";

const lookupTableData = ref({});
const selectedColumns = ref([]);

const rowsPerPage = ref(20);
const offset = ref(0);
const sortDirection = ref(null);
const sortColumn = ref(null);
const totalRecords = ref(0);

const selectedRows = ref([]);
const menu = ref();
const editingRows = ref([]);
const lookupDataTable = ref([]);

const addLookupTableRow = ref(null);
const columnValueInputs = ref([]);
const columnValueOverlay = ref(null);
const newColumnKey = ref(null);
const newColumnValue = ref(null);

const { getLookupTable, lookup, removeLookupTableRows, upsertLookupTableRows } = useLookupTable();
const route = useRoute();

onMounted(async () => {
  if (!route.params.lookupTableName) {
    notify.error("No Lookup Table was provided.");
    return;
  }
  const response = await getLookupTable(route.params.lookupTableName);
  lookupTableData.value = await response.data.getLookupTable;
  if (_.isEmpty(lookupTableData.value)) {
    notify.error(`Lookup Table ${route.params.lookupTableName} was not found.`);
    return;
  }
  getPersistedParams(lookupTableData.value.name);
  await getLookupTableResults();
});

const getDataKey = (row) => {
  const pickedKeyValues = _.pick(row, lookupTableData.value.keyColumns);
  const pickedValues = _.values(pickedKeyValues);
  const dataKey = _.join(pickedValues, "-");

  return dataKey;
};

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

const items = ref([]);

const search = (event, key) => {
  items.value = _.filter(_.uniq(_.map(lookupDataTable.value, key.column)), (str) => {
    return _.includes(str, event.query);
  });
};

const menuItems = ref([
  {
    label: "Clear Selected",
    icon: "fas fa-times fa-fw",
    command: () => {
      selectedRows.value = [];
    },
    disabled: computed(() => selectedRows.value.length == 0),
  },
  {
    label: "Select All Visible",
    icon: "fas fa-check-double fa-fw",
    command: () => {
      selectedRows.value = lookupDataTable.value;
    },
  },
  {
    label: "Add Row",
    icon: "text-muted pi pi-pencil",
    visible: computed(() => hasPermission("LookupTableUpdate")),
    command: () => {
      addLookupTableRow.value.showDialog();
    },
  },
  {
    separator: true,
    visible: computed(() => hasPermission("LookupTableDelete")),
  },
  {
    label: "Delete Selected Rows",
    icon: "fas fa-trash fa-fw",
    command: () => {
      deleteRow();
    },
    visible: computed(() => hasPermission("LookupTableDelete")),
    disabled: computed(() => selectedRows.value.length == 0),
  },
]);

const onPage = (event) => {
  offset.value = event.first;
  rowsPerPage.value = event.rows;
  refresh();
};

const refresh = async () => {
  await getLookupTableResults();
};

const getLookupTableResults = async () => {
  const response = await lookup(lookupTableData.value.name, searchColumnValues.value, searchSelectedColumns.value, sortColumn.value, sortDirection.value, offset.value, rowsPerPage.value);
  const allLookupDataTable = response.data.lookup;

  totalRecords.value = allLookupDataTable.totalCount;

  // Transform the rows
  const formattedRows = _.map(allLookupDataTable.rows, (row) => {
    const rowObj = {};
    for (const cell of row) {
      rowObj[cell.column] = cell.value;
    }
    return rowObj;
  });

  lookupDataTable.value = formattedRows;
};

// The nonKeyColumnsArray should only have the list of columns that are not in the keyColumns
const nonKeyColumnsArray = computed(() => {
  if (_.isEmpty(lookupTableData.value)) {
    return [];
  }
  // Separate out the key columns from the remaining elements
  const nonKeyColumns = _.reject(lookupTableData.value.columns, (item) => lookupTableData.value.keyColumns.includes(item));

  const keyColumnsReorderedList = [...nonKeyColumns];

  return _.map(keyColumnsReorderedList, (key) => ({ field: key, header: key }));
});

// The required keyColumns should always appear first in the table plus the user selected columns they want to view
const viewableTableData = computed(() => {
  if (_.isEmpty(lookupTableData.value)) {
    return [];
  }
  const requiredKeyColumn = _.map(lookupTableData.value.keyColumns, (key) => ({ field: key, header: `${key} *` }));

  const lastUpdatedColumn = [{ field: "last_updated", header: "Last Updated" }];

  return [...requiredKeyColumn, ...selectedColumns.value, ...lastUpdatedColumn];
});

// When a column is selected or unselected, add it or remove it from the selectedColumns array
const onToggleSelectableColumn = async (newSelectedColumns) => {
  setPersistedParams(lookupTableData.value.name, _.isEqual(nonKeyColumnsArray.value, newSelectedColumns) ? null : newSelectedColumns);

  selectedColumns.value = _.filter(nonKeyColumnsArray.value, (nonKeyColumn) => _.some(newSelectedColumns, (newSelectedColumn) => _.isEqual(nonKeyColumn, newSelectedColumn)));
  await refresh();
};

// Check if the row data has changed from its original values
const originalRowDataChanged = (data, newData) => {
  return !_.isEqual(data, newData);
};

// Check if the Key Columns have non-null/empty values
const validRowChange = (row) => {
  // Pick out the key columns of the updated row
  const pickedObject = _.pick(row, lookupTableData.value.keyColumns);
  // Check if any of the values are null
  const hasNull = _.some(_.values(pickedObject), _.isNull);

  if (hasNull) {
    return false;
  }

  return true;
};

const convertRowToPairs = (row) => {
  return _.map(_.toPairs(row), ([column, value]) => ({ column, value }));
};

const keyColumnsChanged = async (data, newData, index) => {
  const originalKeyColumns = _.pick(data, lookupTableData.value.keyColumns);
  const newKeyColumns = _.pick(newData, lookupTableData.value.keyColumns);

  const isKeyColumnsChanged = !_.isEqual(originalKeyColumns, newKeyColumns);

  if (isKeyColumnsChanged) {
    // TODO: Add error handling
    const remove = await removeLookupTableRows(lookupTableData.value.name, [convertRowToPairs(data)]);
  }
};

const onRowEditSave = async (event) => {
  const { data, newData, index } = event;

  if (!originalRowDataChanged(data, newData)) {
    return;
  }

  if (!validRowChange(newData)) {
    lookupDataTable.value[index] = data;
    return;
  }

  keyColumnsChanged(data, newData, index);

  const upsert = await upsertLookupTableRows(lookupTableData.value.name, [convertRowToPairs(newData)]);
  // TODO: Add error handling
  lookupDataTable.value[index] = newData;
};

const deleteRow = async () => {
  const formatSelectedRows = [];

  for (const row of selectedRows.value) {
    formatSelectedRows.push(convertRowToPairs(row));
  }
  // TODO: Add error handling
  const response = await removeLookupTableRows(lookupTableData.value.name, formatSelectedRows);
  if (!_.isEmpty(response.data.removeLookupTableRows.errors)) {
    notify.error("Error deleting rows", response.data.removeLookupTableRows.errors);
  } else {
    notify.success("Rows deleted successfully");
    refresh();
  }
};

const showColumnValuesOverlay = (event) => {
  columnValueOverlay.value.toggle(event);
};

const removeColumnValueItem = (item) => {
  const index = columnValueInputs.value.indexOf(item);
  columnValueInputs.value.splice(index, 1);
  refresh();
};

const addColumnValueItem = (column, value) => {
  columnValueInputs.value.push({ column: column, value: value });
  refresh();
};

const addColumnValueItemEvent = () => {
  if (newColumnKey.value && newColumnValue.value) {
    addColumnValueItem(newColumnKey.value.column, newColumnValue.value);
    newColumnKey.value = null;
    newColumnValue.value = null;
    columnValueOverlay.value.toggle();
  }
};

const hideColumnValuesOverlay = () => {
  newColumnKey.value = null;
  newColumnValue.value = null;
};

// The keyColumns should always appear first in the table, move them to the front if they are not.
const searchColumnValues = computed(() => {
  if (columnValueInputs.value.length === 0) {
    return null;
  }

  return columnValueInputs.value;
});

// Provides the search query with the field values of all columns showing in the table
const searchSelectedColumns = computed(() => {
  return _.map(viewableTableData.value, "field");
});

// The filterColumnOptions should only show a list of options that reflects whats currently showing in the table minus any filters options already applied
const filterColumnOptions = computed(() => {
  // This is all the columns that have been selected for viewing in the table
  let allAvailableColumns = _.cloneDeep(viewableTableData.value).map((obj) => {
    return { column: obj.field };
  });

  if (columnValueInputs.value.length === 0) {
    return allAvailableColumns;
  }

  const alreadySelectedColumns = _.cloneDeep(columnValueInputs.value).map((obj) => {
    return { column: obj.column };
  });

  // Find columns in allAvailableColumns that have not been selected for a filter and are not present in columnValueInputs based on 'key'
  allAvailableColumns = _.differenceWith(allAvailableColumns, alreadySelectedColumns, (obj1, obj2) => {
    return obj1.column === obj2.column;
  });

  return allAvailableColumns;
});

const clearFilterOptions = () => {
  columnValueInputs.value = [];
  refresh();
};

const clearMultiSelectOptions = () => {
  selectedColumns.value = nonKeyColumnsArray.value;
  setPersistedParams(lookupTableData.value.name, null);
  refresh();
};

const getPersistedParams = async (tableName) => {
  const storedUserSelectedColumns = useStorage(storageKey, {}, sessionStorage, { serializer: StorageSerializers.object });
  if (_.has(storedUserSelectedColumns.value, tableName)) {
    if (!_.isEmpty(storedUserSelectedColumns.value[tableName])) {
      selectedColumns.value = storedUserSelectedColumns.value[tableName];
    } else {
      selectedColumns.value = nonKeyColumnsArray.value;
    }
  } else {
    selectedColumns.value = nonKeyColumnsArray.value;
  }
};

const setPersistedParams = (tableName, newSelectedColumns) => {
  const storedUserSelectedColumns = useStorage(storageKey, {}, sessionStorage, { serializer: StorageSerializers.object });
  storedUserSelectedColumns.value[tableName] = newSelectedColumns;
};
</script>
<style>
.lookup-tables-results-page {
  .results {
    .p-paginator-current {
      margin-right: 0.75rem;
      font-weight: 500;
    }

    .p-panel-header {
      padding: 0 1.25rem;

      .p-panel-title {
        padding: 1rem 0;
      }

      .p-panel-header-icon {
        margin-top: 0.25rem;
        margin-right: 0;
      }
    }

    .p-panel-content {
      padding: 0 !important;
      border: none;

      td.filename-column {
        overflow-wrap: anywhere;
      }
    }
  }

  .p-paginator {
    background: inherit !important;
    color: inherit !important;
    border: none !important;
    padding: 0 !important;
    font-size: inherit !important;

    .p-paginator-current {
      background: unset;
      color: unset;
      border: unset;
    }
  }

  .add-column-value-btn {
    cursor: pointer;
    background: var(--secondary);
    color: var(--primary-color-text);
    padding: 0 0.25rem;
  }
}
</style>
