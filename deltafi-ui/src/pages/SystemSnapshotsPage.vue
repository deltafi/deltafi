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
  <div class="system-snapshots">
    <PageHeader heading="System Snapshots">
      <div class="d-flex mb-2">
        <Button label="Create Snapshot" icon="pi pi-plus" class="p-button-sm p-button-outlined mx-1" @click="onCreateSnapshot()" />
      </div>
    </PageHeader>
    <Panel header="Snapshots" class="table-panel system-snapshots-panel">
      <template #icons>
        <span class="p-input-icon-left">
          <i class="pi pi-search" />
          <InputText v-model="filters['global'].value" placeholder="Search" />
        </span>
      </template>
      <DataTable v-model:filters="filters" :value="snapshots" responsive-layout="scroll" class="p-datatable-sm p-datatable-gridlines" striped-rows>
        <template #empty>No snapshots to display.</template>
        <template #loading>Loading. Please wait...</template>
        <Column field="id" header="ID">
          <template #body="data">
            <a v-tooltip.top="`View Snapshot`" class="cursor-pointer monospace" style="color: black" @click="showSnapshot(data.data)">{{ data.data.id }}</a>
          </template>
        </Column>
        <Column field="reason" header="Reason" />
        <Column field="created" header="Date Created">
          <template #body="row">
            <Timestamp :timestamp="row.data.created" />
          </template>
        </Column>
        <Column :style="{ width: '4rem', padding: 0 }">
          <template #body="data">
            <Button v-tooltip.left="'Download Snapshot'" icon="fas fa-download fa-fw" class="p-button-text p-button-sm p-button-rounded p-button-secondary" @click="onDownload(data.data)" />
          </template>
        </Column>
      </DataTable>
    </Panel>
  </div>
  <Dialog v-model:visible="showSnapshotDialog" header="System Snapshot" :modal="true" :dismissable-mask="true" :draggable="false">
    <div v-if="snapshot != null">
      <HighlightedCode :code="JSON.stringify(snapshot, null, 2)" :style="{ width: '65vw' }" />
    </div>
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
</template>

<script setup>
import PageHeader from "@/components/PageHeader.vue";
import Button from "primevue/button";
import Panel from "primevue/panel";
import { onMounted, ref } from "vue";
import InputText from "primevue/inputtext";
import useSystemSnapshots from "@/composables/useSnapshots";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import Dialog from "primevue/dialog";
import HighlightedCode from "@/components/HighlightedCode.vue";
import Timestamp from "@/components/Timestamp.vue";
import useNotifications from "@/composables/useNotifications";
import { FilterMatchMode } from "primevue/api";

const { data: snapshots, fetch: getSystemSnapshots, create: createSystemSnapshot, mutationData: createResponse } = useSystemSnapshots();
const snapshot = ref(null);
const notify = useNotifications();
const reason = ref("");
const showSnapshotDialog = ref(false);
const showCreateSnapshotDialog = ref(false);
const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
});

onMounted(async () => {
  await getSystemSnapshots();
});

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
};
const onDownload = (snapshotData) => {
  download(JSON.stringify(snapshotData, null, 2), snapshotData.id, "application/json");
};
const showSnapshot = (snapshotData) => {
  snapshot.value = snapshotData;
  showSnapshotDialog.value = true;

};

const onCreateSnapshot = () => {
  reason.value = "";
  showCreateSnapshotDialog.value = true;
};

const confirmCreate = async () => {
  await createSystemSnapshot(reason.value);
  close();
  if (createResponse.value != null) {
    notify.success("Successfully Created Snapshot ", createResponse.value);
    await getSystemSnapshots();
  } else {
    notify.error("Failed To Create Snapshot");
  }
};
</script>

<style lang="scss">
.system-snapshots-panel {
  .p-panel-header {
    padding: 0 1.25rem;

    .p-panel-title {
      padding: 1rem 0;
    }
  }
}
</style>
