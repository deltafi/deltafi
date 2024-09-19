<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div class="roles-page">
    <PageHeader heading="Role Management">
      <Button :hidden="!$hasPermission('RoleCreate')" label="Add Role" icon="pi pi-plus" class="p-button-sm p-button-outlined" @click="newRole" />
    </PageHeader>
    <Panel header="Roles" class="roles-panel table-panel">
      <DataTable :value="roles" :loading="loading && !loaded" data-Key="id" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" :row-hover="true">
        <template #empty> No roles to display </template>
        <Column v-for="(col, index) of columns" :key="col.field + '_' + index" :field="col.field" :header="col.header" :sortable="col.sortable" :class="col.class">
          <template #body="{ data, field }">
            <span v-if="['permissions'].includes(field)">
              <PermissionPill v-for="permissionName in data.permissions" :key="permissionName" class="mr-1" :permission="appPermissionsByName[permissionName]" />
            </span>
            <span v-else-if="['createdAt', 'updatedAt'].includes(field)">
              <Timestamp :timestamp="data[field]" format="YYYY-MM-DD HH:mm:ss"></Timestamp>
            </span>
            <span v-else-if="['name'].includes(field)">
              <span class="cursor-pointer" @click="showRole(data)">{{ data.name }}</span>
            </span>
            <span v-else>{{ data[field] }}</span>
          </template>
        </Column>
        <Column style="width: 5rem; padding: 0" :hidden="!$hasSomePermissions('RoleUpdate', 'RoleDelete')">
          <template #body="{ data }">
            <Button v-tooltip.top="`Edit Role ${data.name}`" :hidden="!$hasPermission('RoleUpdate')" icon="pi pi-pencil" class="p-button-text p-button-sm p-button-rounded p-button-secondary" @click="editRole(data)" />
            <Button v-tooltip.top="`Remove Role ${data.name}`" :hidden="!$hasPermission('RoleDelete')" icon="pi pi-trash" class="p-button-text p-button-sm p-button-rounded p-button-danger" @click="confirmDeleteRole(data)" />
          </template>
        </Column>
      </DataTable>
    </Panel>
    <Dialog v-model:visible="roleDialog" :style="{ width: '60vw' }" header="Role Details" :modal="true" class="p-fluid roles-dialog" :dismissable-mask="false">
      <Message v-if="errors.length" severity="error">
        <div v-for="error in errors" :key="error">{{ error }}</div>
      </Message>
      <div>
        <dl>
          <dt>Name*</dt>
          <dd>
            <InputText id="name" v-model="role.name" autofocus :class="{ 'p-invalid': submitted && !role.name }" autocomplete="off" placeholder="Role Name" :disabled="isReadOnly" />
          </dd>
          <dt>Permissions*</dt>
          <dd>
            <div class="deltafi-fieldset">
              <div class="px-2">
                <PermissionCheckboxes v-model="role.permissions" :read-only="isReadOnly" />
              </div>
            </div>
          </dd>
        </dl>
      </div>
      <template v-if="!isReadOnly" #footer>
        <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="hideDialog" />
        <Button label="Save" icon="pi pi-check" class="p-button-text" @click="saveRole" />
      </template>
    </Dialog>
    <Dialog v-model:visible="deleteRoleDialog" :style="{ width: '450px' }" header="Confirm" :modal="true">
      <div class="confirmation-content">
        <i class="pi pi-exclamation-triangle mr-3" style="font-size: 2rem" />
        <span v-if="role">Are you sure you want to delete <b>{{ role.name }}</b>?</span>
      </div>
      <template #footer>
        <Button label="No" icon="pi pi-times" class="p-button-text" @click="deleteRoleDialog = false" />
        <Button label="Yes" icon="pi pi-check" class="p-button-text" @click="deleteRole" />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import PageHeader from "@/components/PageHeader.vue";
import PermissionCheckboxes from "@/components/PermissionCheckboxes.vue";
import PermissionPill from "@/components/PermissionPill.vue";
import Timestamp from "@/components/Timestamp.vue";
import usePermissions from "@/composables/usePermissions";
import useRoles from "@/composables/useRoles";
import { onMounted, ref, inject, watch } from "vue";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import Panel from "primevue/panel";

const submitted = ref(false);
const role = ref({ name: "", permissions: [] });
const roleDialog = ref(false);
const deleteRoleDialog = ref(false);
const isNew = ref(false);
const isReadOnly = ref(false);
const { data: roles, loaded, loading, fetch: fetchRoles, create, remove: removeRole, update: updateRole, errors } = useRoles();
const { appPermissionsByName } = usePermissions();
const uiConfig = inject("uiConfig");
const activeTabIndex = ref(0);
const columns = ref([
  { field: "name", header: "Name", sortable: true, class: "name-col" },
  { field: "permissions", header: "Permissions", sortable: false },
  { field: "createdAt", header: "Added", sortable: true, class: "timestamp-col" },
  { field: "updatedAt", header: "Updated", sortable: true, class: "timestamp-col" },
]);

const hideDialog = () => {
  role.value = { name: "", permissions: [] };
  roleDialog.value = false;
  isNew.value = false;
  deleteRoleDialog.value = false;
};

const editRole = (roleInfo) => {
  errors.value.splice(0, errors.value.length);
  role.value = { ...roleInfo };
  isNew.value = false;
  isReadOnly.value = false;
  roleDialog.value = true;
};

const showRole = (roleInfo) => {
  role.value = { ...roleInfo };
  isNew.value = false;
  isReadOnly.value = true;
  roleDialog.value = true;
};

const newRole = () => {
  errors.value.splice(0, errors.value.length);
  role.value = { name: "", permissions: [] };
  isNew.value = true;
  isReadOnly.value = false;
  submitted.value = false;
  roleDialog.value = true;
};

const saveRole = async () => {
  const { id, createdAt, updatedAt, ...saveParams } = role.value; // eslint-disable-line @typescript-eslint/no-unused-vars
  try {
    isNew.value ? await create(saveParams) : await updateRole(role.value.id, saveParams);
    await fetchRoles();
    hideDialog();
  } catch {
    // No op - keep dialog open
  }
  submitted.value = true;
};

const confirmDeleteRole = (roleInfo) => {
  role.value = roleInfo;
  deleteRoleDialog.value = true;
};

const deleteRole = async () => {
  await removeRole(role.value);
  deleteRoleDialog.value = false;
  fetchRoles();
};

onMounted(() => {
  fetchRoles();
});

watch(
  () => roleDialog.value,
  (newValue) => {
    if (newValue) {
      activeTabIndex.value = uiConfig.authMode == "cert" ? 1 : 0;
    }
  }
);
</script>

<style lang="scss">
@import "@/styles/pages/roles-page.scss";
</style>
