<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div class="artifact-registry-page">
    <PageHeader heading="Artifact Registry">
      <div class="d-flex align-items-center">
        <IconField iconPosition="left" class="mr-2">
          <InputIcon class="pi pi-search" />
          <InputText v-model="filterText" type="text" placeholder="Search" aria-label="Search artifacts by owner or name" class="p-inputtext deltafi-input-field" />
        </IconField>
        <div class="btn-toolbar">
          <ArtifactRegistryHeaderButtonGroup @reload="refresh" />
        </div>
      </div>
    </PageHeader>
    <Panel header="Artifacts" class="table-panel">
      <DataTable aria-label="Artifacts" :value="filteredArtifacts" :loading="loading" row-group-mode="subheader" group-rows-by="owner" sort-field="owner" :sort-order="1" :pt="{ rowGroupHeaderCell: { colspan: COLUMN_COUNT } }" class="p-datatable-sm p-datatable-gridlines">
        <template #empty> No artifacts found </template>
        <template #groupheader="{ data }">
          <span class="owner-group-header">
            <i class="pi pi-box" aria-hidden="true" />
            <span>{{ data.owner }}</span>
          </span>
        </template>
        <Column field="artifactName" header="Name">
          <template #body="{ data }">
            <div class="d-flex justify-content-between align-items-center">
              <router-link :to="{ path: `/admin/artifact-registry/${data.owner}/${data.artifactName}` }">
                {{ data.artifactName }}
              </router-link>
              <span class="d-flex align-items-center">
                <Button v-if="hasPermission('ArtifactRegistryWrite')" icon="pi pi-trash" severity="danger" text size="small" v-tooltip.left="'Delete Artifact'" :aria-label="`Delete artifact ${data.artifactName}`" @click="confirmDelete(data.owner, data.artifactName)" />
              </span>
            </div>
          </template>
        </Column>
        <Column field="activeVersion" header="Active Version">
          <template #body="{ data }">
            <span v-if="data.activeVersion">{{ data.activeVersion }}</span>
            <span v-else class="text-muted">None</span>
          </template>
        </Column>
        <Column field="totalVersions" header="Total Versions" />
      </DataTable>
    </Panel>
    <ConfirmDialog group="delete-artifact" />
  </div>
</template>

<script setup lang="ts">
import ArtifactRegistryHeaderButtonGroup from "@/components/artifactRegistry/ArtifactRegistryHeaderButtonGroup.vue";
import PageHeader from "@/components/PageHeader.vue";
import useArtifactRegistry from "@/composables/useArtifactRegistry";
import type { ArtifactSummary, ListArtifactsResponse } from "@/composables/useArtifactRegistry";
import { computed, inject, onMounted, ref } from "vue";

import Button from "primevue/button";
import Column from "primevue/column";
import ConfirmDialog from "primevue/confirmdialog";
import DataTable from "primevue/datatable";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";
import Panel from "primevue/panel";
import { useConfirm } from "primevue/useconfirm";

// Each artifact carries its owner so the table can group every owner together.
interface ArtifactRow extends ArtifactSummary {
  owner: string;
}

// Number of visible columns. PrimeVue spans the group header cell across colspan - 1 by
// default, leaving the last column uncovered; we override the colspan to span all of them.
const COLUMN_COUNT = 3;

const { listAllArtifacts, deleteArtifact, loading } = useArtifactRegistry();
const hasPermission = inject("hasPermission") as (permission: string) => boolean;
const confirm = useConfirm();

const artifacts = ref<ArtifactRow[]>([]);
const filterText = ref("");

// Filter on both owner (group) and artifact name so searching by either narrows the table.
// Matching an owner keeps its whole group; matching a name keeps that row under its group.
const filteredArtifacts = computed(() => {
  const query = filterText.value.trim().toLowerCase();
  if (!query) return artifacts.value;
  return artifacts.value.filter((artifact) => artifact.owner.toLowerCase().includes(query) || artifact.artifactName.toLowerCase().includes(query));
});

onMounted(() => {
  refresh();
});

// Load artifacts for every owner and flatten them into a single list tagged with the owner.
// PrimeVue subheader grouping requires the rows to be pre-sorted by the group field, so we
// sort by owner first and then by artifact name within each owner.
const refresh = async () => {
  const response = await listAllArtifacts();
  const groups = Array.isArray(response.value) ? (response.value as ListArtifactsResponse[]) : [];
  artifacts.value = groups
    .flatMap((group) => (group.artifacts || []).map((artifact) => ({ ...artifact, owner: group.pluginName })))
    .sort((a, b) => a.owner.localeCompare(b.owner) || a.artifactName.localeCompare(b.artifactName));
};

const confirmDelete = (owner: string, artifactName: string) => {
  confirm.require({
    position: "center",
    group: "delete-artifact",
    message: `Remove artifact "${artifactName}" and all its versions?`,
    acceptLabel: "Remove",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: async () => {
      await deleteArtifact(owner, artifactName);
      refresh();
    },
    reject: () => {},
  });
};
</script>

<style scoped>
/* Owner group headers read as full-width section dividers above their artifacts.
   The cell is forced to span every column via the pt colspan override on the table. */
:deep(tr.p-datatable-row-group-header > td) {
  background: var(--surface-hover) !important;
  border: none;
  border-left: 3px solid var(--df-accent);
  padding: 0.6rem 0.75rem;
}

.owner-group-header {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.95rem;
  font-weight: 700;
}

.owner-group-header .pi-box {
  color: var(--df-accent);
}
</style>
