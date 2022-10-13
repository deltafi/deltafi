<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
    <PageHeader heading="User Management">
      <Button label="Add User" icon="pi pi-plus" class="p-button-sm p-button-outlined" @click="newUser" />
    </PageHeader>
    <Panel header="Users" class="users-panel table-panel">
      <DataTable :value="users" data-Key="id" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" :row-hover="true">
        <template #header>
          <div style="text-align:left">
            <MultiSelect :model-value="selectedColumns" :options="columns" option-label="header" placeholder="Select Columns" style="width: 22rem" @update:model-value="onToggle" />
          </div>
        </template>
        <template #empty>
          No users to display
        </template>
        <Column v-for="(col, index) of selectedColumns" :key="col.field + '_' + index" :field="col.field" :header="col.header" :sortable="col.sortable" :class="col.class">
          <template #body="{ data, field }">
            <span v-if="field == 'domains'">
              <span v-for="domain in data.domains.split(',')" :key="domain" class="badge badge-pill mr-2">{{ domain }}</span>
            </span>
            <span v-else-if="['created_at', 'updated_at'].includes(field)">
              <Timestamp :timestamp="data[field]" format="YYYY-MM-DD HH:mm:ss"></Timestamp>
            </span>
            <span v-else>{{ data[field] }}</span>
          </template>
        </Column>
        <Column style="width: 5rem; padding: 0;">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" @click="editUser(data)" />
            <Button icon="pi pi-trash" class="p-button-text p-button-sm p-button-rounded p-button-danger" @click="confirmDeleteUser(data)" />
          </template>
        </Column>
      </DataTable>
    </Panel>
    <Dialog v-model:visible="userDialog" :style="{ width: '50vw' }" header="User Details" :modal="true" class="p-fluid user-dialog">
      <Message v-if="errors.length" severity="error">
        <div v-for="error in errors" :key="error">{{ error }}</div>
      </Message>
      <h5>Name</h5>
      <div class="field mb-2">
        <InputText id="name" v-model="user.name" autofocus :class="{ 'p-invalid': submitted && !user.name }" autocomplete="off" placeholder="e.g. Jane Doe" />
      </div>
      <h5 class="mt-3">Domains</h5>
      <div class="field mb-2">
        <Chips v-model="domains" :add-on-blur="true" />
      </div>
      <h5 class="mt-3">Authentication</h5>
      <TabView :active-index="activeTabIndex">
        <TabPanel header="Basic">
          <Message v-if="uiConfig.authMode != 'basic'" severity="warn">
            Authentication mode is currently set to <strong>{{ uiConfig.authMode }}</strong>.
            This must be set to <strong>basic</strong> before changes to this section will take effect.
          </Message>
          <div class="field mb-2">
            <label for="dn">Username</label>
            <InputText id="username" v-model="user.username" autocomplete="off" placeholder="janedoe" />
          </div>
          <div class="field mb-2">
            <label for="dn">Password</label>
            <Password v-model="user.password" autocomplete="off" toggle-mask></Password>
          </div>
        </TabPanel>
        <TabPanel header="Certificate">
          <Message v-if="uiConfig.authMode != 'cert'" severity="warn">
            Authentication mode is currently set to <strong>{{ uiConfig.authMode }}</strong>.
            This must be set to <strong>cert</strong> before changes to this section will take effect.
          </Message>
          <div class="field mb-2">
            <label for="dn">Distinguished Name (DN)</label>
            <InputText id="DN" v-model="user.dn" autocomplete="off" placeholder="e.g. CN=Jane Doe, OU=Sales, O=Acme Corporation, C=US" />
          </div>
        </TabPanel>
      </TabView>
      <template #footer>
        <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="hideDialog" />
        <Button label="Save" icon="pi pi-check" class="p-button-text" @click="saveUser" />
      </template>
    </Dialog>
    <Dialog v-model:visible="deleteUserDialog" :style="{ width: '450px' }" header="Confirm" :modal="true">
      <div class="confirmation-content">
        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
        <span v-if="user">Are you sure you want to delete <b>{{ user.name }}</b>?</span>
      </div>
      <template #footer>
        <Button label="No" icon="pi pi-times" class="p-button-text" @click="deleteUserDialog = false" />
        <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteUser" />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, inject, watch } from "vue";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";
import Password from "primevue/password";
import Chips from "primevue/chips";
import Message from "primevue/message";
import Button from "primevue/button";
import PageHeader from "@/components/PageHeader.vue";
import Panel from "primevue/panel";
import useUsers from "@/composables/useUsers";
import Timestamp from "@/components/Timestamp.vue";
import TabView from 'primevue/tabview';
import TabPanel from 'primevue/tabpanel';
import MultiSelect from 'primevue/multiselect'

const submitted = ref(false);
const user = ref({});
const userDialog = ref(false);
const deleteUserDialog = ref(false);
const isNew = ref(false);
const { data: users, fetch: fetchUsers, create, remove: removeUser, update: updateUser, errors } = useUsers();
const uiConfig = inject('uiConfig');
const domains = ref();
const activeTabIndex = ref(0);
const selectedColumns = ref([]);
const columns = ref([
  { field: "id", header: "ID", sortable: true, class: "id-col" },
  { field: "name", header: "Name", sortable: true },
  { field: "dn", header: "DN", sortable: true, class: "dn-col", hideInAuthMode: 'basic' },
  { field: "username", header: "Username", sortable: true, hideInAuthMode: 'cert' },
  { field: "domains", header: "Domains", sortable: true, class: "domains-col" },
  { field: "created_at", header: "Added", sortable: true, class: "timestamp-col" },
  { field: "updated_at", header: "Updated", sortable: true, class: "timestamp-col" },
]);

watch(uiConfig, () => {
  selectedColumns.value = columns.value.filter(col => col.hideInAuthMode !== uiConfig.authMode);
});

const onToggle = (val) => {
  selectedColumns.value = columns.value.filter(col => val.includes(col));
};

const hideDialog = () => {
  user.value = {};
  userDialog.value = false;
  isNew.value = false;
  deleteUserDialog.value = false;
};

const editUser = (userInfo) => {
  errors.value.splice(0, errors.value.length)
  user.value = { ...userInfo };
  domains.value = user.value.domains.split(',')
  isNew.value = false;
  userDialog.value = true;
};

const newUser = () => {
  errors.value.splice(0, errors.value.length)
  user.value = {};
  domains.value = [uiConfig.domain];
  isNew.value = true;
  submitted.value = false
  userDialog.value = true;
};

const saveUser = async () => {
  user.value.domains = domains.value.join(',');
  const { id, created_at, updated_at, ...saveParams } = user.value; // eslint-disable-line no-unused-vars
  try {
    isNew.value ? await create(saveParams) : await updateUser(user.value.id, saveParams)
    await fetchUsers();
    hideDialog();
  } catch {
    // No op - keep dialog open
  }
  submitted.value = true
};

const confirmDeleteUser = (userInfo) => {
  user.value = userInfo;
  deleteUserDialog.value = true;
};

const deleteUser = async () => {
  await removeUser(user.value);
  deleteUserDialog.value = false;
  fetchUsers();
};

onMounted(() => {
  fetchUsers();
  selectedColumns.value = columns.value.filter(col => col.hideInAuthMode !== uiConfig.authMode);;
});

watch(() => userDialog.value, (newValue) => {
  if (newValue) {
    activeTabIndex.value = uiConfig.authMode == 'cert' ? 1 : 0
  }
})
</script>

<style lang="scss">
@import "@/styles/pages/users-page.scss";
</style>
