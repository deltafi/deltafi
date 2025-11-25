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
<!-- ABOUTME: Fleet configuration page for comparing configs across member instances. -->
<!-- ABOUTME: Provides plugins list view and snapshot viewer with inline diff highlighting. -->

<template>
  <div class="leader-config-page">
    <PageHeader heading="Fleet Config">
      <Button label="Refresh" icon="pi pi-refresh" @click="refresh" :loading="loading || pluginsLoading" />
    </PageHeader>

    <!-- View Mode Selector -->
    <div class="view-mode-container mb-3">
      <SelectButton v-model="viewMode" :options="viewModeOptions" optionLabel="label" optionValue="value" :allow-empty="false" class="view-mode-selector" @change="onViewModeChange" />
    </div>

    <!-- Members View -->
    <div v-if="viewMode === 'members'" class="members-view">
      <div v-if="membersLoading && memberSummaries.length === 0" class="loading-message">
        <i class="pi pi-spin pi-spinner" /> Loading member status...
      </div>
      <div v-else-if="memberSummaries.length === 0" class="empty-message">
        <Message severity="info" :closable="false">No members found.</Message>
      </div>
      <DataTable v-else :value="memberSummaries" responsiveLayout="scroll" class="p-datatable-sm members-table">
        <Column field="name" header="Member" :sortable="true" style="min-width: 200px">
          <template #body="{ data }">
            <span class="member-name">{{ data.name }}</span>
          </template>
        </Column>
        <Column header="Reporting" style="width: 120px">
          <template #body="{ data }">
            <Tag v-if="data.reporting" severity="success" value="Yes" icon="pi pi-check" />
            <Tag v-else severity="danger" value="No" icon="pi pi-times" />
          </template>
        </Column>
        <Column header="Sync Status" style="min-width: 200px">
          <template #body="{ data }">
            <template v-if="!data.reporting">
              <span class="text-color-secondary">—</span>
            </template>
            <template v-else-if="data.loading">
              <i class="pi pi-spin pi-spinner mr-2" />
              <span class="text-color-secondary">Checking...</span>
            </template>
            <template v-else-if="data.diffCount === 0">
              <Tag severity="success" value="In sync" icon="pi pi-check" />
            </template>
            <template v-else-if="data.diffCount !== null">
              <Tag severity="warning" :value="`${data.diffCount} difference${data.diffCount === 1 ? '' : 's'}`" icon="pi pi-exclamation-triangle" />
            </template>
            <template v-else>
              <span class="text-color-secondary">Unable to compare</span>
            </template>
          </template>
        </Column>
        <Column header="" style="width: 100px; text-align: right">
          <template #body="{ data }">
            <Button v-if="data.reporting" label="Compare" size="small" text @click="compareMember(data.name)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <!-- Plugins View -->
    <div v-if="viewMode === 'plugins'" class="plugins-view">
      <div v-if="pluginsLoading" class="loading-message">
        <i class="pi pi-spin pi-spinner" /> Loading plugins...
      </div>
      <div v-else-if="uniquePlugins.length === 0 && membersNotReporting.length === 0" class="empty-message">
        <Message severity="info" :closable="false">No plugins found across members.</Message>
      </div>
      <template v-else>
        <!-- Members not reporting warning -->
        <Message v-if="membersNotReporting.length > 0" severity="warn" :closable="false" class="mb-3">
          <strong>{{ membersNotReporting.length }}</strong> member{{ membersNotReporting.length === 1 ? '' : 's' }} not reporting config data: {{ membersNotReporting.join(', ') }}
        </Message>
      <DataTable v-if="uniquePlugins.length > 0" :value="uniquePlugins" responsiveLayout="scroll" class="p-datatable-sm plugins-table" :expandedRows="expandedPluginRows" @update:expandedRows="expandedPluginRows = $event" dataKey="coordinates">
        <Column :expander="true" style="width: 3rem" />
        <Column field="displayName" header="Plugin" :sortable="true" style="min-width: 250px">
          <template #body="{ data }">
            <div class="plugin-name">{{ data.displayName }}</div>
            <div class="plugin-coords text-sm text-color-secondary">{{ data.coordinates }}</div>
          </template>
        </Column>
        <Column header="Leader Version" style="min-width: 120px">
          <template #body="{ data }">
            <Tag v-if="data.memberVersions['Leader']" severity="info" :value="data.memberVersions['Leader']" />
            <span v-else class="text-color-secondary">—</span>
          </template>
        </Column>
        <Column header="Members" style="min-width: 200px">
          <template #body="{ data }">
            <span>{{ getPluginMemberSummary(data) }}</span>
            <Tag v-if="getPluginMismatchCount(data) > 0" severity="warning" :value="`${getPluginMismatchCount(data)} mismatch`" class="ml-2" />
          </template>
        </Column>
        <template #expansion="{ data }">
          <div class="plugin-expansion p-3">
            <DataTable :value="getPluginMemberDetails(data)" class="p-datatable-sm">
              <Column field="member" header="Member" style="width: 200px" />
              <Column header="Version">
                <template #body="{ data: row }">
                  <Tag v-if="row.version" :severity="row.matchesLeader ? 'success' : 'warning'" :value="row.version" />
                  <span v-else class="text-color-secondary">Not installed</span>
                </template>
              </Column>
              <Column header="Status">
                <template #body="{ data: row }">
                  <span v-if="!row.version" class="text-color-secondary">—</span>
                  <span v-else-if="row.matchesLeader" class="text-success"><i class="pi pi-check" /> Matches Leader</span>
                  <span v-else class="text-warning"><i class="pi pi-exclamation-triangle" /> Different version</span>
                </template>
              </Column>
            </DataTable>
          </div>
        </template>
      </DataTable>
      </template>
    </div>

    <!-- Snapshot View (side-by-side comparison) -->
    <div v-if="viewMode === 'snapshot'" class="snapshot-view">
      <div class="snapshot-controls mb-3">
        <span class="control-label">Compare Leader with:</span>
        <Dropdown v-model="selectedMember" :options="memberDropdownOptions" optionLabel="name" optionValue="name" optionDisabled="disabled" placeholder="Select member..." class="w-15rem" @change="onMemberChange">
          <template #option="{ option }">
            <span :class="{ 'text-color-secondary': option.disabled }">
              {{ option.name }}
              <i v-if="option.disabled" class="pi pi-exclamation-triangle text-warning ml-1" />
              <span v-if="option.disabled" class="text-sm text-color-secondary ml-1">(not reporting)</span>
            </span>
          </template>
        </Dropdown>
        <span v-if="selectedMember && !snapshotLoading && memberSnapshot">
          <Tag v-if="totalDiffCount > 0" severity="warning" :value="`${totalDiffCount} difference${totalDiffCount === 1 ? '' : 's'}`" />
          <Tag v-else severity="success" value="In sync" icon="pi pi-check" />
        </span>
      </div>

      <div v-if="snapshotLoading" class="loading-message">
        <i class="pi pi-spin pi-spinner" /> Loading configuration...
      </div>
      <div v-else-if="!leaderSnapshot" class="empty-message">
        <Message severity="info" :closable="false">Loading leader configuration...</Message>
      </div>
      <div v-else class="snapshot-content">
        <!-- Side-by-side headers -->
        <div class="comparison-header mb-3">
          <div class="side-header leader-side">
            <span class="text-lg font-semibold">Leader</span>
            <Button label="JSON" icon="pi pi-code" size="small" text @click="showJson('leader')" />
          </div>
          <div class="side-header member-side">
            <span class="text-lg font-semibold">{{ selectedMember || 'Select Member' }}</span>
            <Button v-if="memberSnapshot" label="JSON" icon="pi pi-code" size="small" text @click="showJson('member')" />
          </div>
        </div>

        <Accordion :multiple="true" :activeIndex="[0]">
          <!-- Plugins -->
          <AccordionTab v-if="mergedPlugins.length > 0">
            <template #header>
              <span class="accordion-header-with-badge">
                <span>Plugins ({{ mergedPlugins.length }})</span>
                <Tag v-if="pluginDiffCount > 0" severity="warning" :value="pluginDiffCount" class="ml-2" />
              </span>
            </template>
            <div class="comparison-table">
              <table class="diff-table">
                <thead>
                  <tr>
                    <th class="plugin-col">Plugin</th>
                    <th class="version-col leader-col">Leader Version</th>
                    <th class="version-col member-col">{{ selectedMember || 'Member' }} Version</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="plugin in mergedPlugins" :key="plugin.coordinates">
                    <td class="plugin-col">
                      <div class="plugin-name">{{ plugin.displayName }}</div>
                      <div class="plugin-coords text-sm text-color-secondary">{{ plugin.coordinates }}</div>
                    </td>
                    <td class="version-col leader-col" :class="[plugin.diffClass, { 'diff-missing': !plugin.leaderVersion }]">
                      <Tag v-if="plugin.leaderVersion" :severity="plugin.hasDiff ? 'warning' : 'info'" :value="plugin.leaderVersion" />
                      <span v-else class="missing-indicator">—</span>
                    </td>
                    <td class="version-col member-col" :class="[plugin.diffClass, { 'diff-missing': selectedMember && !plugin.memberVersion }]">
                      <template v-if="selectedMember">
                        <Tag v-if="plugin.memberVersion" :severity="plugin.hasDiff ? 'warning' : 'info'" :value="plugin.memberVersion" />
                        <span v-else class="missing-indicator">—</span>
                      </template>
                      <span v-else class="text-color-secondary">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </AccordionTab>

          <!-- Flows comparison (REST, Timed, Transform, DataSinks combined) -->
          <AccordionTab v-if="mergedFlows.length > 0">
            <template #header>
              <span class="accordion-header-with-badge">
                <span>Flows ({{ mergedFlows.length }})</span>
                <Tag v-if="flowDiffCount > 0" severity="warning" :value="flowDiffCount" class="ml-2" />
              </span>
            </template>
            <div class="comparison-table">
              <table class="diff-table">
                <thead>
                  <tr>
                    <th class="flow-name-col">Flow</th>
                    <th class="flow-type-col">Type</th>
                    <th class="flow-status-col leader-col">Leader</th>
                    <th class="flow-status-col member-col">{{ selectedMember || 'Member' }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="flow in mergedFlows" :key="`${flow.type}-${flow.name}`">
                    <td class="flow-name-col">{{ flow.name }}</td>
                    <td class="flow-type-col">
                      <Tag :value="flow.typeLabel" :severity="getFlowTypeSeverity(flow.type)" />
                    </td>
                    <td class="flow-status-col leader-col" :class="[flow.diffClass, { 'diff-missing': !flow.leaderExists }]">
                      <template v-if="flow.leaderExists">
                        <Tag :severity="flow.leaderRunning ? 'success' : 'secondary'" :value="flow.leaderRunning ? 'Running' : 'Stopped'" class="mr-1" />
                        <Tag v-if="flow.leaderTestMode" severity="warning" value="Test" />
                      </template>
                      <span v-else class="missing-indicator">—</span>
                    </td>
                    <td class="flow-status-col member-col" :class="[flow.diffClass, { 'diff-missing': selectedMember && !flow.memberExists }]">
                      <template v-if="selectedMember">
                        <template v-if="flow.memberExists">
                          <Tag :severity="flow.memberRunning ? 'success' : 'secondary'" :value="flow.memberRunning ? 'Running' : 'Stopped'" class="mr-1" />
                          <Tag v-if="flow.memberTestMode" severity="warning" value="Test" />
                        </template>
                        <span v-else class="missing-indicator">—</span>
                      </template>
                      <span v-else class="text-color-secondary">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </AccordionTab>

          <!-- Properties -->
          <AccordionTab v-if="mergedProperties.length > 0">
            <template #header>
              <span class="accordion-header-with-badge">
                <span>Properties ({{ mergedProperties.length }})</span>
                <Tag v-if="propertyDiffCount > 0" severity="warning" :value="propertyDiffCount" class="ml-2" />
              </span>
            </template>
            <div class="comparison-table">
              <table class="diff-table">
                <thead>
                  <tr>
                    <th class="prop-key-col">Property</th>
                    <th class="prop-value-col leader-col">Leader Value</th>
                    <th class="prop-value-col member-col">{{ selectedMember || 'Member' }} Value</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="prop in mergedProperties" :key="prop.key">
                    <td class="prop-key-col font-mono text-sm">{{ prop.key }}</td>
                    <td class="prop-value-col leader-col" :class="[prop.diffClass, { 'diff-missing': prop.leaderValue === undefined }]">
                      <span v-if="prop.leaderValue !== undefined" class="prop-value">{{ prop.leaderValue }}</span>
                      <span v-else class="missing-indicator">—</span>
                    </td>
                    <td class="prop-value-col member-col" :class="[prop.diffClass, { 'diff-missing': selectedMember && prop.memberValue === undefined }]">
                      <template v-if="selectedMember">
                        <span v-if="prop.memberValue !== undefined" class="prop-value">{{ prop.memberValue }}</span>
                        <span v-else class="missing-indicator">—</span>
                      </template>
                      <span v-else class="text-color-secondary">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </AccordionTab>

          <!-- Links -->
          <AccordionTab v-if="mergedLinks.length > 0">
            <template #header>
              <span class="accordion-header-with-badge">
                <span>Links ({{ mergedLinks.length }})</span>
                <Tag v-if="linkDiffCount > 0" severity="warning" :value="linkDiffCount" class="ml-2" />
              </span>
            </template>
            <div class="comparison-table">
              <table class="diff-table">
                <thead>
                  <tr>
                    <th>Link Name</th>
                    <th class="presence-col leader-col">Leader</th>
                    <th class="presence-col member-col">{{ selectedMember || 'Member' }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in mergedLinks" :key="item.name">
                    <td>{{ item.name }}</td>
                    <td class="presence-col leader-col" :class="item.diffClass">
                      <i v-if="item.leaderExists" class="pi pi-check text-success" />
                      <span v-else class="missing-indicator">—</span>
                    </td>
                    <td class="presence-col member-col" :class="item.diffClass">
                      <template v-if="selectedMember">
                        <i v-if="item.memberExists" class="pi pi-check text-success" />
                        <span v-else class="missing-indicator">—</span>
                      </template>
                      <span v-else class="text-color-secondary">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </AccordionTab>

          <!-- Users -->
          <AccordionTab v-if="mergedUsers.length > 0">
            <template #header>
              <span class="accordion-header-with-badge">
                <span>Users ({{ mergedUsers.length }})</span>
                <Tag v-if="userDiffCount > 0" severity="warning" :value="userDiffCount" class="ml-2" />
              </span>
            </template>
            <div class="comparison-table">
              <table class="diff-table">
                <thead>
                  <tr>
                    <th>Username</th>
                    <th class="presence-col leader-col">Leader</th>
                    <th class="presence-col member-col">{{ selectedMember || 'Member' }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in mergedUsers" :key="item.name">
                    <td>{{ item.name }}</td>
                    <td class="presence-col leader-col" :class="item.diffClass">
                      <i v-if="item.leaderExists" class="pi pi-check text-success" />
                      <span v-else class="missing-indicator">—</span>
                    </td>
                    <td class="presence-col member-col" :class="item.diffClass">
                      <template v-if="selectedMember">
                        <i v-if="item.memberExists" class="pi pi-check text-success" />
                        <span v-else class="missing-indicator">—</span>
                      </template>
                      <span v-else class="text-color-secondary">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </AccordionTab>

          <!-- Roles -->
          <AccordionTab v-if="mergedRoles.length > 0">
            <template #header>
              <span class="accordion-header-with-badge">
                <span>Roles ({{ mergedRoles.length }})</span>
                <Tag v-if="roleDiffCount > 0" severity="warning" :value="roleDiffCount" class="ml-2" />
              </span>
            </template>
            <div class="comparison-table">
              <table class="diff-table">
                <thead>
                  <tr>
                    <th>Role Name</th>
                    <th class="presence-col leader-col">Leader</th>
                    <th class="presence-col member-col">{{ selectedMember || 'Member' }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in mergedRoles" :key="item.name">
                    <td>{{ item.name }}</td>
                    <td class="presence-col leader-col" :class="item.diffClass">
                      <i v-if="item.leaderExists" class="pi pi-check text-success" />
                      <span v-else class="missing-indicator">—</span>
                    </td>
                    <td class="presence-col member-col" :class="item.diffClass">
                      <template v-if="selectedMember">
                        <i v-if="item.memberExists" class="pi pi-check text-success" />
                        <span v-else class="missing-indicator">—</span>
                      </template>
                      <span v-else class="text-color-secondary">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </AccordionTab>

          <!-- Resume Policies -->
          <AccordionTab v-if="mergedResumePolicies.length > 0">
            <template #header>
              <span class="accordion-header-with-badge">
                <span>Resume Policies ({{ mergedResumePolicies.length }})</span>
                <Tag v-if="resumePolicyDiffCount > 0" severity="warning" :value="resumePolicyDiffCount" class="ml-2" />
              </span>
            </template>
            <div class="comparison-table">
              <table class="diff-table">
                <thead>
                  <tr>
                    <th>Policy Name</th>
                    <th class="presence-col leader-col">Leader</th>
                    <th class="presence-col member-col">{{ selectedMember || 'Member' }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in mergedResumePolicies" :key="item.name">
                    <td>{{ item.name }}</td>
                    <td class="presence-col leader-col" :class="item.diffClass">
                      <i v-if="item.leaderExists" class="pi pi-check text-success" />
                      <span v-else class="missing-indicator">—</span>
                    </td>
                    <td class="presence-col member-col" :class="item.diffClass">
                      <template v-if="selectedMember">
                        <i v-if="item.memberExists" class="pi pi-check text-success" />
                        <span v-else class="missing-indicator">—</span>
                      </template>
                      <span v-else class="text-color-secondary">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </AccordionTab>

          <!-- Delete Policies -->
          <AccordionTab v-if="mergedDeletePolicies.length > 0">
            <template #header>
              <span class="accordion-header-with-badge">
                <span>Delete Policies ({{ mergedDeletePolicies.length }})</span>
                <Tag v-if="deletePolicyDiffCount > 0" severity="warning" :value="deletePolicyDiffCount" class="ml-2" />
              </span>
            </template>
            <div class="comparison-table">
              <table class="diff-table">
                <thead>
                  <tr>
                    <th>Policy Name</th>
                    <th class="presence-col leader-col">Leader</th>
                    <th class="presence-col member-col">{{ selectedMember || 'Member' }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in mergedDeletePolicies" :key="item.name">
                    <td>{{ item.name }}</td>
                    <td class="presence-col leader-col" :class="item.diffClass">
                      <i v-if="item.leaderExists" class="pi pi-check text-success" />
                      <span v-else class="missing-indicator">—</span>
                    </td>
                    <td class="presence-col member-col" :class="item.diffClass">
                      <template v-if="selectedMember">
                        <i v-if="item.memberExists" class="pi pi-check text-success" />
                        <span v-else class="missing-indicator">—</span>
                      </template>
                      <span v-else class="text-color-secondary">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </AccordionTab>

          <!-- Plugin Variables -->
          <AccordionTab v-if="mergedPluginVariables.length > 0">
            <template #header>
              <span class="accordion-header-with-badge">
                <span>Plugin Variables ({{ mergedPluginVariables.length }})</span>
                <Tag v-if="pluginVariablesDiffCount > 0" severity="warning" :value="pluginVariablesDiffCount" class="ml-2" />
              </span>
            </template>
            <div class="comparison-table">
              <table class="diff-table">
                <thead>
                  <tr>
                    <th>Plugin</th>
                    <th>Variable</th>
                    <th class="prop-value-col leader-col">Leader Value</th>
                    <th class="prop-value-col member-col">{{ selectedMember || 'Member' }} Value</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="v in mergedPluginVariables" :key="`${v.pluginId}::${v.varName}`">
                    <td class="font-mono text-sm">{{ v.pluginId }}</td>
                    <td>{{ v.varName }}</td>
                    <td class="prop-value-col leader-col" :class="[v.diffClass, { 'diff-missing': v.leaderValue === undefined }]">
                      <span v-if="v.leaderValue !== undefined" class="prop-value">{{ v.leaderValue }}</span>
                      <span v-else class="missing-indicator">—</span>
                    </td>
                    <td class="prop-value-col member-col" :class="[v.diffClass, { 'diff-missing': selectedMember && v.memberValue === undefined }]">
                      <template v-if="selectedMember">
                        <span v-if="v.memberValue !== undefined" class="prop-value">{{ v.memberValue }}</span>
                        <span v-else class="missing-indicator">—</span>
                      </template>
                      <span v-else class="text-color-secondary">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </AccordionTab>
        </Accordion>
      </div>
    </div>

    <!-- JSON Dialog -->
    <Dialog v-model:visible="showJsonDialog" :header="jsonDialogTitle" modal :style="{ width: '80vw', height: '80vh' }">
      <pre class="json-content">{{ JSON.stringify(jsonDialogContent, null, 2) }}</pre>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
import useLeaderConfig, { type PluginSnapshot } from "@/composables/useLeaderConfig";
import PageHeader from "@/components/PageHeader.vue";
import Button from "primevue/button";
import SelectButton from "primevue/selectbutton";
import DataTable from "primevue/datatable";
import Column from "primevue/column";
import Tag from "primevue/tag";
import Message from "primevue/message";
import Dropdown from "primevue/dropdown";
import Accordion from "primevue/accordion";
import AccordionTab from "primevue/accordiontab";
import Dialog from "primevue/dialog";

interface FlowItem {
  name: string;
  running?: boolean;
  testMode?: boolean;
}

const {
  leaderSnapshot,
  memberSnapshot,
  membersNotReporting,
  memberSummaries,
  loading,
  pluginsLoading,
  snapshotLoading,
  membersLoading,
  uniquePlugins,
  memberNames,
  fetchAllPlugins,
  fetchComparisonSnapshots,
  fetchLeaderSnapshot,
  fetchAllMemberSummaries,
} = useLeaderConfig();

// Load persisted preferences from localStorage
const STORAGE_KEY = "fleetConfig";
const loadPreferences = () => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) return JSON.parse(stored);
  } catch { /* ignore */ }
  return {};
};
const savedPrefs = loadPreferences();

// View mode
const viewModeOptions = [
  { label: "Members", value: "members" },
  { label: "Plugins", value: "plugins" },
  { label: "Snapshot", value: "snapshot" },
];
const validViews = viewModeOptions.map((o) => o.value);
const viewMode = ref(validViews.includes(savedPrefs.viewMode) ? savedPrefs.viewMode : "members");

// Plugins view state
const expandedPluginRows = ref<Record<string, boolean>>({});

// Snapshot view state
const selectedMember = ref<string>("");

// Persist only the view mode (not selected member)
const savePreferences = () => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    viewMode: viewMode.value,
  }));
};
watch(viewMode, savePreferences);
const showJsonDialog = ref(false);
const jsonDialogSide = ref<"leader" | "member">("leader");

