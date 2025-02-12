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
  <div class="users-page">
    <PageHeader heading="User Management">
      <Button :hidden="!$hasPermission('UserCreate')" label="Add User" icon="pi pi-plus" class="p-button-sm p-button-outlined" @click="newUser" />
    </PageHeader>
    <Panel header="Users" class="users-panel table-panel">
      <DataTable :value="users" data-Key="id" :loading="loading && !loaded" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" :row-hover="true">
        <template #header>
          <div style="text-align: left">
            <MultiSelect :model-value="selectedColumns" :options="columns" option-label="header" placeholder="Select Columns" style="width: 22rem" @update:model-value="onToggle" />
          </div>
        </template>
        <template #empty>
          No users to display
        </template>
        <Column v-for="(col, index) of selectedColumns" :key="col.field + '_' + index" :field="col.field" :header="col.header" :sortable="col.sortable" :class="col.class">
          <template #body="{ data, field }">
            <span v-if="field == 'roles'">
              <RolePill v-for="role in data.roles" :key="role.id" class="mr-1" :role="role" />
            </span>
            <span v-else-if="field == 'permissions'">
              <PermissionPill v-for="permissionName in data.permissions" :key="permissionName" class="mr-1" :permission="appPermissionsByName[permissionName]" />
            </span>
            <span v-else-if="['createdAt', 'updatedAt'].includes(field)">
              <Timestamp :timestamp="data[field]" format="YYYY-MM-DD HH:mm:ss" />
            </span>
            <span v-else-if="['name'].includes(field)">
              <span class="cursor-pointer" @click="showUser(data)">{{ data.name }}</span>
            </span>
            <span v-else>{{ data[field] }}</span>
          </template>
        </Column>
        <Column style="width: 5rem; padding: 0" :hidden="!$hasSomePermissions('UserUpdate', 'UserDelete')">
          <template #body="{ data }">
            <Button v-tooltip.top="`Edit User ${data.name}`" :hidden="!$hasPermission('UserUpdate')" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" @click="editUser(data)" />
            <Button v-tooltip.top="`Remove User ${data.name}`" :hidden="!$hasPermission('UserDelete')" icon="pi pi-trash" class="p-button-text p-button-sm p-button-rounded p-button-danger" @click="confirmDeleteUser(data)" />
          </template>
        </Column>
      </DataTable>
    </Panel>
    <Dialog v-model:visible="userDialog" :style="{ width: '60vw' }" header="User Details" :modal="true" class="p-fluid users-page-dialog">
      <Message v-if="errors.length" severity="error">
        <div v-for="error in errors" :key="error">
          {{ error }}
        </div>
      </Message>
      <div>
        <dl>
          <dt>Name*</dt>
          <dd>
            <InputText id="name" v-model="user.name" autofocus :class="{ 'p-invalid': submitted && !user.name }" autocomplete="off" placeholder="e.g. Jane Doe" :disabled="isReadOnly" />
          </dd>

          <dt>Authentication</dt>
          <dd>
            <div class="deltafi-fieldset">
              <div class="px-2 pt-3">
                <TabView :active-index="activeTabIndex">
                  <TabPanel header="Basic">
                    <Message v-if="uiConfig.authMode != 'basic' && !isReadOnly" severity="warn">
                      Authentication mode is currently set to <strong>{{ uiConfig.authMode }}</strong>. This must be set to <strong>basic</strong> before changes to this section will take effect.
                    </Message>
                    <div class="field mb-2">
                      <label for="dn">Username*</label>
                      <InputText id="username" v-model="user.username" autocomplete="off" placeholder="janedoe" :disabled="isReadOnly" />
                    </div>
                    <div v-if="!isReadOnly" class="field mb-2">
                      <label for="dn">Password</label>
                      <Password v-model="user.password" autocomplete="off" toggle-mask />
                    </div>
                  </TabPanel>
                  <TabPanel header="Certificate">
                    <Message v-if="uiConfig.authMode != 'cert' && !isReadOnly" severity="warn">
                      Authentication mode is currently set to <strong>{{ uiConfig.authMode }}</strong>. This must be set to <strong>cert</strong> before changes to this section will take effect.
                    </Message>
                    <div class="field mb-2">
                      <label for="dn">Distinguished Name (DN)</label>
                      <InputText id="DN" v-model="user.dn" autocomplete="off" placeholder="e.g. CN=Jane Doe, OU=Sales, O=Acme Corporation, C=US" :disabled="isReadOnly" />
                    </div>
                  </TabPanel>
                </TabView>
              </div>
            </div>
          </dd>
          <dt>Roles</dt>
          <dd>
            <div class="deltafi-fieldset">
              <div class="px-2 pt-3">
                <div v-for="role in roles" :key="role.id" class="field-checkbox">
                  <Checkbox v-model="user.roleIds" :input-id="role.name" name="role" :value="role.id" :disabled="isReadOnly" />
                  <label :for="role.name">
                    <RolePill :role="role" :enabled="user.roleIds.includes(role.id)" />
                  </label>
                </div>
              </div>
            </div>
          </dd>
        </dl>
      </div>
      <div v-if="isReadOnly" class="mb-3">
        <h5>Permissions</h5>
        <PermissionCheckboxes v-model="user.permissions" :read-only="true" />
      </div>
      <template v-if="!isReadOnly" #footer>
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
import PermissionCheckboxes from "@/components/PermissionCheckboxes.vue";
import PermissionPill from "@/components/PermissionPill.vue";
import Timestamp from "@/components/Timestamp.vue";
import usePermissions from "@/composables/usePermissions";
import useRoles from "@/composables/useRoles";
import useUsers from "@/composables/useUsers";
import { onMounted, ref, inject, watch } from "vue";

