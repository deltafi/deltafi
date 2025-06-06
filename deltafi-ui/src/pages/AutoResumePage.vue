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
  <div class="auto-resume-page">
    <PageHeader heading="Auto Resume">
      <div class="d-flex">
        <Button label="Export Rules" icon="fas fa-download fa-fw" class="p-button-sm p-button-secondary p-button-outlined mx-1" @click="exportAutoResume()" />
        <AutoResumeImportFile v-has-permission:ResumePolicyCreate @refresh-page="fetchAutoResumeRules()" />
        <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="Add New Auto Resume Rule" required-permission="ResumePolicyCreate" dialog-width="75vw" :row-data-prop="{}" @refresh-page="fetchAutoResumeRules()">
          <Button v-has-permission:ResumePolicyCreate label="Add Rule" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" />
        </DialogTemplate>
      </div>
    </PageHeader>
    <Panel header="Rules" class="auto-resume-panel table-panel" @contextmenu="onPanelRightClick">
      <ContextMenu ref="menu" :model="menuItems" />
      <template #icons>
        <div class="btn-group align-items-center">
          <IconField iconPosition="left">
            <InputIcon class="pi pi-search"> </InputIcon>
            <InputText v-model="filters['global'].value" class="p-inputtext-sm deltafi-input-field mx-1" placeholder="Search" />
          </IconField>
          <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
            <span class="fas fa-bars" />
          </Button>
        </div>
        <Menu ref="menu" :model="menuItems" :popup="true" />
      </template>
      <DataTable v-model:selection="selectedRules" v-model:filters="filters" :edit-mode="$hasPermission('ResumePolicyUpdate') ? 'cell' : null" :value="uiAutoResumeRules" :loading="loading && !loaded" data-Key="id" selection-mode="multiple" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines auto-resume-table" :global-filter-fields="['dataSource', 'errorSubstring', 'action']" :row-hover="true" @row-contextmenu="onRowContextMenu" @cell-edit-complete="onCellEditComplete">
        <template #empty> No Auto Resume rules to display </template>
        <Column field="name" header="Name" :sortable="true" :style="{ width: '15%' }">
          <template #body="{ data }">
            <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="View Auto Resume Rule" dialog-width="75vw" :row-data-prop="data" view-auto-resume-rule @refresh-page="fetchAutoResumeRules()">
              <a class="cursor-pointer" style="color: black">{{ data.name }}</a>
            </DialogTemplate>
          </template>
        </Column>
        <Column field="errorSubstring" header="Error Substring" :sortable="true" />
        <Column field="dataSource" header="Data Source" :sortable="true" />
        <Column field="action" header="Action" :sortable="true" />
        <Column field="priority" header="Priority" :sortable="true" class="priority-column">
          <template #body="{ data, field }">
            <span v-if="data[field] === null">-</span>
            <span v-else>{{ data[field] }}</span>
          </template>
          <template #editor="{ data, field }">
            <InputNumber v-model="data[field]" class="p-inputtext-sm priority-column-input" autofocus />
          </template>
        </Column>
        <Column :style="{ width: '5%' }" :body-style="{ padding: 0 }" :hidden="!$hasSomePermissions('ResumePolicyUpdate', 'ResumePolicyDelete')">
          <template #body="{ data }">
            <div class="d-flex">
              <DialogTemplate component-name="autoResume/AutoResumeConfigurationDialog" header="Edit Auto Resume Rule" required-permission="ResumePolicyUpdate" dialog-width="75vw" :row-data-prop="data" @refresh-page="fetchAutoResumeRules()">
                <Button v-has-permission:ResumePolicyUpdate v-tooltip.top="`Edit Rule`" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" />
              </DialogTemplate>
              <AutoResumeRemoveButton v-has-permission:ResumePolicyDelete class="pl-2" :row-data-prop="data" @refresh-page="fetchAutoResumeRules()" />
            </div>
          </template>
        </Column>
      </DataTable>
    </Panel>
  </div>
</template>

<script setup>
import AutoResumeImportFile from "@/components/autoResume/AutoResumeImportFile.vue";
import AutoResumeRemoveButton from "@/components/autoResume/AutoResumeRemoveButton.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import PageHeader from "@/components/PageHeader.vue";
import useAutoResumeConfiguration from "@/composables/useAutoResumeConfiguration";
import useAutoResumeQueryBuilder from "@/composables/useAutoResumeQueryBuilder";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { nextTick, onMounted, ref, inject, computed } from "vue";

const hasPermission = inject("hasPermission");

import { FilterMatchMode } from "primevue/api";
import Button from "primevue/button";
import Column from "primevue/column";
import ContextMenu from "primevue/contextmenu";
import DataTable from "primevue/datatable";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";
import InputNumber from "primevue/inputnumber";
import Menu from "primevue/menu";
import Panel from "primevue/panel";

import _ from "lodash";

const menu = ref();
const selectedRules = ref([]);
const autoResumeRules = ref([]);
const uiAutoResumeRules = ref([]);
const hasSomePermissions = inject("hasSomePermissions");
const notify = useNotifications();
const { validateAutoResumeRule } = useAutoResumeConfiguration();
const { getAllResumePolicies, updateResumePolicy, applyResumePolicies, loaded, loading } = useAutoResumeQueryBuilder();
const { pluralize } = useUtilFunctions();

