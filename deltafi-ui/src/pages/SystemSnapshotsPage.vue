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
  <div class="system-snapshots-page">
    <PageHeader heading="System Snapshots">
      <div class="d-flex">
        <FileUpload ref="fileUploader" v-has-permission:SnapshotCreate mode="basic" choose-label="Import Snapshot" class="p-button p-button-secondary p-button-sm p-button-outlined p-button-secondary-upload mx-1" :auto="true" :custom-upload="true" @uploader="onUpload" />
        <Button v-has-permission:SnapshotCreate label="Create Snapshot" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" @click="onCreateSnapshot()" />
      </div>
    </PageHeader>
    <Panel header="Snapshots" class="table-panel system-snapshots-panel">
      <template #icons>
        <div class="btn-group align-items-center">
          <Paginator template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :first="pageFirst" :rows="pageRows" :total-records="totalSnaps" :rows-per-page-options="[10, 20, 50, 100, 1000]" style="float: left" @page="onPage" />
          <IconField iconPosition="left">
            <InputIcon class="pi pi-search"> </InputIcon>
            <InputText v-model="filters['global'].value" class="p-inputtext-sm deltafi-input-field mx-1" placeholder="Search" />
          </IconField>
        </div>
      </template>
      <DataTable v-model:filters="filters" :value="snapshots" :paginator="true" :first="pageFirst" :rows="pageRows" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines" striped-rows :row-hover="true" :loading="loading" data-key="id">
        <template #empty> No snapshots to display. </template>
        <template #loading> Loading. Please wait... </template>
        <Column field="id" header="ID">
          <template #body="data">
            <a v-tooltip.top="`View Snapshot`" class="cursor-pointer monospace" style="color: black" @click="showSnapshot(data.data)">{{ data.data.id }}</a>
          </template>
        </Column>
        <Column field="reason" header="Reason" />
        <Column field="created" header="Date Created" class="date-column" :sortable="true">
          <template #body="row">
            <Timestamp :timestamp="row.data.created" />
          </template>
        </Column>
        <Column :style="{ width: '5rem', padding: 0 }">
          <template #body="data">
            <span class="btn-group">
              <Button v-tooltip.left="'Download Snapshot'" icon="fas fa-download fa-fw" class="p-button-text p-button-sm p-button-rounded p-button-secondary" @click="onDownload(data.data)" />
              <Button v-has-permission:SnapshotRevert v-tooltip.left="'Revert to Snapshot'" icon="fas fa-history fa-fw" class="p-button-text p-button-sm p-button-rounded p-button-secondary" @click="onRevertClick(data.data)" />
              <Button v-has-permission:SnapshotDelete v-tooltip.left="'Delete Snapshot'" icon="pi pi-trash" class="p-button-text p-button-sm p-button-rounded p-button-secondary" @click="onDeleteClick(data.data)" />
            </span>
          </template>
        </Column>
      </DataTable>
    </Panel>
  </div>
  <Dialog v-model:visible="showSnapshotDialog" header="System Snapshot" :modal="true" :dismissable-mask="true" :draggable="false">
    <div v-if="snapshot != null">
      <HighlightedCode :code="JSON.stringify(snapshot, null, 2)" :style="{ width: '65vw' }" />
    </div>
    <template #footer>
      <span class="btn-group">
        <Button label="Download" icon="fas fa-download fa-fw" class="p-button p-button-secondary p-button-outlined" @click="onDownload(snapshot)" />
        <Button v-has-permission:SnapshotRevert label="Revert to Snapshot" icon="fas fa-history fa-fw" class="p-button p-button-secondary p-button-outlined" @click="onRevertClick(snapshot)" />
        <Button v-has-permission:SnapshotDelete v-tooltip.left="'Delete Snapshot'" label="Delete Snapshot" icon="pi pi-trash" class="p-button p-button-secondary p-button-outlined" @click="onDeleteClick(snapshot)" />
      </span>
    </template>
  </Dialog>
  <Dialog v-model:visible="showCreateSnapshotDialog" header="Create Snapshot" :style="{ width: '25vw' }" :modal="true" :draggable="false" :dismissable-mask="true" @update:visible="close">
    <div class="p-fluid">
      <span class="p-float-label mt-3">
        <InputText id="reason" v-model="reason" type="text" autofocus />
        <label for="reason">Reason</label>
      </span>
    </div>
    <template #footer>
      <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="close" />
      <Button label="Create Snapshot" @click="confirmCreate()" />
    </template>
  </Dialog>
  <ConfirmDialog />
</template>

<script setup>
import FileUpload from "@/components/deprecatedPrimeVue/FileUpload.vue";
import HighlightedCode from "@/components/HighlightedCode.vue";
import PageHeader from "@/components/PageHeader.vue";
import Timestamp from "@/components/Timestamp.vue";
import useNotifications from "@/composables/useNotifications";
import useSystemSnapshots from "@/composables/useSnapshots";
import { onMounted, ref, computed } from "vue";