// Computed
const isNotReporting = (member: string) => membersNotReporting.value.includes(member);

const memberDropdownOptions = computed(() => {
  // Exclude Leader from dropdown since it's always shown on the left
  return memberNames.value
    .filter((name) => name !== "Leader")
    .map((name) => ({
      name,
      disabled: isNotReporting(name),
    }));
});

const jsonDialogContent = computed(() => {
  return jsonDialogSide.value === "leader" ? leaderSnapshot.value : memberSnapshot.value;
});

const jsonDialogTitle = computed(() => {
  return jsonDialogSide.value === "leader" ? "Leader Snapshot JSON" : `${selectedMember.value} Snapshot JSON`;
});

// Merged data computed properties for side-by-side comparison
const mergedPlugins = computed(() => {
  const pluginMap = new Map<string, { coordinates: string; displayName: string; leaderVersion?: string; memberVersion?: string }>();

  // Add leader plugins
  if (leaderSnapshot.value?.plugins) {
    for (const plugin of leaderSnapshot.value.plugins as PluginSnapshot[]) {
      const coords = `${plugin.pluginCoordinates.groupId}:${plugin.pluginCoordinates.artifactId}`;
      pluginMap.set(coords, {
        coordinates: coords,
        displayName: plugin.pluginCoordinates.artifactId,
        leaderVersion: plugin.pluginCoordinates.version,
      });
    }
  }

  // Add/merge member plugins
  if (memberSnapshot.value?.plugins) {
    for (const plugin of memberSnapshot.value.plugins as PluginSnapshot[]) {
      const coords = `${plugin.pluginCoordinates.groupId}:${plugin.pluginCoordinates.artifactId}`;
      const existing = pluginMap.get(coords);
      if (existing) {
        existing.memberVersion = plugin.pluginCoordinates.version;
      } else {
        pluginMap.set(coords, {
          coordinates: coords,
          displayName: plugin.pluginCoordinates.artifactId,
          memberVersion: plugin.pluginCoordinates.version,
        });
      }
    }
  }

  return Array.from(pluginMap.values())
    .map((p) => ({
      ...p,
      hasDiff: selectedMember.value && (p.leaderVersion !== p.memberVersion || !p.leaderVersion || !p.memberVersion),
      diffClass: getDiffClass(p.leaderVersion, p.memberVersion),
    }))
    .sort((a, b) => a.displayName.localeCompare(b.displayName));
});

