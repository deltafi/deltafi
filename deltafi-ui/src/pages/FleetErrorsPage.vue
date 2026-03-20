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

<!-- ABOUTME: Fleet-wide error management page for viewing and acting on errors across member systems. -->
<!-- ABOUTME: Shows error counts by member, data source, and error message with bulk action capabilities. -->

<template>
  <div class="fleet-errors-page">
    <FilterSidebar
      v-model="sidebarOpen"
      title="Error Filters"
      tab-label="Filters"
      width="350px"
      :has-active-filters="hasActiveFilters"
      @clear="clearFilters"
    >
      <div class="filter-item">
        <label>Members</label>
        <MultiSelect v-model="memberNamesFilter" :options="availableMemberNames" placeholder="All Members" :max-selected-labels="2" class="w-full" />
      </div>
      <div class="filter-item">
        <label>Tags</label>
        <MultiSelect v-model="memberTagsFilter" :options="availableTags" placeholder="All Tags" :max-selected-labels="2" class="w-full" />
      </div>
      <div class="filter-item">
        <label>Data Sources</label>
        <MultiSelect v-model="dataSourcesFilter" :options="availableDataSources" placeholder="All Data Sources" :max-selected-labels="2" class="w-full" />
      </div>
      <div class="filter-item">
        <label>Error Message</label>
        <InputText v-model="errorCauseFilter" placeholder="Contains text..." class="w-full" />
      </div>
      <div class="filter-item">
        <label>Acknowledgement</label>
        <Select v-model="acknowledgedFilter" :options="ackOptions" option-label="name" option-value="value" class="w-full" />
      </div>
    </FilterSidebar>
    <div class="main-content">
      <PageHeader heading="Fleet Errors">
        <SearchDateToolbar
          v-model:date-type="queryDateType"
          :date-type-options="['Modified']"
          :start-time-date="startTimeDate"
          :end-time-date="endTimeDate"
          :reset-default="resetDefaultTimeDate"
          :loading="loading || summaryLoading"
          @date-change="onDateChange"
          @refresh="refreshAll"
        />
      </PageHeader>

    <!-- Summary Stats Bar -->
    <div class="summary-stats-bar mb-3">
      <div class="stat-item">
        <div class="stat-value-row">
          <Skeleton v-if="loading" width="3rem" height="1.5rem" />
          <span v-else class="stat-value">{{ formatCount(totalFilteredErrors) }}</span>
        </div>
        <div class="stat-label-row">
          <i class="pi pi-exclamation-circle stat-icon error-icon" />
          <span class="stat-label">Total Errors</span>
        </div>
      </div>
      <div class="stat-item">
        <div class="stat-value-row">
          <Skeleton v-if="loading" width="3rem" height="1.5rem" />
          <span v-else class="stat-value">{{ connectedMemberCount }}/{{ totalMemberCount }}</span>
        </div>
        <div class="stat-label-row">
          <i class="fas fa-sitemap stat-icon" />
          <span class="stat-label">Members</span>
        </div>
      </div>
      <div class="stat-item">
        <div class="stat-value-row">
          <Skeleton v-if="summaryLoading" width="2rem" height="1.5rem" />
          <span v-else class="stat-value">{{ flowsWithErrorsCount }}</span>
        </div>
        <div class="stat-label-row">
          <i class="fas fa-project-diagram stat-icon" />
          <span class="stat-label">Flows</span>
        </div>
      </div>
      <div class="stat-item">
        <div class="stat-value-row">
          <Skeleton v-if="summaryLoading" width="2rem" height="1.5rem" />
          <span v-else class="stat-value">{{ uniqueMessagesCount }}</span>
        </div>
        <div class="stat-label-row">
          <i class="fas fa-comment-dots stat-icon" />
          <span class="stat-label">Messages</span>
        </div>
      </div>
    </div>

    <!-- Tabbed Results -->
    <Tabs v-model:value="activeTab" @update:value="onTabChange">
      <TabList>
        <Tab :value="0">By Member</Tab>
        <Tab :value="1">By Flow</Tab>
        <Tab :value="2">By Message</Tab>
      </TabList>
      <TabPanels>
      <!-- By Member Tab -->
      <TabPanel :value="0">
        <Panel header="Error Counts by Member" class="table-panel results">
          <template #icons>
            <div class="panel-toolbar">
              <span v-if="selectedMembers.length > 0" class="selection-info">
                {{ selectedMembers.length }} member(s) selected ({{ formatCount(selectedMembersErrorCount) }} errors)
              </span>
              <span v-else class="selection-info text-secondary">Select members to perform actions</span>
              <div class="action-buttons">
                <Button label="Acknowledge" icon="pi pi-check-square" class="p-button-sm" :disabled="selectedMembers.length === 0" @click="openActionDialog('acknowledge')" />
                <Button label="Resume" icon="pi pi-play" class="p-button-sm" :disabled="selectedMembers.length === 0" @click="openActionDialog('resume')" />
                <Button label="Replay" icon="pi pi-refresh" class="p-button-sm" :disabled="selectedMembers.length === 0" @click="openActionDialog('replay')" />
              </div>
            </div>
          </template>
          <DataTable
            v-model:selection="selectedMembers"
            :value="filteredMemberResults"
            aria-label="Fleet errors by DeltaFile"
            class="p-datatable p-datatable-sm p-datatable-gridlines clickable-rows"
            striped-rows
            :loading="loading"
            loading-icon="pi pi-spinner"
            :sort-field="sortField"
            :sort-order="sortOrder"
            @sort="onSort"
            @row-click="(e) => toggleMemberSelection(e.data)"
          >
            <template #empty>
              <div class="empty-state">
                <i class="pi pi-check-circle empty-state-icon success" />
                <span v-if="hasActiveFilters" class="empty-state-message">No errors match your current filters.</span>
                <span v-else class="empty-state-message">No errors found across fleet members.</span>
                <Button v-if="hasActiveFilters" label="Clear Filters" icon="pi pi-filter-slash" class="p-button-sm p-button-text mt-2" @click="clearFilters" />
              </div>
            </template>
            <template #loading>
              <div class="loading-state">
                <i class="pi pi-spin pi-spinner loading-spinner" />
                <span>Searching fleet members for errors...</span>
              </div>
            </template>

            <Column selection-mode="multiple" style="width: 3rem; flex: 0 0 3rem" />

            <Column field="memberName" header="Member" sortable class="member-column">
              <template #body="{ data }">
                <div class="member-cell">
                  <a v-if="data.status === 'CONNECTED'" :href="buildMemberErrorsUrl(data.memberUrl, buildFilter())" target="_blank" class="member-link">
                    {{ data.memberName }}
                    <i class="pi pi-external-link ml-1" style="font-size: 0.75rem" />
                  </a>
                  <span v-else>{{ data.memberName }}</span>
                </div>
              </template>
              <template #footer>
                <span class="font-bold">Total</span>
              </template>
            </Column>

            <Column field="count" header="Errors" sortable class="count-column">
              <template #body="{ data }">
                <span v-if="data.status === 'CONNECTED'" class="count-value error-count">{{ formatCount(data.count) }}</span>
                <span v-else v-tooltip.top="data.error || 'Unreachable'" class="error-text">
                  <i class="pi pi-exclamation-triangle" />
                </span>
              </template>
              <template #footer>
                <span class="count-value error-count">{{ formatCount(totalFilteredErrors) }}</span>
              </template>
            </Column>

            <Column field="tags" header="Tags" class="tags-column">
              <template #body="{ data }">
                <div class="tags-container">
                  <Tag v-for="tag in data.tags" :key="tag" :value="tag" severity="secondary" class="mr-1" />
                </div>
              </template>
            </Column>

            <Column field="status" header="Status" class="status-column">
              <template #body="{ data }">
                <Tag :severity="getStatusSeverity(data.status)" :value="data.status" />
              </template>
            </Column>
          </DataTable>
        </Panel>
      </TabPanel>

      <!-- By Flow Tab -->
      <TabPanel :value="1">
        <Panel header="Error Counts by Flow" class="table-panel results">
          <template #icons>
            <div class="panel-toolbar">
              <span v-if="selectedFlows.length > 0" class="selection-info">
                {{ selectedFlows.length }} flow(s) selected ({{ formatCount(selectedFlowsErrorCount) }} errors)
              </span>
              <span v-else class="selection-info text-secondary">Select flows to perform actions</span>
              <div class="action-buttons">
                <Button label="Acknowledge" icon="pi pi-check-square" class="p-button-sm" :disabled="selectedFlows.length === 0" @click="openActionDialog('acknowledge')" />
                <Button label="Resume" icon="pi pi-play" class="p-button-sm" :disabled="selectedFlows.length === 0" @click="openActionDialog('resume')" />
                <Button label="Replay" icon="pi pi-refresh" class="p-button-sm" :disabled="selectedFlows.length === 0" @click="openActionDialog('replay')" />
              </div>
            </div>
          </template>
          <DataTable
            v-model:selection="selectedFlows"
            :value="dataSourceSummary?.summaries || []"
            aria-label="Fleet errors by flow"
            class="p-datatable p-datatable-sm p-datatable-gridlines clickable-rows"
            striped-rows
            :loading="summaryLoading"
            loading-icon="pi pi-spinner"
            @row-click="(e) => toggleFlowSelection(e.data)"
          >
            <template #empty>
              <div class="empty-state">
                <i class="pi pi-check-circle empty-state-icon success" />
                <span v-if="hasActiveFilters" class="empty-state-message">No errors match your current filters.</span>
                <span v-else class="empty-state-message">No errors found by flow.</span>
                <Button v-if="hasActiveFilters" label="Clear Filters" icon="pi pi-filter-slash" class="p-button-sm p-button-text mt-2" @click="clearFilters" />
              </div>
            </template>
            <template #loading>
              <div class="loading-state">
                <i class="pi pi-spin pi-spinner loading-spinner" />
                <span>Loading error summary by flow...</span>
              </div>
            </template>

            <Column selection-mode="multiple" style="width: 3rem; flex: 0 0 3rem" />

            <Column field="key" header="Flow" sortable class="flow-name-column">
              <template #body="{ data }">
                <span class="font-semibold">{{ data.key }}</span>
              </template>
              <template #footer>
                <span class="font-bold">Total ({{ dataSourceSummary?.summaries?.length || 0 }} flows)</span>
              </template>
            </Column>

            <Column field="flowType" header="Type" sortable class="type-column">
              <template #body="{ data }">
                <Tag v-tooltip.top="getFlowTypeInfo(data.flowType).tooltip" :severity="getFlowTypeInfo(data.flowType).severity" class="flow-type-badge">
                  <i :class="getFlowTypeInfo(data.flowType).icon" class="mr-1" />
                  {{ getFlowTypeInfo(data.flowType).label }}
                </Tag>
              </template>
            </Column>

            <Column field="count" header="Errors" sortable class="count-column">
              <template #body="{ data }">
                <span class="count-value error-count">{{ formatCount(data.count) }}</span>
              </template>
              <template #footer>
                <span class="count-value error-count">{{ formatCount(dataSourceSummary?.totalCount) }}</span>
              </template>
            </Column>

            <Column field="nodeCount" header="Members" sortable class="node-column">
              <template #body="{ data }">
                <span>{{ data.nodeCount }}</span>
              </template>
            </Column>
          </DataTable>
        </Panel>
      </TabPanel>

      <!-- By Message Tab -->
      <TabPanel :value="2">
        <Panel header="Error Counts by Message" class="table-panel results">
          <template #icons>
            <div class="panel-toolbar">
              <span v-if="selectedMessages.length > 0" class="selection-info">
                {{ selectedMessages.length }} message(s) selected ({{ formatCount(selectedMessagesErrorCount) }} errors)
              </span>
              <span v-else class="selection-info text-secondary">Select messages to perform actions</span>
              <div class="action-buttons">
                <Button label="Acknowledge" icon="pi pi-check-square" class="p-button-sm" :disabled="selectedMessages.length === 0" @click="openActionDialog('acknowledge')" />
                <Button label="Resume" icon="pi pi-play" class="p-button-sm" :disabled="selectedMessages.length === 0" @click="openActionDialog('resume')" />
                <Button label="Replay" icon="pi pi-refresh" class="p-button-sm" :disabled="selectedMessages.length === 0" @click="openActionDialog('replay')" />
              </div>
            </div>
          </template>
          <DataTable
            v-model:selection="selectedMessages"
            :value="messageSummary?.summaries || []"
            aria-label="Fleet errors by message"
            class="p-datatable p-datatable-sm p-datatable-gridlines clickable-rows"
            striped-rows
            :loading="summaryLoading"
            loading-icon="pi pi-spinner"
            @row-click="(e) => toggleMessageSelection(e.data)"
          >
            <template #empty>
              <div class="empty-state">
                <i class="pi pi-check-circle empty-state-icon success" />
                <span v-if="hasActiveFilters" class="empty-state-message">No errors match your current filters.</span>
                <span v-else class="empty-state-message">No errors found by message.</span>
                <Button v-if="hasActiveFilters" label="Clear Filters" icon="pi pi-filter-slash" class="p-button-sm p-button-text mt-2" @click="clearFilters" />
              </div>
            </template>
            <template #loading>
              <div class="loading-state">
                <i class="pi pi-spin pi-spinner loading-spinner" />
                <span>Loading error summary by message...</span>
              </div>
            </template>

            <Column selection-mode="multiple" style="width: 3rem; flex: 0 0 3rem" />

            <Column field="key" header="Error Message" sortable class="message-column">
              <template #body="{ data }">
                <span class="error-message-text">{{ data.key }}</span>
              </template>
              <template #footer>
                <span class="font-bold">Total ({{ messageSummary?.summaries?.length || 0 }} messages)</span>
              </template>
            </Column>

            <Column field="count" header="Errors" sortable class="count-column">
              <template #body="{ data }">
                <span class="count-value error-count">{{ formatCount(data.count) }}</span>
              </template>
              <template #footer>
                <span class="count-value error-count">{{ formatCount(messageSummary?.totalCount) }}</span>
              </template>
            </Column>

            <Column field="nodeCount" header="Members" sortable class="node-column">
              <template #body="{ data }">
                <span>{{ data.nodeCount }}</span>
              </template>
            </Column>
          </DataTable>
        </Panel>
      </TabPanel>
      </TabPanels>
    </Tabs>

    <!-- Action Dialog -->
    <FleetErrorActionDialog
      v-model:visible="showActionDialog"
      :action="selectedAction"
      :member-names="selectedMemberNames"
      :filter="buildActionFilter()"
      :search-response="searchResponse"
      :expected-total="actionExpectedTotal"
      :flows="actionFlows"
      :messages="actionMessages"
      @completed="onActionCompleted"
    />
    </div>
  </div>