import Button from "primevue/button";
import Column from "primevue/column";
import ConfirmDialog from "primevue/confirmdialog";
import DataTable from "primevue/datatable";
import Dialog from "primevue/dialog";
import { FilterMatchMode } from "primevue/api";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";
import Paginator from "primevue/paginator";
import Panel from "primevue/panel";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const pageRows = ref(20);
const pageFirst = ref(0);
const { data: snapshots, fetch: getSystemSnapshots, create: createSystemSnapshot, mutationData: mutationResponse, revert: revertSnapshot, importSnapshot: importSnapshot, deleteSnapshot, loading } = useSystemSnapshots();
const snapshot = ref(null);
const notify = useNotifications();
const reason = ref("");
const fileUploader = ref();
const showSnapshotDialog = ref(false);
const showCreateSnapshotDialog = ref(false);
const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

onMounted(async () => {
  await getSystemSnapshots();
});

const totalSnaps = computed(() => (snapshots.value || []).length);

const download = (content, fileName, contentType) => {
  const a = document.createElement("a");
  const file = new Blob([content], { type: contentType });
  a.href = URL.createObjectURL(file);
  a.download = fileName;
  a.click();
};

const close = () => {
  showCreateSnapshotDialog.value = false;
  showSnapshotDialog.value = false;
  fileUploader.value.files = [];
};
const onDownload = (snapshotData) => {
  download(JSON.stringify(snapshotData, null, 2), snapshotData.id, "application/json");
};
const showSnapshot = (snapshotData) => {
  snapshot.value = snapshotData;
  showSnapshotDialog.value = true;
};

const onPage = (event) => {
  pageFirst.value = event.first;
  pageRows.value = event.rows;
};

const onRevert = async (id) => {
  await revertSnapshot(id);
  close();
  if (mutationResponse.value.success) {
    notify.success("Successfully Reverted to Snapshot ", id);
  } else {
    const uniqueErrors = [...new Set(mutationResponse.value.errors)];
    notify.error("Failed to Revert to Snapshot", uniqueErrors.join("<br />"));
  }
};

const onRevertClick = (snapshotData) => {
  confirm.require({
    message: `Are you sure you want to revert the system to this snapshot (${snapshotData.id})?`,
    header: "Confirm Revert",
    icon: "pi pi-exclamation-triangle",
    acceptLabel: "Revert",
    rejectLabel: "Cancel",
    accept: () => {
      onRevert(snapshotData.id);
    },
    reject: () => { },
  });
};

const onDeleteClick = (snapshotData) => {
  confirm.require({
    message: `Are you sure you want to delete this snapshot (${snapshotData.id})?`,
    header: "Confirm Delete",
    icon: "pi pi-exclamation-triangle",
    acceptLabel: "Delete",
    rejectLabel: "Cancel",
    accept: () => {
      onDelete(snapshotData.id);
    },
    reject: () => { },
  });
};

const onDelete = async (id) => {
  const deleteResponse = await deleteSnapshot(id);
  if (deleteResponse.success === true) {
    notify.success("Successfully Deleted Snapshot ", id);
    close();
    await getSystemSnapshots();
  } else {
    notify.error("Failed to Delete Snapshot", deleteResponse.errors[0]);
  }
};

const onImport = async (snapShotData) => {
  const clean = cleanUpSnapshot(snapshot);
  await importSnapshot(clean);
  if (mutationResponse.value.id === snapShotData.value.id) {
    notify.success("Successfully Imported Snapshot", mutationResponse.value.id);
    showSnapshot(mutationResponse.value);
  } else {
    close();
    notify.error("Error Importing Snapshot");
  }
  await getSystemSnapshots();
};

const cleanUpSnapshot = (snapShotData) => {
  const snap = JSON.parse(JSON.stringify(snapShotData.value));

  // support older snapshots by wrapping the snapshot data in the snapshot field if it doesn't exist
  if (!snap.snapshot) {
    const { id, created, reason, ...otherFields } = snap;
    return {
      id,
      created,
      reason,
      schemaVersion: 1,
      snapshot: otherFields,
    };
  }
  return snap;
};

const onUpload = async (event) => {
  const file = event.files[0];
  const reader = new FileReader();
  reader.readAsText(file);
  reader.onload = function () {
    const fileJSON = JSON.parse(reader.result);
    snapshot.value = fileJSON;
    onImport(snapshot);
  };
  fileUploader.value.files = [];
};

const onCreateSnapshot = () => {
  reason.value = "";
  showCreateSnapshotDialog.value = true;
};

const confirmCreate = async () => {
  await createSystemSnapshot(reason.value);
  close();
  if (mutationResponse.value != null) {
    notify.success("Successfully Created Snapshot", mutationResponse.value);
    await getSystemSnapshots();
  } else {
    notify.error("Failed To Create Snapshot");
  }
};
</script>

<style>
.system-snapshots-page {
  .system-snapshots-panel {
    .p-datatable .p-paginator {
      display: none;
    }

    .p-panel-header {
      padding: 0 1.25rem;

      .p-panel-title {
        padding: 1rem 0;
      }
    }

    .date-column {
      width: 15rem;
    }

    .p-paginator {
      background: inherit !important;
      color: inherit !important;
      border: none !important;
      padding: 0.2rem 0 !important;
      font-size: inherit !important;

      .p-paginator-current {
        background: unset;
        color: unset;
        border: unset;
      }
    }

    .p-input-icon-left {
      padding-top: 0.2rem;

      i {
        margin-top: -0.4rem;
      }

      .p-inputtext {
        padding-top: 6px;
        padding-bottom: 5px;
      }
    }
  }
}
</style>
