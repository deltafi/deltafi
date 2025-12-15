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

<!-- ABOUTME: Fleet-wide federated search page for querying DeltaFiles across member systems. -->
<!-- ABOUTME: Shows aggregated counts per system with links to drill down on each member. -->

<template>
  <div class="fleet-search-page">
    <div>
      <PageHeader heading="Fleet Search">
        <SearchDateToolbar
          v-model:date-type="model.queryDateTypeOptions"
          :date-type-options="queryDateTypes"
          :start-time-date="model.startTimeDate"
          :end-time-date="model.endTimeDate"
          :reset-default="resetDefaultTimeDate"
          :loading="loading"
          @date-change="onDateChange"
          @refresh="executeSearch"
        />
      </PageHeader>
    </div>
    <div class="row mb-3">
      <div class="col-12">
        <DeltaFileSearchOptions
          v-model="model"
          :active-advanced-options="activeAdvancedOptions"
          :formatted-data-source-names="formattedDataSourceNames"
          :data-sink-options="dataSinkOptions"
          :transform-options="transformOptions"
          :topic-options="topicOptions"
          :stage-options="stageOptions"
          :annotation-keys-options="annotationsKeysOptions"
          :size-units-options-map="sizeUnitsMap"
          :size-types-options="sizeTypes"
          :collapsed="collapsedSearchOption"
          :annotation-validation-fn="validAnnotation"
          title="Search Options"
          @clear-options="clearOptions"
          @add-annotation="addAnnotationItem"
          @remove-annotation="removeAnnotationItem"
        />
      </div>
    </div>
    <!-- Filters -->
    <CollapsiblePanel v-if="searchResponse" class="filters-panel mb-3" :collapsed="true">
      <template #header>
        <span class="filters-header">
          <span class="p-panel-title">Member Filters</span>
          <Button v-tooltip.right="{ value: 'Clear filters', disabled: !hasActiveFilters }" rounded :class="`ml-2 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${hasActiveFilters ? 'p-column-filter-menu-button-active' : ''}`" :disabled="!hasActiveFilters" @click.stop="clearFilters">
            <i class="pi pi-filter" style="font-size: 1rem" />
          </Button>
        </span>
      </template>
      <div class="filters-content">
        <div class="filter-item">
          <label>Search by Name</label>
          <InputText v-model="systemNameFilter" placeholder="Filter by system name..." class="w-full" />
        </div>

        <div class="filter-item">
          <label>Filter by Tags</label>
          <MultiSelect v-model="selectedResultTags" :options="availableResultTags" placeholder="Select tags..." class="w-full" />
        </div>
      </div>
      <div class="filter-stats">
        Showing {{ filteredResults.length }} of {{ searchResponse.results.length }} systems
      </div>
    </CollapsiblePanel>

    <Panel header="Results" class="table-panel results">
      <template #icons>
        <div v-if="searchResponse" class="summary-tags mr-3">
          <Tag severity="info" :value="`${formatTotalCount(searchResponse)} total matches`" class="mr-2" />
          <Tag v-if="searchResponse.membersFailed > 0" severity="warning" :value="`${searchResponse.membersFailed} member(s) unreachable`" />
        </div>
      </template>
      <DataTable :value="filteredResults" responsive-layout="scroll" class="p-datatable p-datatable-sm p-datatable-gridlines" striped-rows :loading="loading" loading-icon="pi pi-spinner">
        <template #empty>
          <span v-if="searchResponse?.results?.length > 0 && isResultFiltered">No member systems match your filter criteria.</span>
          <span v-else>Click "Search Fleet" to search across all systems.</span>
        </template>
        <template #loading> Searching fleet members. Please wait. </template>
        <Column field="memberName" header="System" class="system-column">
          <template #body="{ data }">
            <div class="member-cell">
              <a v-if="data.status === 'CONNECTED'" :href="buildMemberSearchUrl(data.memberUrl, buildFilter(), model.queryDateTypeOptions)" target="_blank" class="member-link">
                {{ data.memberName }}
                <i class="pi pi-external-link ml-1" style="font-size: 0.75rem" />
              </a>
              <span v-else>{{ data.memberName }}</span>
            </div>
          </template>
        </Column>
        <Column field="count" header="Count" class="count-column">
          <template #body="{ data }">
            <span v-if="data.status === 'CONNECTED'" class="count-value">{{ formatCount(data.count) }}</span>
            <span v-else class="error-text">
              <i class="pi pi-exclamation-triangle mr-1" />
              {{ data.error || "Unreachable" }}
            </span>
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
  </div>