</template>

<script setup>
import FilterSidebar from "@/components/FilterSidebar.vue";
import FleetErrorActionDialog from "@/components/FleetErrorActionDialog.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchDateToolbar from "@/components/SearchDateToolbar.vue";
import useFederatedErrors from "@/composables/useFederatedErrors";
import useFederatedSearch from "@/composables/useFederatedSearch";
import { computed, inject, nextTick, onBeforeMount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useUrlSearchParams } from "@vueuse/core";

import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

import Button from "primevue/button";
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Select from "primevue/select";
import InputText from "primevue/inputtext";
import MultiSelect from "primevue/multiselect";
import Panel from "primevue/panel";
import Tab from "primevue/tab";
import TabList from "primevue/tablist";
import TabPanel from "primevue/tabpanel";
import TabPanels from "primevue/tabpanels";
import Tabs from "primevue/tabs";
import Skeleton from "primevue/skeleton";
import Tag from "primevue/tag";

dayjs.extend(utc);

const uiConfig = inject("uiConfig");
const route = useRoute();
const router = useRouter();

const {
  searchErrors,
  buildMemberErrorsUrl,
  loading,
  response: searchResponse,
  errorSummaryByDataSource,
  errorSummaryByMessage,
  summaryLoading,
} = useFederatedErrors();
const { fetchOptions, options: searchOptions } = useFederatedSearch();