const pluginDiffCount = computed(() => {
  if (!selectedMember.value || !memberSnapshot.value) return 0;
  return mergedPlugins.value.filter((p) => p.hasDiff).length;
});

const mergedFlows = computed(() => {
  const flowMap = new Map<
    string,
    {
      name: string;
      type: string;
      typeLabel: string;
      leaderExists: boolean;
      leaderRunning: boolean;
      leaderTestMode: boolean;
      memberExists: boolean;
      memberRunning: boolean;
      memberTestMode: boolean;
    }
  >();

  const flowTypes = [
    { key: "restDataSources", label: "REST" },
    { key: "timedDataSources", label: "Timed" },
    { key: "onErrorDataSources", label: "OnError" },
    { key: "transformFlows", label: "Transform" },
    { key: "dataSinks", label: "Data Sink" },
  ];

  // Add flows from leader
  for (const { key, label } of flowTypes) {
    const flows = (leaderSnapshot.value?.[key as keyof typeof leaderSnapshot.value] as FlowItem[]) || [];
    for (const flow of flows) {
      const mapKey = `${key}-${flow.name}`;
      flowMap.set(mapKey, {
        name: flow.name,
        type: key,
        typeLabel: label,
        leaderExists: true,
        leaderRunning: flow.running ?? false,
        leaderTestMode: flow.testMode ?? false,
        memberExists: false,
        memberRunning: false,
        memberTestMode: false,
      });
    }
  }

  // Merge member flows
  for (const { key, label } of flowTypes) {
    const flows = (memberSnapshot.value?.[key as keyof typeof memberSnapshot.value] as FlowItem[]) || [];
    for (const flow of flows) {
      const mapKey = `${key}-${flow.name}`;
      const existing = flowMap.get(mapKey);
      if (existing) {
        existing.memberExists = true;
        existing.memberRunning = flow.running ?? false;
        existing.memberTestMode = flow.testMode ?? false;
      } else {
        flowMap.set(mapKey, {
          name: flow.name,
          type: key,
          typeLabel: label,
          leaderExists: false,
          leaderRunning: false,
          leaderTestMode: false,
          memberExists: true,
          memberRunning: flow.running ?? false,
          memberTestMode: flow.testMode ?? false,
        });
      }
    }
  }

  return Array.from(flowMap.values())
    .map((f) => ({
      ...f,
      hasDiff:
        selectedMember.value &&
        (f.leaderExists !== f.memberExists || f.leaderRunning !== f.memberRunning || f.leaderTestMode !== f.memberTestMode),
      diffClass: getFlowDiffClass(f),
    }))
    .sort((a, b) => {
      if (a.type !== b.type) return a.type.localeCompare(b.type);
      return a.name.localeCompare(b.name);
    });
});

