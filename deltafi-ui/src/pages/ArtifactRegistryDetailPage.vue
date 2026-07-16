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
  <div class="artifact-registry-detail-page">
    <PageHeader>
      <template #header>
        <div>
          <h2 class="mb-0">
            <router-link to="/admin/artifact-registry" class="text-decoration-none">Artifact Registry</router-link>
            <span class="text-muted"> / </span>
            {{ artifactName }}
          </h2>
          <small class="text-muted">Owner: {{ owner }}</small>
        </div>
      </template>
      <div class="d-flex align-items-center">
        <IconField iconPosition="left" class="mr-2">
          <InputIcon class="pi pi-search" />
          <InputText v-model="filterText" type="text" placeholder="Search" aria-label="Search versions by version, description, labels, or alias" class="p-inputtext deltafi-input-field" />
        </IconField>
        <div class="btn-toolbar">
          <ArtifactRegistryHeaderButtonGroup :owner="owner" :artifact-name="artifactName" @reload="refresh" />
        </div>
      </div>
    </PageHeader>

    <Panel header="Versions" class="mb-3 versions-panel" @contextmenu="onPanelRightClick">
      <ContextMenu ref="contextMenu" :model="menuItems" />
      <template v-if="hasPermission('ArtifactRegistryWrite')" #icons>
        <Button severity="secondary" variant="text" class="p-panel-header-icon" aria-label="Version actions menu" aria-haspopup="true" @click="toggleMenu">
          <span class="fas fa-bars" />
        </Button>
        <Menu ref="menu" :model="menuItems" :popup="true" />
      </template>
      <DataTable v-model:selection="selectedVersions" aria-label="Versions" data-key="version" :selection-mode="hasPermission('ArtifactRegistryWrite') ? 'multiple' : undefined" :value="filteredVersions" :loading="loading" striped-rows scrollable scroll-height="52vh" :meta-key-selection="false" class="p-datatable-sm p-datatable-gridlines" @row-contextmenu="onRowContextMenu">
        <template #empty> No versions found </template>
        <Column field="version" header="Version" :sortable="true">
          <template #body="{ data }">
            <div class="d-flex justify-content-between align-items-center">
              <span>
                <span>{{ data.version }}</span>
                <Tag v-if="data.version === activeVersion" value="Active" severity="success" class="ml-2" />
                <Tag v-for="alias in aliasesForVersion(data.version)" :key="alias" :value="alias" severity="info" class="ml-1" />
              </span>
              <ArtifactVersionColumnButtonGroup :owner="owner" :artifact-name="artifactName" :version="data.version" :active="data.version === activeVersion" @refresh="refresh" @set-alias="openSetAliasDialog" />
            </div>
          </template>
        </Column>
        <Column field="description" header="Description">
          <template #body="{ data }">
            <span v-if="data.description">{{ data.description }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </Column>
        <Column field="size" header="Size" :sortable="true">
          <template #body="{ data }">
            <FormattedBytes :bytes="data.size" />
          </template>
        </Column>
        <Column field="sha256" header="SHA-256">
          <template #body="{ data }">
            <span v-tooltip.top="data.sha256" class="sha-truncated">{{ data.sha256?.substring(0, 12) }}...</span>
          </template>
        </Column>
        <Column field="createdAt" header="Created" :sortable="true" :style="{ width: '15rem', whiteSpace: 'nowrap' }" header-style="width: 15rem">
          <template #body="{ data }">
            <Timestamp :timestamp="data.createdAt" />
          </template>
        </Column>
        <Column field="labels" header="Labels">
          <template #body="{ data }">
            <Tag v-for="(value, key) in data.labels" :key="key" :value="`${key}=${value}`" severity="secondary" class="mr-1" />
            <span v-if="!data.labels || Object.keys(data.labels).length === 0" class="text-muted">-</span>
          </template>
        </Column>
      </DataTable>
    </Panel>

    <Panel v-if="hasPermission('ArtifactRegistryWrite')" header="Purge Configuration" class="mb-3">
      <div class="d-flex align-items-end gap-3 mb-3">
        <div>
          <label for="keepLatest" class="font-weight-bold mb-1 d-block">
            Max Versions to Keep
            <i class="pi pi-info-circle text-muted ml-1" v-tooltip.top="keepLatestHelp" :aria-label="keepLatestHelp" />
          </label>
          <InputNumber id="keepLatest" v-model="purgeConfig.keepLatest" :min="0" :input-style="{ width: '9rem' }" />
        </div>
        <div>
          <label for="maxAgeMinutes" class="font-weight-bold mb-1 d-block">
            Max Age (minutes)
            <i class="pi pi-info-circle text-muted ml-1" v-tooltip.top="maxAgeHelp" :aria-label="maxAgeHelp" />
          </label>
          <InputNumber id="maxAgeMinutes" v-model="purgeConfig.maxAgeMinutes" :min="0" :input-style="{ width: '9rem' }" />
        </div>
        <Button label="Save Configuration" icon="pi pi-save" aria-label="Save purge configuration" @click="savePurgeConfig" />
        <Button label="Purge Now" icon="pi pi-trash" severity="danger" outlined aria-label="Run purge now" @click="runPurge" />
      </div>
    </Panel>

    <DialogTemplate ref="setAliasDialog" component-name="artifactRegistry/ArtifactSetAliasDialog" header="Set Alias" dialog-width="30vw" :owner="owner" :artifact-name="artifactName" :versions="versionStrings" :initial-version="aliasTargetVersion" @refresh-page="refresh" />
    <ConfirmDialog group="delete-versions" />
  </div>
</template>

<script setup lang="ts">
import ArtifactRegistryHeaderButtonGroup from "@/components/artifactRegistry/ArtifactRegistryHeaderButtonGroup.vue";
import ArtifactVersionColumnButtonGroup from "@/components/artifactRegistry/ArtifactVersionColumnButtonGroup.vue";
import DialogTemplate from "@/components/DialogTemplate.vue";
import FormattedBytes from "@/components/FormattedBytes.vue";
import PageHeader from "@/components/PageHeader.vue";
import Timestamp from "@/components/Timestamp.vue";
import useArtifactRegistry from "@/composables/useArtifactRegistry";
import type { ArtifactVersion, PurgeConfig } from "@/composables/useArtifactRegistry";
import useNotifications from "@/composables/useNotifications";
import { computed, inject, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";

import Button from "primevue/button";
import Column from "primevue/column";
import ConfirmDialog from "primevue/confirmdialog";
import ContextMenu from "primevue/contextmenu";
import DataTable from "primevue/datatable";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputNumber from "primevue/inputnumber";
import InputText from "primevue/inputtext";
import Menu from "primevue/menu";
import Panel from "primevue/panel";
import Tag from "primevue/tag";
import { useConfirm } from "primevue/useconfirm";

const route = useRoute();
const { listVersions, getPurgeConfig, updatePurgeConfig, purge, deleteVersions, loading } = useArtifactRegistry();
const hasPermission = inject("hasPermission") as (permission: string) => boolean;
const confirm = useConfirm();
const notify = useNotifications();

const owner = route.params.owner as string;
const artifactName = route.params.artifactName as string;

const versions = ref<ArtifactVersion[]>([]);
const activeVersion = ref<string | undefined>(undefined);
// Reverse alias map from the API: version string -> alias names pointing at it.
const aliasMap = ref<Record<string, string[]>>({});
const purgeConfig = reactive<PurgeConfig>({
  keepLatest: 0,
  maxAgeMinutes: 0,
});
const setAliasDialog = ref<InstanceType<typeof DialogTemplate> | null>(null);
const aliasTargetVersion = ref("");

const versionStrings = computed(() => versions.value.map((v) => v.version));

// Search across version, description, labels (key=value), and aliases so any of them narrows the table.
const filterText = ref("");
const filteredVersions = computed(() => {
  const query = filterText.value.trim().toLowerCase();
  if (!query) return versions.value;
  return versions.value.filter((v) => {
    const labelText = v.labels ? Object.entries(v.labels).map(([key, value]) => `${key}=${value}`).join(" ") : "";
    const aliasText = (aliasMap.value[v.version] || []).join(" ");
    return v.version.toLowerCase().includes(query) || (v.description ?? "").toLowerCase().includes(query) || labelText.toLowerCase().includes(query) || aliasText.toLowerCase().includes(query);
  });
});

// ── Bulk selection / delete ────────────────────────────────────────────────
const selectedVersions = ref<ArtifactVersion[]>([]);
const menu = ref();
const contextMenu = ref();

const menuItems = ref([
  {
    label: "Clear Selected",
    icon: "fas fa-times fa-fw",
    command: () => {
      clearSelected();
    },
    disabled: computed(() => selectedVersions.value.length === 0),
  },
  {
    label: "Select All Visible",
    icon: "fas fa-check-double fa-fw",
    command: () => {
      selectedVersions.value = [...filteredVersions.value];
    },
  },
  {
    separator: true,
    visible: computed(() => hasPermission("ArtifactRegistryWrite")),
  },
  {
    label: "Delete Selected",
    icon: "fas fa-trash fa-fw",
    command: () => {
      confirmDeleteSelected();
    },
    visible: computed(() => hasPermission("ArtifactRegistryWrite")),
    disabled: computed(() => selectedVersions.value.length === 0),
  },
]);

const toggleMenu = (event: Event) => {
  menu.value.toggle(event);
};

const onPanelRightClick = (event: Event) => {
  contextMenu.value.show(event);
};

// Right-clicking a row with nothing selected acts on just that row, matching the errors page.
const onRowContextMenu = (event: { data: ArtifactVersion }) => {
  if (selectedVersions.value.length <= 0) {
    selectedVersions.value = [event.data];
  }
};

const clearSelected = () => {
  selectedVersions.value = [];
};

const confirmDeleteSelected = () => {
  // Only act on versions that are both selected AND visible under the current filter — a
  // selection made before filtering must not delete rows the filter is currently hiding.
  const visible = new Set(filteredVersions.value.map((v) => v.version));
  const selected = selectedVersions.value.map((v) => v.version).filter((version) => visible.has(version));
  if (selected.length === 0) {
    notify.warn("Nothing to Delete", "No selected versions are visible in the current filter.");
    return;
  }
  // The active version can't be deleted (backend 409); drop it from the batch and say so.
  const deletable = selected.filter((v) => v !== activeVersion.value);
  if (deletable.length === 0) {
    notify.warn("Cannot Delete", "The active version cannot be deleted. Set another version as active first.");
    return;
  }
  const activeExcluded = deletable.length !== selected.length;
  const count = deletable.length;
  confirm.require({
    position: "center",
    group: "delete-versions",
    message: `Delete ${count} selected version${count === 1 ? "" : "s"} of ${artifactName}?${activeExcluded ? " The active version was excluded and will not be deleted." : ""}`,
    acceptLabel: "Delete",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: async () => {
      await deleteVersions(owner, artifactName, deletable);
      clearSelected();
      refresh();
    },
    reject: () => {},
  });
};

// A version is purged only when it is BOTH beyond "Max Versions to Keep" AND older than
// "Max Age"; the active version is never purged. Spell that out since the AND is non-obvious.
const keepLatestHelp = "The number of most recent versions to keep. A version is purged only if it is both beyond this count and older than Max Age. The active version is never purged.";
const maxAgeHelp = "Only versions older than this are eligible to be purged. A version is purged only if it is both older than this age and beyond Max Versions to Keep. The active version is never purged.";

// "active" is rendered separately as its own tag, so exclude it from the alias list here.
const aliasesForVersion = (version: string) => (aliasMap.value[version] || []).filter((alias) => alias !== "active");

onMounted(() => {
  refresh();
  fetchPurgeConfig();
});

const refresh = async () => {
  const response = await listVersions(owner, artifactName);
  if (response.value) {
    versions.value = response.value.versions || [];
    activeVersion.value = response.value.activeVersion;
    aliasMap.value = response.value.aliases || {};
  }
};

const fetchPurgeConfig = async () => {
  try {
    const response = await getPurgeConfig(owner, artifactName);
    if (response.value) {
      purgeConfig.keepLatest = response.value.keepLatest ?? 0;
      purgeConfig.maxAgeMinutes = response.value.maxAgeMinutes ?? 0;
    }
  } catch {
    // May not exist yet, use defaults
  }
};

const savePurgeConfig = async () => {
  await updatePurgeConfig(owner, artifactName, {
    keepLatest: purgeConfig.keepLatest,
    maxAgeMinutes: purgeConfig.maxAgeMinutes,
  });
};

const runPurge = async () => {
  await purge(owner, artifactName);
  refresh();
};

const openSetAliasDialog = (version: string) => {
  aliasTargetVersion.value = version;
  setAliasDialog.value?.showDialog();
};
</script>

<style scoped>
/* Let the versions table fill the panel edge-to-edge instead of sitting inside padding. */
.versions-panel :deep(.p-panel-content) {
  padding: 0;
}

/* The header-actions button would otherwise stretch the header taller than other panels.
   Use the app's standard panel-header sizing (see .table-panel usages) so the header lands
   at the conventional ~43px height regardless of the header icons. */
.versions-panel :deep(.p-panel-header) {
  padding: 0 1.25rem;
}
.versions-panel :deep(.p-panel-title) {
  padding: 1rem 0;
}

.sha-truncated {
  font-family: monospace;
  cursor: help;
}

.gap-3 {
  gap: 1rem;
}
</style>