// URL persistence
const urlParams = useUrlSearchParams("history");
const initialized = ref(false);
const timeExplicitlySet = ref(false);
const initialSearchDone = ref(false);

// Sidebar state
const sidebarOpen = ref(false);

// Tab state
const activeTab = ref(0);

// Sort state
const sortField = ref("count");
const sortOrder = ref(-1);

// Member list (for filter dropdowns)
const allMembers = ref([]);

// Summary data
const dataSourceSummary = ref(null);
const messageSummary = ref(null);

// Date handling
const queryDateType = ref("Modified");
const timestampFormat = "YYYY-MM-DD HH:mm:ss";

const defaultStartTimeDate = computed(() => {
  const date = dayjs().utc();
  return (uiConfig.useUTC ? date : date.local()).startOf("day");
});

const defaultEndTimeDate = computed(() => {
  const date = dayjs().utc();
  return (uiConfig.useUTC ? date : date.local()).endOf("day");
});

const startTimeDate = ref(new Date(defaultStartTimeDate.value.format(timestampFormat)));
const endTimeDate = ref(new Date(defaultEndTimeDate.value.format(timestampFormat)));

const resetDefaultTimeDate = computed(() => {
  return [new Date(defaultStartTimeDate.value.format(timestampFormat)), new Date(defaultEndTimeDate.value.format(timestampFormat))];
});