const flowDiffCount = computed(() => {
  if (!selectedMember.value || !memberSnapshot.value) return 0;
  return mergedFlows.value.filter((f) => f.hasDiff).length;
});

const mergedProperties = computed(() => {
  const propMap = new Map<string, { key: string; leaderValue?: string; memberValue?: string }>();

  // Add leader properties
  if (leaderSnapshot.value?.deltaFiProperties) {
    for (const prop of leaderSnapshot.value.deltaFiProperties) {
      propMap.set(prop.key, { key: prop.key, leaderValue: prop.value });
    }
  }

  // Merge member properties
  if (memberSnapshot.value?.deltaFiProperties) {
    for (const prop of memberSnapshot.value.deltaFiProperties) {
      const existing = propMap.get(prop.key);
      if (existing) {
        existing.memberValue = prop.value;
      } else {
        propMap.set(prop.key, { key: prop.key, memberValue: prop.value });
      }
    }
  }

  return Array.from(propMap.values())
    .map((p) => ({
      ...p,
      hasDiff: selectedMember.value && p.leaderValue !== p.memberValue,
      diffClass: getDiffClass(p.leaderValue, p.memberValue),
    }))
    .sort((a, b) => a.key.localeCompare(b.key));
});

const propertyDiffCount = computed(() => {
  if (!selectedMember.value || !memberSnapshot.value) return 0;
  return mergedProperties.value.filter((p) => p.hasDiff).length;
});

