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
      <DataTable :value="users" data-Key="id" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines">
        <template #empty>
          No users to display
        </template>
        <Column field="id" header="ID" :sortable="true" style="width: 1rem" />
        <Column field="name" header="Name" :sortable="true" />
        <Column field="dn" header="Distinguished Name (DN)" :sortable="true" class="dn-column" />
        <Column field="domains" header="Domains" :sortable="true" />
        <Column field="created_at" header="Created" :sortable="true" class="timestamp-column">
          <template #body="{ data }">
            <Timestamp :timestamp="data.created_at"></Timestamp>
          </template>
        </Column>
        <Column field="updated_at" header="Updated" :sortable="true" class="timestamp-column">
          <template #body="{ data }">
            <Timestamp :timestamp="data.updated_at"></Timestamp>
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
    <Dialog v-model:visible="userDialog" :style="{ width: '50vw' }" header="User Details" :modal="true" class="p-fluid">
      <Message v-if="errors.length" severity="error">
        <div v-for="error in errors" :key="error">{{ error }}</div>
      </Message>
      <div class="field mb-2">
        <label for="name">Name</label>
        <InputText id="name" v-model="user.name" autofocus :class="{ 'p-invalid': submitted && !user.name }" autocomplete="off" placeholder="e.g. Jane Doe" />
      </div>
      <div class="field mb-2">
        <label for="dn">Distinguished Name (DN)</label>
        <InputText id="DN" v-model="user.dn" :class="{ 'p-invalid': submitted && !user.dn }" autocomplete="off" placeholder="e.g. CN=Jane Doe, OU=Sales, O=Acme Corporation, C=US" />
      </div>
      <div class="field mb-2">
        <label for="domains">Domains</label>
        <InputText id="name" v-model="user.domains" :class="{ 'p-invalid': submitted && !user.domains }" autocomplete="off" :placeholder="`e.g. *${uiConfig.domain}`" />
      </div>
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
import { onMounted, ref, inject } from "vue";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import Button from "primevue/button";
import PageHeader from "@/components/PageHeader.vue";
import Panel from "primevue/panel";
import useUsers from "@/composables/useUsers";
import Timestamp from "@/components/Timestamp.vue";

const submitted = ref(false);
const user = ref({});
const userDialog = ref(false);
const deleteUserDialog = ref(false);
const isNew = ref(false);
const { data: users, fetch: fetchUsers, create, remove: removeUser, update: updateUser, errors } = useUsers();
const uiConfig = inject('uiConfig');

onMounted(() => {
  fetchUsers();
});

const hideDialog = () => {
  user.value = {};
  userDialog.value = false;
  isNew.value = false;
  deleteUserDialog.value = false;
};

const editUser = (userInfo) => {
  errors.value.splice(0, errors.value.length)
  user.value = { ...userInfo };
  isNew.value = false;
  userDialog.value = true;
};

const newUser = () => {
  errors.value.splice(0, errors.value.length)
  user.value = {};
  isNew.value = true;
  submitted.value = false
  userDialog.value = true;
};

const saveUser = async () => {
  const saveParams = { name: user.value.name, dn: user.value.dn, domains: user.value.domains };
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
  removeUser(user.value);
  deleteUserDialog.value = false;
  fetchUsers();
};
</script>

<style lang="scss">
@import "@/styles/pages/users-page.scss";
</style>