// Filters
const acknowledgedFilter = ref(false);
const dataSourcesFilter = ref([]);
const errorCauseFilter = ref("");
const memberNamesFilter = ref([]);
const memberTagsFilter = ref([]);

const ackOptions = [
  { name: "All", value: null },
  { name: "Unacknowledged (default)", value: false },
  { name: "Acknowledged", value: true },
];

// Persistence functions
const dateToISOString = (dateData) => {
  return dayjs(dateData).utc(uiConfig.useUTC).toISOString();
};

const isoStringToDate = (isoStringData) => {
  return uiConfig.useUTC ? dayjs(isoStringData).add(new Date().getTimezoneOffset(), "minute").toDate() : dayjs(isoStringData).toDate();
};

const loadPersistedParams = () => {
  // Load from URL params only
  const hasUrlParams = Object.keys(urlParams).some((k) => urlParams[k] !== null && urlParams[k] !== undefined && urlParams[k] !== "");
  if (!hasUrlParams) {
    initialized.value = true;
    return;
  }

  const persistedState = { ...urlParams };

  // Decode and apply persisted state
  if (persistedState.memberNames) {
    memberNamesFilter.value = persistedState.memberNames.split(",");
  }
  if (persistedState.memberTags) {
    memberTagsFilter.value = persistedState.memberTags.split(",");
  }
  if (persistedState.dataSources) {
    dataSourcesFilter.value = persistedState.dataSources.split(",");
  }
  if (persistedState.errorCause) {
    errorCauseFilter.value = persistedState.errorCause;
  }
  if (persistedState.acknowledged !== undefined && persistedState.acknowledged !== "") {
    acknowledgedFilter.value = persistedState.acknowledged === "true" ? true : persistedState.acknowledged === "false" ? false : null;
  }
  if (persistedState.startTime || persistedState.endTime) {
    timeExplicitlySet.value = true;
    if (persistedState.startTime) {
      startTimeDate.value = isoStringToDate(persistedState.startTime);
    }
    if (persistedState.endTime) {
      endTimeDate.value = isoStringToDate(persistedState.endTime);
    }
  }
  if (persistedState.tab !== undefined) {
    activeTab.value = Number(persistedState.tab);
  }
  if (persistedState.sortField) {
    sortField.value = persistedState.sortField;
  }
  if (persistedState.sortOrder !== undefined) {
    sortOrder.value = Number(persistedState.sortOrder);
  }

  initialized.value = true;
};