// Simple list comparisons for links, users, roles, resumePolicies
interface NamedItem { name: string; [key: string]: unknown }

const toArray = (val: unknown): NamedItem[] => {
  if (Array.isArray(val)) return val;
  return [];
};

const mergeNamedLists = (leaderList: unknown, memberList: unknown) => {
  const itemMap = new Map<string, { name: string; leaderExists: boolean; memberExists: boolean }>();
  for (const item of toArray(leaderList)) {
    if (item?.name) {
      itemMap.set(item.name, { name: item.name, leaderExists: true, memberExists: false });
    }
  }
  for (const item of toArray(memberList)) {
    if (item?.name) {
      const existing = itemMap.get(item.name);
      if (existing) {
        existing.memberExists = true;
      } else {
        itemMap.set(item.name, { name: item.name, leaderExists: false, memberExists: true });
      }
    }
  }
  return Array.from(itemMap.values())
    .map((item) => ({
      ...item,
      hasDiff: selectedMember.value && item.leaderExists !== item.memberExists,
      diffClass: item.leaderExists && !item.memberExists ? "diff-row-removed" : !item.leaderExists && item.memberExists ? "diff-row-added" : "",
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
};

const mergedLinks = computed(() => mergeNamedLists(leaderSnapshot.value?.links, memberSnapshot.value?.links));
const linkDiffCount = computed(() => (selectedMember.value && memberSnapshot.value ? mergedLinks.value.filter((l) => l.hasDiff).length : 0));

const mergedUsers = computed(() => mergeNamedLists(leaderSnapshot.value?.users, memberSnapshot.value?.users));
const userDiffCount = computed(() => (selectedMember.value && memberSnapshot.value ? mergedUsers.value.filter((u) => u.hasDiff).length : 0));

const mergedRoles = computed(() => mergeNamedLists(leaderSnapshot.value?.roles, memberSnapshot.value?.roles));
const roleDiffCount = computed(() => (selectedMember.value && memberSnapshot.value ? mergedRoles.value.filter((r) => r.hasDiff).length : 0));

const mergedResumePolicies = computed(() => mergeNamedLists(leaderSnapshot.value?.resumePolicies, memberSnapshot.value?.resumePolicies));
const resumePolicyDiffCount = computed(() => (selectedMember.value && memberSnapshot.value ? mergedResumePolicies.value.filter((r) => r.hasDiff).length : 0));

// deletePolicies is {timedPolicies: []} not a direct array
const mergedDeletePolicies = computed(() => {
  const leaderPolicies = (leaderSnapshot.value?.deletePolicies as { timedPolicies?: NamedItem[] })?.timedPolicies;
  const memberPolicies = (memberSnapshot.value?.deletePolicies as { timedPolicies?: NamedItem[] })?.timedPolicies;
  return mergeNamedLists(leaderPolicies, memberPolicies);
});
const deletePolicyDiffCount = computed(() => (selectedMember.value && memberSnapshot.value ? mergedDeletePolicies.value.filter((d) => d.hasDiff).length : 0));

// Plugin variables comparison
interface PluginVariablesItem { sourcePlugin: { artifactId: string }; variables?: { name: string; value?: unknown }[] }

const mergedPluginVariables = computed(() => {
  const varMap = new Map<string, { pluginId: string; varName: string; leaderValue?: string; memberValue?: string }>();

  const extractVars = (list: PluginVariablesItem[] | undefined, side: "leader" | "member") => {
    for (const pv of list || []) {
      const pluginId = pv.sourcePlugin?.artifactId || "unknown";
      for (const v of pv.variables || []) {
        const key = `${pluginId}::${v.name}`;
        const existing = varMap.get(key) || { pluginId, varName: v.name };
        if (side === "leader") {
          existing.leaderValue = v.value !== undefined ? String(v.value) : undefined;
        } else {
          existing.memberValue = v.value !== undefined ? String(v.value) : undefined;
        }
        varMap.set(key, existing);
      }
    }
  };

  extractVars(leaderSnapshot.value?.pluginVariables as PluginVariablesItem[], "leader");
  extractVars(memberSnapshot.value?.pluginVariables as PluginVariablesItem[], "member");

  return Array.from(varMap.values())
    .map((v) => ({
      ...v,
      hasDiff: selectedMember.value && v.leaderValue !== v.memberValue,
      diffClass: getDiffClass(v.leaderValue, v.memberValue),
    }))
    .sort((a, b) => a.pluginId.localeCompare(b.pluginId) || a.varName.localeCompare(b.varName));
});
const pluginVariablesDiffCount = computed(() => (selectedMember.value && memberSnapshot.value ? mergedPluginVariables.value.filter((v) => v.hasDiff).length : 0));

const totalDiffCount = computed(() => {
  return pluginDiffCount.value + flowDiffCount.value + propertyDiffCount.value + linkDiffCount.value + userDiffCount.value + roleDiffCount.value + resumePolicyDiffCount.value + deletePolicyDiffCount.value + pluginVariablesDiffCount.value;
});

// Diff class helpers
const getDiffClass = (leaderVal?: string, memberVal?: string): string => {
  if (!selectedMember.value) return "";
  if (!leaderVal && memberVal) return "diff-row-added";
  if (leaderVal && !memberVal) return "diff-row-removed";
  if (leaderVal !== memberVal) return "diff-row-modified";
  return "";
};

const getFlowDiffClass = (flow: { leaderExists: boolean; memberExists: boolean; leaderRunning: boolean; memberRunning: boolean; leaderTestMode: boolean; memberTestMode: boolean }): string => {
  if (!selectedMember.value) return "";
  if (!flow.leaderExists && flow.memberExists) return "diff-row-added";
  if (flow.leaderExists && !flow.memberExists) return "diff-row-removed";
  if (flow.leaderRunning !== flow.memberRunning || flow.leaderTestMode !== flow.memberTestMode) return "diff-row-modified";
  return "";
};

const getFlowTypeSeverity = (type: string): string => {
  switch (type) {
    case "restDataSources":
      return "info";
    case "timedDataSources":
      return "secondary";
    case "transformFlows":
      return "success";
    case "dataSinks":
      return "warning";
    default:
      return "info";
  }
};

// Actions
const refresh = async () => {
  if (viewMode.value === "members") {
    await fetchAllMemberSummaries();
  } else if (viewMode.value === "plugins") {
    await fetchAllPlugins();
  } else if (viewMode.value === "snapshot") {
    await fetchLeaderSnapshot();
    if (selectedMember.value) {
      await fetchComparisonSnapshots(selectedMember.value);
    }
  }
};

const onViewModeChange = async () => {
  if (viewMode.value === "members" && memberSummaries.value.length === 0) {
    await fetchAllMemberSummaries();
  } else if (viewMode.value === "plugins" && uniquePlugins.value.length === 0) {
    await fetchAllPlugins();
  } else if (viewMode.value === "snapshot") {
    // Need plugins data to populate member dropdown
    if (memberNames.value.length === 0) {
      await fetchAllPlugins();
    }
    if (!leaderSnapshot.value) {
      await fetchLeaderSnapshot();
    }
  }
};

const onMemberChange = async () => {
  if (selectedMember.value && !isNotReporting(selectedMember.value)) {
    await fetchComparisonSnapshots(selectedMember.value);
  }
};

const compareMember = async (memberName: string) => {
  selectedMember.value = memberName;
  viewMode.value = "snapshot";
  await fetchComparisonSnapshots(memberName);
};

const showJson = (side: "leader" | "member") => {
  jsonDialogSide.value = side;
  showJsonDialog.value = true;
};

// Plugin helpers (for plugins view table) - exclude non-reporting members
const reportingMembers = computed(() => memberNames.value.filter((m) => m !== "Leader" && !membersNotReporting.value.includes(m)));

const getPluginMemberSummary = (data: { memberVersions: Record<string, string> }): string => {
  const membersWithPlugin = Object.keys(data.memberVersions).filter((m) => m !== "Leader" && !membersNotReporting.value.includes(m));
  return `${membersWithPlugin.length}/${reportingMembers.value.length} members`;
};

const getPluginMismatchCount = (data: { memberVersions: Record<string, string> }): number => {
  const leaderVersion = data.memberVersions["Leader"];
  if (!leaderVersion) return 0;
  return Object.entries(data.memberVersions).filter(([member, version]) => member !== "Leader" && !membersNotReporting.value.includes(member) && version !== leaderVersion).length;
};

const getPluginMemberDetails = (data: { memberVersions: Record<string, string> }) => {
  const leaderVersion = data.memberVersions["Leader"];
  return reportingMembers.value.map((member) => ({
    member,
    version: data.memberVersions[member] || null,
    matchesLeader: data.memberVersions[member] === leaderVersion,
  }));
};

// Lifecycle
onMounted(async () => {
  // Load data based on initial view from URL
  if (viewMode.value === "members") {
    await fetchAllMemberSummaries();
  } else if (viewMode.value === "plugins") {
    await fetchAllPlugins();
  } else if (viewMode.value === "snapshot") {
    // Need plugins data to populate member dropdown
    await fetchAllPlugins();
    await fetchLeaderSnapshot();
  }
});
</script>

<style scoped>
.view-mode-container {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.view-mode-selector :deep(.p-button.p-highlight) {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: var(--primary-color-text);
}

.loading-message,
.empty-message {
  text-align: center;
  padding: 2rem;
}

.loading-message i {
  margin-right: 0.5rem;
}

/* Plugins View */
.plugins-table .plugin-name {
  font-weight: 600;
}

.plugins-table .plugin-coords {
  font-family: monospace;
  font-size: 0.8rem;
}

.plugin-expansion {
  background: var(--surface-50);
}

.text-success {
  color: var(--green-500);
}

.text-warning {
  color: var(--orange-500);
}

/* Members View */
.members-table .member-name {
  font-weight: 600;
}

/* Snapshot View */
.snapshot-controls {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.control-label {
  font-weight: 500;
}

/* Side-by-side comparison header */
.comparison-header {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.side-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background: var(--surface-100);
  border-radius: 6px;
}

.side-header.leader-side {
  border-left: 3px solid var(--primary-color);
}

.side-header.member-side {
  border-left: 3px solid var(--cyan-500);
}

.accordion-header-with-badge {
  display: flex;
  align-items: center;
}

/* Comparison tables */
.comparison-table {
  overflow-x: auto;
}

.diff-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.diff-table th,
.diff-table td {
  padding: 0.5rem 0.75rem;
  text-align: left;
  border-bottom: 1px solid var(--surface-200);
}

.diff-table th {
  background: var(--surface-100);
  font-weight: 600;
  color: var(--text-color-secondary);
  text-transform: uppercase;
  font-size: 0.75rem;
  letter-spacing: 0.05em;
}

.diff-table tbody tr:hover {
  background: var(--surface-50);
}

/* Column widths */
.plugin-col {
  width: 40%;
}

.version-col {
  width: 30%;
}

.flow-name-col {
  width: 35%;
}

.flow-type-col {
  width: 15%;
}

.flow-status-col {
  width: 25%;
}

.prop-key-col {
  width: 40%;
}

.prop-value-col {
  width: 30%;
  word-break: break-word;
}

.presence-col {
  width: 100px;
  text-align: center;
}

/* Column highlighting */
.leader-col {
  background: rgba(var(--primary-color-rgb, 59, 130, 246), 0.03);
}

.member-col {
  background: rgba(6, 182, 212, 0.03);
}

/* Diff row highlighting */
.diff-row-added {
  background-color: rgba(34, 197, 94, 0.1) !important;
}

.diff-row-removed {
  background-color: rgba(239, 68, 68, 0.1) !important;
}

.diff-row-modified {
  background-color: rgba(245, 158, 11, 0.1) !important;
}

/* Missing value indicator */
.diff-missing {
  color: var(--text-color-secondary);
}

.missing-indicator {
  color: var(--text-color-secondary);
  font-style: italic;
}

/* Plugin info in diff table */
.diff-table .plugin-name {
  font-weight: 600;
}

.diff-table .plugin-coords {
  font-family: monospace;
}

.prop-value {
  font-family: monospace;
  font-size: 0.8rem;
}

.json-content {
  font-family: monospace;
  font-size: 0.85rem;
  background: var(--surface-50);
  padding: 1rem;
  border-radius: 4px;
  overflow: auto;
  max-height: calc(80vh - 100px);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