import Button from "primevue/button";
import Checkbox from "primevue/checkbox";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import MultiSelect from "primevue/multiselect";
import PageHeader from "@/components/PageHeader.vue";
import Panel from "primevue/panel";
import Password from "primevue/password";
import RolePill from "@/components/RolePill.vue";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";

const submitted = ref(false);
const user = ref({ roleIds: [] });
const userDialog = ref(false);
const deleteUserDialog = ref(false);
const isNew = ref(false);
const isReadOnly = ref(false);
const { data: users, loading, loaded, fetch: fetchUsers, create, remove: removeUser, update: updateUser, errors } = useUsers();
const { data: roles, fetch: fetchRoles } = useRoles();
const { appPermissionsByName } = usePermissions();
const uiConfig = inject("uiConfig");
const activeTabIndex = ref(0);
const selectedColumns = ref([]);
const columns = ref([
  { field: "name", header: "Name", sortable: true },
  { field: "dn", header: "DN", sortable: true, class: "dn-col", hideInAuthMode: "basic" },
  { field: "username", header: "Username", sortable: true, hideInAuthMode: "cert" },
  { field: "roles", header: "Roles", sortable: true },
  { field: "permissions", header: "Permissions", sortable: true, hidden: true },
  { field: "createdAt", header: "Added", sortable: true, class: "timestamp-col" },
  { field: "updatedAt", header: "Updated", sortable: true, class: "timestamp-col" },
]);

const onToggle = (val) => {
  selectedColumns.value = columns.value.filter((col) => val.includes(col));
};

const hideDialog = () => {
  user.value = { roleIds: [] };
  userDialog.value = false;
  isNew.value = false;
  deleteUserDialog.value = false;
};

const editUser = (userInfo) => {
  errors.value.splice(0, errors.value.length);
  user.value = { ...userInfo };
  isNew.value = false;
  isReadOnly.value = false;
  userDialog.value = true;
};

const showUser = (userInfo) => {
  user.value = { ...userInfo };
  isNew.value = false;
  isReadOnly.value = true;
  userDialog.value = true;
};

const newUser = () => {
  errors.value.splice(0, errors.value.length);
  user.value = { roleIds: [] };
  isNew.value = true;
  isReadOnly.value = false;
  submitted.value = false;
  userDialog.value = true;
};

const saveUser = async () => {
  const { id, createdAt, updatedAt, roles, permissions, ...saveParams } = user.value;
  try {
    isNew.value ? await create(saveParams) : await updateUser(user.value.id, saveParams);
    await fetchUsers();
    hideDialog();
  } catch {
    // No op - keep dialog open
  }
  submitted.value = true;
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
  fetchRoles();
  selectedColumns.value = columns.value.filter((col) => col.hideInAuthMode !== uiConfig.authMode && !col.hidden);
});

watch(
  () => userDialog.value,
  (newValue) => {
    if (newValue) {
      activeTabIndex.value = uiConfig.authMode == "cert" ? 1 : 0;
    }
  }
);
</script>

<style>
.users-page {
  .users-panel {
    td.id-col {
      width: 1rem;
    }

    td.dn-col {
      font-family: monospace;
      font-size: 90%;
    }

    td.timestamp-col {
      font-size: 90%;
      width: 12rem;
    }

    td.domains-col {
      .badge {
        background-color: #dee2e6;
        font-size: 90%;
        font-weight: normal;
      }
    }
  }
}

.users-page-dialog {
  .p-tabview .p-tabview-panels {
    padding: 1rem 0 0 0;
  }

  .field-checkbox {
    display: flex;

    label {
      display: flex;
      align-items: center;
      margin-top: 0.15rem;
      margin-left: 0.4rem;
    }
  }

  .deltafi-fieldset {
    display: block;
    margin-inline-start: 2px;
    margin-inline-end: 2px;
    padding-block-start: 0.35em;
    padding-inline-start: 0.75em;
    padding-inline-end: 0.75em;
    padding-block-end: 0.625em;
    min-inline-size: min-content;
    border-radius: 4px;
    border: 1px solid #ced4da;
    border-width: 1px;
    border-style: groove;
    border-color: rgb(225, 225, 225);
    border-image: initial;
  }

  dt {
    margin-bottom: 0rem;
  }

  dd {
    margin-bottom: 1.2rem;
  }

  dl {
    margin-bottom: 1rem;
  }
}
</style>