const savePersistedParams = () => {
  if (!initialized.value) return;

  const state = {};

  // Only save non-default values
  if (memberNamesFilter.value.length > 0) {
    state.memberNames = memberNamesFilter.value.join(",");
  }
  if (memberTagsFilter.value.length > 0) {
    state.memberTags = memberTagsFilter.value.join(",");
  }
  if (dataSourcesFilter.value.length > 0) {
    state.dataSources = dataSourcesFilter.value.join(",");
  }
  if (errorCauseFilter.value.trim()) {
    state.errorCause = errorCauseFilter.value.trim();
  }
  if (acknowledgedFilter.value !== false) {
    state.acknowledged = acknowledgedFilter.value === null ? "" : String(acknowledgedFilter.value);
  }
  // Only save time if explicitly changed from default preset
  if (timeExplicitlySet.value) {
    if (startTimeDate.value) {
      state.startTime = dateToISOString(startTimeDate.value);
    }
    if (endTimeDate.value) {
      state.endTime = dateToISOString(endTimeDate.value);
    }
  }
  state.tab = String(activeTab.value);
  if (sortField.value !== "count") {
    state.sortField = sortField.value;
  }
  if (sortOrder.value !== -1) {
    state.sortOrder = String(sortOrder.value);
  }

  // Update URL params
  Object.keys(urlParams).forEach((k) => (urlParams[k] = null));
  for (const key in state) {
    urlParams[key] = state[key];
  }
};

// Combine all data source types for the filter dropdown
const availableDataSources = computed(() => {
  if (!searchOptions.value) return [];
  const sources = [
    ...(searchOptions.value.restDataSources || []),
    ...(searchOptions.value.timedDataSources || []),
  ];
  return [...new Set(sources)].sort();
});

// Available member names for filter dropdown
const availableMemberNames = computed(() => {
  return allMembers.value.map((m) => m.memberName).sort();
});

// Available tags for filter dropdown (deduplicated from all members)
const availableTags = computed(() => {
  const tags = new Set();
  for (const member of allMembers.value) {
    if (member.tags) {
      for (const tag of member.tags) {
        tags.add(tag);
      }
    }
  }
  return [...tags].sort();
});

// Selection
const selectedMembers = ref([]);
const selectedFlows = ref([]);
const selectedMessages = ref([]);

const selectedMemberNames = computed(() => {
  const selected = selectedMembers.value.filter((m) => m.status === "CONNECTED").map((m) => m.memberName);
  // If all connected members are selected, return empty array to target all
  const allConnected = (searchResponse.value?.results || []).filter((m) => m.status === "CONNECTED");
  if (selected.length >= allConnected.length && allConnected.length > 0) {
    return [];
  }
  return selected;
});

const selectedFlowNames = computed(() => {
  return selectedFlows.value.map((f) => f.key);
});

// Summary stats computeds
const connectedMemberCount = computed(() => {
  if (!searchResponse.value?.results) return 0;
  return searchResponse.value.results.filter((m) => m.status === "CONNECTED").length;
});

const totalMemberCount = computed(() => {
  if (!searchResponse.value?.results) return 0;
  return searchResponse.value.results.length;
});

const flowsWithErrorsCount = computed(() => {
  return dataSourceSummary.value?.summaries?.length || 0;
});

const uniqueMessagesCount = computed(() => {
  return messageSummary.value?.summaries?.length || 0;
});

// Selection error count computeds
const selectedMembersErrorCount = computed(() => {
  return selectedMembers.value.reduce((sum, m) => sum + (m.count || 0), 0);
});

const selectedFlowsErrorCount = computed(() => {
  return selectedFlows.value.reduce((sum, f) => sum + (f.count || 0), 0);
});

const selectedMessagesErrorCount = computed(() => {
  return selectedMessages.value.reduce((sum, m) => sum + (m.count || 0), 0);
});

// Row click handlers for toggling selection
const toggleMemberSelection = (member) => {
  const index = selectedMembers.value.findIndex((m) => m.memberName === member.memberName);
  if (index === -1) {
    selectedMembers.value = [...selectedMembers.value, member];
  } else {
    selectedMembers.value = selectedMembers.value.filter((m) => m.memberName !== member.memberName);
  }
};

const toggleFlowSelection = (flow) => {
  const index = selectedFlows.value.findIndex((f) => f.key === flow.key);
  if (index === -1) {
    selectedFlows.value = [...selectedFlows.value, flow];
  } else {
    selectedFlows.value = selectedFlows.value.filter((f) => f.key !== flow.key);
  }
};