</template>

<script setup>
import DeltaFileSearchOptions from "@/components/DeltaFileSearchOptions.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchDateToolbar from "@/components/SearchDateToolbar.vue";
import useDeltaFilesQueryBuilder from "@/composables/useDeltaFilesQueryBuilder";
import useDeltaFileSearchFilters from "@/composables/useDeltaFileSearchFilters";
import useFederatedSearch from "@/composables/useFederatedSearch";
import { computed, nextTick, onBeforeMount, ref } from "vue";

import _ from "lodash";

import Column from "primevue/column";
import DataTable from "primevue/datatable";
import InputText from "primevue/inputtext";
import MultiSelect from "primevue/multiselect";
import Panel from "primevue/panel";
import Tag from "primevue/tag";
import Button from "primevue/button";
import CollapsiblePanel from "@/components/CollapsiblePanel.vue";

const { getEnumValuesByEnumType } = useDeltaFilesQueryBuilder();
const { search: searchFederated, buildMemberSearchUrl, loading, response: searchResponse, fetchOptions, options: aggregatedOptions } = useFederatedSearch();

// Use shared search filter composable with URL persistence
const {
  model,
  resetDefaultTimeDate,
  activeAdvancedOptions,
  sizeUnitsMap,
  sizeTypes,
  queryDateTypes,
  loadPersistedParams,
  buildFilter,
  clearOptions,
  updateDateRange,
  setupFilterWatchers,
} = useDeltaFileSearchFilters({ storageKey: "fleet-search-persisted-params" });

const collapsedSearchOption = ref(true);
const formattedDataSourceNames = ref([]);

// Dropdown options derived from aggregated options
const dataSinkOptions = computed(() => aggregatedOptions.value?.dataSinks || []);
const transformOptions = computed(() => aggregatedOptions.value?.transforms || []);
const topicOptions = computed(() => aggregatedOptions.value?.topics || []);
const annotationsKeysOptions = computed(() => {
  if (aggregatedOptions.value?.annotationKeys) {
    return aggregatedOptions.value.annotationKeys.map((key) => ({ key }));
  }
  return [];
});

// Result filtering and sorting
const systemNameFilter = ref("");
const selectedResultTags = ref([]);

const availableResultTags = computed(() => {
  if (!searchResponse.value?.results) return [];
  const tags = new Set();
  searchResponse.value.results.forEach((r) => r.tags?.forEach((t) => tags.add(t)));
  return Array.from(tags).sort();
});

const isResultFiltered = computed(() => {
  return systemNameFilter.value.length > 0 || selectedResultTags.value.length > 0;
});

const hasActiveFilters = computed(() => {
  return systemNameFilter.value.length > 0 || selectedResultTags.value.length > 0;
});

const clearFilters = () => {
  systemNameFilter.value = "";
  selectedResultTags.value = [];
};

const filteredResults = computed(() => {
  if (!searchResponse.value?.results) return [];

  let results = [...searchResponse.value.results];

  // Filter by system name
  if (systemNameFilter.value) {
    const filter = systemNameFilter.value.toLowerCase();
    results = results.filter((r) => r.memberName.toLowerCase().includes(filter));
  }

  // Filter by tags
  if (selectedResultTags.value.length > 0) {
    results = results.filter((r) => selectedResultTags.value.some((tag) => r.tags?.includes(tag)));
  }

  // Sort by count descending, then by name ascending
  results.sort((a, b) => {
    const countA = a.count ?? -1;
    const countB = b.count ?? -1;
    if (countB !== countA) return countB - countA;
    return a.memberName.localeCompare(b.memberName);
  });

  return results;
});

