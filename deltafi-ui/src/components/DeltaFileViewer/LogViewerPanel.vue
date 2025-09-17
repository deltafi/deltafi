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
  <div class="log-viewer-panel">
    <CollapsiblePanel header="Log Messages" class="table-panel">
      <template #header>
        <div class="d-flex align-items-center btn-group">
          <div class="p-panel-title">Log Messages</div>
          <div class="d-flex justify-content-left align-items-end gap-2 btn-group pl-2">
            <LogBadges :errorCount="hasErrors" :warningCount="hasWarnings" :userCount="hasUserNotes" @click="filterLogsBySeverity"/>
          </div>
        </div>
      </template>
      <template #icons>
        <div class="float-left">
          <div class="btn-group align-items-center">
            <Button v-tooltip.right="{ value: `Clear Filters`, disabled: !filterOptionsSelected }" rounded :class="`mr-1 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${filterOptionsSelected ? 'p-column-filter-menu-button-active' : null}`" :disabled="!filterOptionsSelected" @click="clearOptions()">
              <i class="pi pi-filter" style="font-size: 1rem" />
            </Button>
            <IconField iconPosition="left">
              <InputIcon class="pi pi-search" />
              <InputText v-model="filters['global'].value" type="text" placeholder="Search" class="p-inputtext deltafi-input-field mx-1" />
            </IconField>
            <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
              <span class="fas fa-bars" />
            </Button>
            <Menu ref="menu" :model="menuItems" :popup="true" />
          </div>
        </div>
      </template>
      <DataTable v-model:filters="filters" dataKey="created" responsive-layout="scroll" class="p-datatable p-datatable-sm p-datatable-gridlines" striped-rows :value="deltaFile.messages" :globalFilterFields="['message', 'source', 'severity']">
        <template #empty> No Logs </template>
        <Column header="Message" field="message" :style="{ width: '55%' }">
          <template #body="{ data }">
            <pre v-if="data.severity">{{ data.message }}</pre>
            <span v-else>{{ data.message }}</span>
          </template>
        </Column>
        <Column header="Source" field="source" :style="{ width: '15%' }" />
        <Column header="Severity" field="severity" :showFilterMenu="false" sortable :style="{ width: '10%' }">
          <template #body="{ data }">
            <Tag :value="data.severity" :severity="getSeverity(data.severity)" class="py-0" />
          </template>
        </Column>
        <Column header="Created" field="created" :style="{ width: '20%' }">
          <template #body="row">
            <Timestamp :timestamp="row.data.created" />
          </template>
        </Column>
      </DataTable>
    </CollapsiblePanel>
    <DialogTemplate ref="addUserNote" component-name="DeltaFileViewer/AddUserNoteDialog" header="Add User Note" dialog-width="30vw" :did="deltaFile.did" @refresh-page="$emit('refreshPage')" />
  </div>
</template>

<script setup>
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import Timestamp from "@/components/Timestamp.vue";
import LogBadges from "@/components/DeltaFileViewer/LogBadges.vue";
import { computed, ref, reactive, watch } from "vue";

import _ from "lodash";

import { FilterMatchMode } from "primevue/api";
import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";
import Menu from "primevue/menu";
import Tag from "primevue/tag";

const props = defineProps({
  deltaFileData: {
    type: Object,
    required: true,
  },
  filterLogSeverity: {
    type: String,
    required: false,
    default: null,
  },
});
const deltaFile = reactive(props.deltaFileData);
const emit = defineEmits(["refreshPage"]);

const menu = ref();
const addUserNote = ref(null);
const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
  severity: { value: null, matchMode: FilterMatchMode.EQUALS },
});

watch(
  () => props.filterLogSeverity,
  () => {
    filters.value["severity"].value = _.isEmpty(props.filterLogSeverity) ? null : props.filterLogSeverity;
  }
);

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

const severityOptions = computed(() => {
  return _.uniq(_.map(deltaFile.messages, "severity"));
});

const menuItems = ref([
  {
    label: "Add User Note",
    icon: "fas fa-pen-to-square fa-fw",
    command: () => {
      addUserNote.value.showDialog();
    },
  },
]);

const filterOptionsSelected = computed(() => {
  const formDirty = _.some([filters.value["severity"].value, filters.value["global"].value], (value) => !(value === "" || value === null || value === undefined));

  return formDirty;
});

const clearOptions = () => {
  filters.value["severity"].value = null;
  filters.value["global"].value = null;
};

const getSeverity = (severity) => {
  switch (severity) {
    case "ERROR":
      return "danger";
    case "INFO":
      return "info";
    case "WARNING":
      return "warning";
    case null:
      return null;
  }
};

const hasErrors = computed(() => {
  return _.filter(deltaFile.messages, { severity: "ERROR" }).length;
});

const hasWarnings = computed(() => {
  return _.filter(deltaFile.messages, { severity: "WARNING" }).length;
});

const hasUserNotes = computed(() => {
  return _.filter(deltaFile.messages, { severity: "USER" }).length;
});

const filterLogsBySeverity = (severity) => {
  filters.value["severity"].value = severity;
};
</script>

<style lang="scss">
.log-viewer-panel {
  .p-panel.p-panel-toggleable .p-panel-header {
    padding: 5px 1.25rem;
  }
}
</style>