const toggleMessageSelection = (message) => {
  const index = selectedMessages.value.findIndex((m) => m.key === message.key);
  if (index === -1) {
    selectedMessages.value = [...selectedMessages.value, message];
  } else {
    selectedMessages.value = selectedMessages.value.filter((m) => m.key !== message.key);
  }
};

// Action dialog
const showActionDialog = ref(false);
const selectedAction = ref("acknowledge");

// Computed
const hasActiveFilters = computed(() => {
  return (
    acknowledgedFilter.value !== false ||
    dataSourcesFilter.value.length > 0 ||
    errorCauseFilter.value.trim() !== "" ||
    memberNamesFilter.value.length > 0 ||
    memberTagsFilter.value.length > 0
  );
});

const filteredMemberResults = computed(() => {
  if (!searchResponse.value?.results) return [];
  return searchResponse.value.results;
});

// Total error count from filtered results
const totalFilteredErrors = computed(() => {
  if (!searchResponse.value?.results) return 0;
  return searchResponse.value.results
    .filter((r) => r.status === "CONNECTED" && r.count !== null)
    .reduce((sum, r) => sum + r.count, 0);
});

// Summary of active filters for display
const filterSummary = computed(() => {
  const parts = [];

  if (memberNamesFilter.value.length > 0) {
    parts.push(memberNamesFilter.value.length === 1 ? memberNamesFilter.value[0] : `${memberNamesFilter.value.length} members`);
  } else {
    parts.push("All members");
  }

  if (memberTagsFilter.value.length > 0) {
    parts.push(memberTagsFilter.value.length === 1 ? `tag: ${memberTagsFilter.value[0]}` : `${memberTagsFilter.value.length} tags`);
  } else {
    parts.push("all tags");
  }

  if (dataSourcesFilter.value.length > 0) {
    parts.push(dataSourcesFilter.value.length === 1 ? dataSourcesFilter.value[0] : `${dataSourcesFilter.value.length} data sources`);
  } else {
    parts.push("all data sources");
  }

  if (acknowledgedFilter.value === false) {
    parts.push("unacknowledged");
  } else if (acknowledgedFilter.value === true) {
    parts.push("acknowledged");
  }

  if (errorCauseFilter.value.trim()) {
    parts.push(`message: "${errorCauseFilter.value.trim()}"`);
  }

  return parts.join(", ");
});

// Methods
const clearFilters = () => {
  // Reset all filters to defaults
  acknowledgedFilter.value = false;
  dataSourcesFilter.value = [];
  errorCauseFilter.value = "";
  memberNamesFilter.value = [];
  memberTagsFilter.value = [];

  // Reset time
  timeExplicitlySet.value = false;

  // Reset sort to default
  sortField.value = "count";
  sortOrder.value = -1;

  // Reset tab
  activeTab.value = 0;

  // Clear URL params
  Object.keys(urlParams).forEach((k) => (urlParams[k] = null));
};

const onDateChange = (start, end) => {
  startTimeDate.value = start;
  endTimeDate.value = end;
  timeExplicitlySet.value = true;
};

const buildFilter = () => {
  const filter = {};

  if (dataSourcesFilter.value.length > 0) {
    filter.dataSources = dataSourcesFilter.value;
  }
  if (errorCauseFilter.value.trim()) {
    filter.errorCause = errorCauseFilter.value.trim();
  }
  if (acknowledgedFilter.value !== null) {
    filter.errorAcknowledged = acknowledgedFilter.value;
  }
  if (memberNamesFilter.value.length > 0) {
    filter.memberNames = memberNamesFilter.value;
  }
  if (memberTagsFilter.value.length > 0) {
    filter.memberTags = memberTagsFilter.value;
  }
  if (startTimeDate.value) {
    filter.modifiedAfter = dayjs(startTimeDate.value).utc(uiConfig.useUTC).toISOString();
  }
  if (endTimeDate.value) {
    // Cap end time to now if it's in the future (can't have errors modified in the future)
    const endTime = dayjs(endTimeDate.value).utc(uiConfig.useUTC);
    const now = dayjs().utc();
    filter.modifiedBefore = (endTime.isAfter(now) ? now : endTime).toISOString();
  }

  return filter;
};