const menuItems = ref([
  {
    label: "Clear Selected",
    icon: "fas fa-times fa-fw",
    command: () => {
      selectedRules.value = [];
    },
  },
  {
    label: "Select All",
    icon: "fas fa-check-double fa-fw",
    command: () => {
      selectedRules.value = uiAutoResumeRules.value;
    },
  },
  {
    separator: true,
    visible: computed(() => hasSomePermissions("DeltaFileAcknowledge", "DeltaFileResume", "ResumePolicyCreate")),
  },
  {
    label: "Run Selected Now",
    icon: "fas fa-sync fa-fw",
    command: () => {
      applyResume();
    },
    visible: computed(() => hasPermission("DeltaFileReplay")),
    disabled: computed(() => selectedRules.value.length == 0),
  },
]);

const applyResume = async () => {
  let response = null;
  response = await applyResumePolicies(formatSelectedRules.value);
  const pluralized = pluralize(formatSelectedRules.value.length, "Auto Resume Rule");
  if (response.data.applyResumePolicies.success) {
    notify.success(`${pluralized} Ran Successfully`, " - " + response.data.applyResumePolicies.info.join("<br/> - "));
  } else {
    notify.error(`${pluralized} Failed to Run`, response.data.applyResumePolicies.errors.join("<br/> - "));
  }
};

const onPanelRightClick = (event) => {
  menu.value.show(event);
};
const toggleMenu = (event) => {
  menu.value.toggle(event);
};
const onRowContextMenu = (event) => {
  if (selectedRules.value.length <= 0) {
    selectedRules.value = [event.data];
  }
};

const formatSelectedRules = computed(() => {
  return selectedRules.value.map((row) => {
    return row.name;
  });
});

onMounted(async () => {
  fetchAutoResumeRules();
});

const fetchAutoResumeRules = async () => {
  const autoResumeRulesResponse = await getAllResumePolicies();
  autoResumeRules.value = [];
  uiAutoResumeRules.value = [];
  await nextTick();
  autoResumeRules.value = autoResumeRulesResponse.data.getAllResumePolicies;
  uiAutoResumeRules.value = _.assign(autoResumeRules.value);
};

const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

const formatExportAutoResumeData = () => {
  const autoResumeRulesList = JSON.parse(JSON.stringify(autoResumeRules.value));
  // Remove the id key from route
  autoResumeRulesList.forEach((e, index) => (autoResumeRulesList[index] = _.omit(e, ["id"])));

  return autoResumeRulesList;
};

const exportAutoResume = () => {
  const link = document.createElement("a");
  const downloadFileName = "auto_resume_export_" + new Date(Date.now()).toLocaleDateString();
  link.download = downloadFileName.toLowerCase();
  const blob = new Blob([JSON.stringify(formatExportAutoResumeData(), null, 2)], {
    type: "application/json",
  });
  link.href = URL.createObjectURL(blob);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
};

const onCellEditComplete = async (event) => {
  const { data, newValue, field } = event;
  if (!_.isEqual(data.priority, newValue)) {
    const resetValue = data.priority;
    data[field] = newValue;
    let uploadNotValid = null;
    uploadNotValid = validateAutoResumeRule(JSON.stringify(data));
    const errorsList = [];
    if (uploadNotValid) {
      for (const errorMessages of uploadNotValid) {
        errorsList.push(errorMessages.message);
      }
      notify.error(`Auto Resume Rule Validation Errors`, "Priority " + errorsList, 4000);
      data[field] = resetValue;
    } else {
      let response = null;
      response = await updateResumePolicy(data);
      if (!_.isEmpty(_.get(response, "errors", null))) {
        notify.error(`Priority update failed`, `Unable to update Auto Resume Rule priorty.`, 4000);
        data[field] = resetValue;
      } else {
        if (!_.isEqual(data.priority, null)) {
          notify.success("Priority Set Successfully", `Priority for ${data.name} will be computed by the system automatically.`);
        } else {
          notify.success("Priority Set Successfully", `Priority for ${data.name} set to ${newValue}`);
        }
        fetchAutoResumeRules();
      }
    }
  }
};
</script>

<style>
.auto-resume-page {
  .auto-resume-panel {
    .p-panel-header {
      padding: 0 1.25rem;

      .p-panel-title {
        padding: 1rem 0;
      }
    }

    .list-item::before {
      content: "•";
      margin-right: 0.25rem;
      font-weight: bold;
    }

    .auto-resume-table {
      td.priority-column {
        width: 7rem;
        padding: 0 !important;

        > span {
          padding: 0.5rem !important;
        }

        .value-clickable {
          cursor: pointer;
          width: 100%;
          display: flex;
        }

        .value-clickable > * {
          flex: 0 0 auto;
        }

        .p-inputnumber {
          padding: 0 !important;
          margin: 0;

          .p-inputtext {
            width: 6.5rem;
          }
        }
      }
    }
  }
}
</style>