// Dropdown options
const stageOptions = ref([]);

onBeforeMount(async () => {
  loadPersistedParams();
  fetchDropdownOptions();
  // Expand search options if there are active filters
  collapsedSearchOption.value = !activeAdvancedOptions.value;
  await nextTick();
  executeSearch();
  setupFilterWatchers(executeSearch);
});

const fetchDropdownOptions = async () => {
  await fetchOptions();
  formatDataSourceNames();
  fetchStages();
};

const formatDataSourceNames = () => {
  formattedDataSourceNames.value = [];
  if (aggregatedOptions.value?.restDataSources?.length) {
    formattedDataSourceNames.value.push({ group: "Rest Data Sources", sources: aggregatedOptions.value.restDataSources.map((s) => ({ label: s })) });
  }
  if (aggregatedOptions.value?.timedDataSources?.length) {
    formattedDataSourceNames.value.push({ group: "Timed Data Sources", sources: aggregatedOptions.value.timedDataSources.map((s) => ({ label: s })) });
  }
};

const fetchStages = async () => {
  const enumsStageTypes = await getEnumValuesByEnumType("DeltaFileStage");
  stageOptions.value = _.uniq(_.map(enumsStageTypes.data.__type.enumValues, "name"));
};

const validAnnotation = (key) => {
  return aggregatedOptions.value?.annotationKeys?.includes(key) ?? false;
};

const addAnnotationItem = (key, value) => {
  model.value.validatedAnnotations.push({ key, value, valid: validAnnotation(key) });
};

const removeAnnotationItem = (item) => {
  const index = model.value.validatedAnnotations.indexOf(item);
  model.value.validatedAnnotations.splice(index, 1);
  model.value.annotations.splice(index, 1);
};

const onDateChange = (startDate, endDate) => {
  updateDateRange(startDate, endDate);
};

const executeSearch = async () => {
  const filter = buildFilter();
  await searchFederated(filter);
};

const MANY_RESULTS = 10000;

const formatCount = (count) => {
  if (count === null || count === undefined) return "0";
  if (count >= MANY_RESULTS) return ">10,000";
  return count.toLocaleString();
};

const formatTotalCount = (response) => {
  if (!response) return "0";
  // Check if any member hit the limit
  const hasMany = response.results.some((r) => r.count >= MANY_RESULTS);
  if (hasMany || response.totalCount >= MANY_RESULTS) {
    return "many";
  }
  return response.totalCount.toLocaleString();
};

const getStatusSeverity = (status) => {
  switch (status) {
    case "CONNECTED":
      return "success";
    case "STALE":
      return "warning";
    case "UNREACHABLE":
      return "danger";
    default:
      return "secondary";
  }
};
</script>

<style>
.fleet-search-page {
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

  .error-text {
    color: var(--danger);
  }

  .tags-container {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem;
  }

  .filters-panel {
    margin-bottom: 1.5rem;
    .p-panel-header {
      padding: 0 1.25rem;

      .p-panel-title {
        padding: 1rem 0;
      }

      .p-panel-header-icon {
        margin-top: 0.25rem;
        margin-right: 0;
      }
    }
  }

  .filters-content {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 1rem;
    align-items: end;
  }

  .filter-item {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
  }

  .filter-item label {
    font-weight: 600;
    font-size: 0.875rem;
  }

  .filter-stats {
    color: var(--text-color-secondary);
    font-size: 0.875rem;
    white-space: nowrap;
    padding: 0;
    margin-top: 1rem;
    text-align: center;
  }

  .filters-header {
    display: flex;
    align-items: center;
    gap: 0.5rem;
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

  td.system-column {
    width: 20%;
  }

  td.count-column {
    width: 15%;
  }

  td.tags-column {
    width: 40%;
  }

  td.status-column {
    width: 15%;
  }
}
</style>