// Build filter for actions, incorporating tab-specific selections
const buildActionFilter = () => {
  const filter = buildFilter();

  if (activeTab.value === 1 && selectedFlows.value.length > 0) {
    // By Flow tab: filter to selected flows, grouped by flow type
    const byType = {
      dataSources: [],
      transforms: [],
      dataSinks: [],
    };
    for (const flow of selectedFlows.value) {
      const name = flow.key;
      switch (flow.flowType) {
        case "TRANSFORM":
          byType.transforms.push(name);
          break;
        case "EGRESS":
          byType.dataSinks.push(name);
          break;
        default:
          // DATA_SOURCE, REST_DATA_SOURCE, TIMED_DATA_SOURCE, or unknown
          byType.dataSources.push(name);
          break;
      }
    }
    if (byType.dataSources.length > 0) filter.dataSources = byType.dataSources;
    if (byType.transforms.length > 0) filter.transforms = byType.transforms;
    if (byType.dataSinks.length > 0) filter.dataSinks = byType.dataSinks;
  } else if (activeTab.value === 2 && selectedMessages.value.length === 1) {
    // By Message tab with single selection: filter to selected message
    filter.errorCause = selectedMessages.value[0].key;
  }
  // Note: When multiple messages are selected, we don't set errorCause here.
  // The dialog will handle executing actions for each message separately.

  return filter;
};

const fetchMemberList = async () => {
  try {
    const res = await fetch("/api/v2/leader/members", {
      method: "GET",
      headers: { Accept: "application/json" },
    });
    if (res.ok) {
      const data = await res.json();
      allMembers.value = data.members || [];
    }
  } catch (e) {
    console.error("Failed to fetch member list:", e);
  }
};

const executeSearch = async () => {
  const filter = buildFilter();
  await searchErrors(filter);
};

const fetchDataSourceSummary = async () => {
  const filter = buildFilter();
  dataSourceSummary.value = await errorSummaryByDataSource(filter);
};

const fetchMessageSummary = async () => {
  const filter = buildFilter();
  messageSummary.value = await errorSummaryByMessage(filter);
};

const refreshAll = async () => {
  // Refresh all data for the summary stats bar
  await Promise.all([executeSearch(), fetchDataSourceSummary(), fetchMessageSummary()]);
};

const onTabChange = async () => {
  if (activeTab.value === 1 && !dataSourceSummary.value) {
    await fetchDataSourceSummary();
  } else if (activeTab.value === 2 && !messageSummary.value) {
    await fetchMessageSummary();
  }
};

// Calculate expected total for action dialog based on active tab and selection
const actionExpectedTotal = computed(() => {
  if (activeTab.value === 1 && selectedFlows.value.length > 0) {
    // By Flow tab: sum of selected flows' counts
    return selectedFlows.value.reduce((sum, flow) => {
      return sum + (flow.count || 0);
    }, 0);
  } else if (activeTab.value === 2 && selectedMessages.value.length > 0) {
    // By Message tab: sum of selected messages' counts
    return selectedMessages.value.reduce((sum, msg) => {
      return sum + (msg.count || 0);
    }, 0);
  }
  // By Member tab: let dialog calculate from searchResponse
  return null;
});

// Get flows for action dialog (only for By Flow tab)
const actionFlows = computed(() => {
  if (activeTab.value === 1 && selectedFlows.value.length > 0) {
    return selectedFlows.value.map((flow) => ({
      name: flow.key,
      flowType: flow.flowType,
    }));
  }
  return null;
});

// Get messages for action dialog (only for By Message tab)
const actionMessages = computed(() => {
  if (activeTab.value === 2 && selectedMessages.value.length > 0) {
    return selectedMessages.value.map((msg) => msg.key);
  }
  return null;
});

const openActionDialog = (action) => {
  selectedAction.value = action;
  showActionDialog.value = true;
};

const onActionCompleted = () => {
  selectedMembers.value = [];
  selectedFlows.value = [];
  selectedMessages.value = [];
  refreshAll();
};

const formatCount = (count) => {
  if (count === null || count === undefined) return "0";
  return count.toLocaleString();
};

const getStatusSeverity = (status) => {
  switch (status) {
    case "CONNECTED":
      return "success";
    case "STALE":
      return "warn";
    case "UNREACHABLE":
      return "danger";
    default:
      return "secondary";
  }
};

const getFlowTypeInfo = (flowType) => {
  switch (flowType) {
    case "TRANSFORM":
      return {
        label: "Transform",
        icon: "pi pi-arrows-h",
        severity: "warn",
        tooltip: "Transform flow - processes and transforms data",
      };
    case "EGRESS":
      return {
        label: "Data Sink",
        icon: "pi pi-upload",
        severity: "success",
        tooltip: "Data Sink - egresses data to external systems",
      };
    case "REST_DATA_SOURCE":
      return {
        label: "REST Source",
        icon: "pi pi-download",
        severity: "info",
        tooltip: "REST Data Source - ingests data via REST API",
      };
    case "TIMED_DATA_SOURCE":
      return {
        label: "Timed Source",
        icon: "pi pi-clock",
        severity: "info",
        tooltip: "Timed Data Source - ingests data on a schedule",
      };
    default:
      return {
        label: "Data Source",
        icon: "pi pi-download",
        severity: "info",
        tooltip: "Data Source - ingests data into the system",
      };
  }
};

// Watchers
watch([dataSourcesFilter, errorCauseFilter, acknowledgedFilter, memberNamesFilter, memberTagsFilter, startTimeDate, endTimeDate], () => {
  // Skip during initialization to avoid triggering searches with partial filter state
  if (!initialized.value) return;

  // Reset summaries when filter changes
  dataSourceSummary.value = null;
  messageSummary.value = null;
  savePersistedParams();
  initialSearchDone.value = true;
  refreshAll();
});

watch(activeTab, () => {
  savePersistedParams();
  // Update Vue Router state so navigation components react to tab changes
  router.replace({ query: { ...route.query, tab: String(activeTab.value) } });
});

// Sort handler
const onSort = (event) => {
  sortField.value = event.sortField;
  sortOrder.value = event.sortOrder;
  savePersistedParams();
};

// Lifecycle
onBeforeMount(() => {
  loadPersistedParams();
});

onMounted(async () => {
  await fetchMemberList();
  await fetchOptions();
  // Wait for next tick to ensure preset values are applied before searching
  await nextTick();
  // Skip if watcher already triggered the initial search
  if (!initialSearchDone.value) {
    // Fetch all data for the summary stats bar
    await Promise.all([executeSearch(), fetchDataSourceSummary(), fetchMessageSummary()]);
  }
});
</script>

<style>
.fleet-errors-page {
  position: relative;
  display: flex;
  height: 100%;

  .main-content {
    flex: 1;
    overflow: auto;
  }

  label {
    font-weight: 500;
  }

  .member-cell {
    .member-link {
      font-weight: 500;
    }
  }

  .count-value {
    font-weight: 600;
    font-size: 1.1rem;
  }

  .error-count {
    color: var(--red-500);
  }

  .unack-count {
    color: var(--orange-500);
  }

  .error-text {
    color: var(--danger);
  }

  .error-message-text {
    font-family: monospace;
    font-size: 0.9rem;
    word-break: break-word;
  }

  .tags-container {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem;
  }

  .filter-item {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    margin-bottom: 1rem;
  }

  .filter-item label {
    font-weight: 600;
    font-size: 0.875rem;
  }

  .action-buttons {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .selection-info {
    font-size: 0.875rem;
  }

  .text-secondary {
    color: var(--text-color-secondary);
  }

  .summary-tags {
    display: flex;
    align-items: center;
  }

  .results {
    .p-panel-header {
      padding: 0 1.25rem;

      .p-panel-title {
        padding: 1rem 0;
      }
    }

    .p-panel-content {
      padding: 0 !important;
      border: none;
    }
  }

  td.member-column {
    width: 20%;
  }

  td.count-column {
    width: 15%;
    text-align: right;
  }

  th.count-column {
    text-align: right;
  }

  td.tags-column {
    width: 30%;
  }

  td.status-column {
    width: 15%;
  }

  td.key-column {
    width: 40%;
  }

  td.type-column {
    width: 12%;
  }

  .flow-type-badge {
    font-size: 0.75rem;
    padding: 0.25rem 0.5rem;
  }

  td.message-column {
    width: 60%;
  }

  td.node-column {
    width: 15%;
    text-align: right;
  }

  th.node-column {
    text-align: right;
  }

  .summary-stats-bar {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 1.5rem;
    padding: 0.5rem 0;
  }

  .stat-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 0.25rem;
    padding: 0.5rem 1rem;
    background: var(--surface-ground);
    border-radius: var(--border-radius);
    min-width: 100px;
  }

  .stat-value-row {
    display: flex;
    justify-content: center;
  }

  .stat-value {
    font-size: 1.25rem;
    font-weight: 700;
    line-height: 1.2;
  }

  .stat-label-row {
    display: flex;
    align-items: baseline;
    gap: 0.35rem;
  }

  .stat-icon {
    font-size: 0.75rem;
    color: var(--text-color-secondary);
    vertical-align: baseline;
  }

  .stat-icon.error-icon {
    color: var(--red-500);
  }

  .stat-label {
    font-size: 0.75rem;
    color: var(--text-color-secondary);
    text-transform: uppercase;
    letter-spacing: 0.025em;
  }

  .panel-toolbar {
    display: flex;
    align-items: center;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .panel-toolbar .selection-info {
    margin-right: auto;
  }

  .panel-toolbar .action-buttons {
    display: flex;
    gap: 0.5rem;
  }

  .clickable-rows :deep(tbody tr) {
    cursor: pointer;
  }

  .clickable-rows :deep(tbody tr:hover) {
    background: var(--surface-hover) !important;
  }

  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 2rem;
    text-align: center;
  }

  .empty-state-icon {
    font-size: 2.5rem;
    margin-bottom: 0.75rem;
    color: var(--text-color-secondary);
  }

  .empty-state-icon.success {
    color: var(--green-500);
  }

  .empty-state-message {
    font-size: 1rem;
    color: var(--text-color-secondary);
  }

  .loading-state {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.75rem;
    padding: 2rem;
    color: var(--text-color-secondary);
  }

  .loading-spinner {
    font-size: 1.25rem;
  }
}
</style>
