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
  <div class="filtered-page">
    <FilterSidebar
      v-model="sidebarOpen"
      title="Filtered Filters"
      tab-label="Filters"
      width="350px"
      :has-active-filters="filterOptionsSelected"
      @clear="clearOptions"
    >
      <div class="filter-item">
        <label>Flow</label>
        <Select v-model="flowSelected" placeholder="Select a Flow" show-clear :options="formattedFlows" option-group-label="label" option-group-children="sources" option-label="name" :editable="false" filter filter-placeholder="Filter flows" class="w-full" />
      </div>
      <div class="filter-item">
        <label>Filter Cause</label>
        <Select v-model="causeSelected" placeholder="Select a Filter Cause" show-clear :options="uniqueMessages" filter filter-placeholder="Filter causes" class="w-full" />
      </div>
    </FilterSidebar>

    <div class="main-content">
      <PageHeader heading="Filtered">
        <div class="btn-toolbar mb-2 mb-md-0">
          <Button :icon="refreshButtonIcon" label="Refresh" class="p-button deltafi-input-field ml-1 p-button-outlined" @click="onRefresh" />
        </div>
      </PageHeader>
      <ProgressBar v-if="!showTabs" mode="indeterminate" style="height: 0.5em" />
      <Tabs v-if="showTabs" v-model:value="activeTab">
        <TabList>
          <Tab :value="0">All</Tab>
          <Tab :value="1">By Flow</Tab>
          <Tab :value="2">By Cause</Tab>
        </TabList>
        <TabPanels>
          <TabPanel :value="0">
            <AllFilteredPanel ref="filterSummaryPanel" :active="activeTab === 0" :flow="flowSelected" :cause="causeSelected" @refresh-filters="onRefresh()" />
          </TabPanel>
          <TabPanel :value="1">
            <SummaryByFlowPanel ref="filterSummaryFlowPanel" :active="activeTab === 1" :flow="flowSelected" @refresh-filters="onRefresh()" @show-all-tab="showAllTab" />
          </TabPanel>
          <TabPanel :value="2">
            <SummaryByCausePanel ref="filterSummaryMessagePanel" :active="activeTab === 2" :flow="flowSelected" @refresh-filters="onRefresh()" @show-all-tab="showAllTab" />
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>
  </div>
</template>

<script setup>
import AllFilteredPanel from "@/components/filtered/AllPanel.vue";
import FilterSidebar from "@/components/FilterSidebar.vue";
import SummaryByCausePanel from "@/components/filtered/SummaryByCausePanel.vue";
import SummaryByFlowPanel from "@/components/filtered/SummaryByFlowPanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "primevue/progressbar";
import useFiltered from "@/composables/useFiltered";
import useFlows from "@/composables/useFlows";
import { computed, nextTick, onBeforeMount, onMounted, ref, watch } from "vue";
import { useStorage, StorageSerializers, useUrlSearchParams } from "@vueuse/core";

import _ from "lodash";

import Button from "primevue/button";
import Select from "primevue/select";
import Tabs from "primevue/tabs";
import TabList from "primevue/tablist";
import Tab from "primevue/tab";
import TabPanels from "primevue/tabpanels";
import TabPanel from "primevue/tabpanel";
import { useRoute, useRouter } from "vue-router";

const filterSummaryMessagePanel = ref();
const filterSummaryFlowPanel = ref();
const filterSummaryPanel = ref();
const { fetchAllFlowNames } = useFlows();
const loading = ref(false);
const sidebarOpen = ref(false);
const flowSelected = ref();
const causeSelected = ref();
const activeTab = ref(0);
const isNavigating = ref(false);
const params = useUrlSearchParams("history");
const useURLSearch = ref(false);
const route = useRoute();
const router = useRouter();
const filteredPanelState = useStorage("filtered-store", {}, sessionStorage, { serializer: StorageSerializers.object });
const { fetchUniqueMessages } = useFiltered();
const uniqueMessages = ref([]);
const formattedFlows = ref([]);
const allFlowNames = ref([]);
const showTabs = ref(false);
const flowTypeMap = {
  TRANSFORM: "transform",
  REST_DATA_SOURCE: "restDataSource",
  TIMED_DATA_SOURCE: "timedDataSource",
  ON_ERROR_DATA_SOURCE: "onErrorDataSource",
  DATA_SINK: "dataSink",
};

const setPersistedParams = () => {
  // Session Storage
  filteredPanelState.value = {
    tabs: activeTab.value,
    flowSelected: flowSelected.value,
    causeSelected: causeSelected.value,
  };
  // URL - use router.replace to keep route.query in sync
  const query = {
    ...route.query,
    tab: String(activeTab.value),
    flowName: flowSelected.value?.name || undefined,
    flowType: flowSelected.value?.type || undefined,
    cause: activeTab.value === 0 && causeSelected.value ? encodeURIComponent(causeSelected.value) : undefined,
  };
  router.replace({ query });
};

const formatFlowNames = () => {
  const map = {
    restDataSource: "Rest Data Sources",
    timedDataSource: "Timed Data Sources",
    transform: "Transforms",
    egress: "Data Sinks",
  };
  for (const [key, label] of Object.entries(map)) {
    if (!_.isEmpty(allFlowNames.value[key])) {
      let flows = _.map(allFlowNames.value[key], (name) => {
        return { name: name, type: key };
      });
      flows = _.sortBy(flows, ["name"]);
      formattedFlows.value.push({ label: label, sources: flows });
    }
  }
};

const clearOptions = () => {
  causeSelected.value = null;
  flowSelected.value = null;
  setPersistedParams();
};

const filterOptionsSelected = computed(() => {
  return _.some([causeSelected.value, flowSelected.value], (value) => !(value === "" || value === null || value === undefined));
});

const getPersistedParams = async () => {
  if (useURLSearch.value) {
    activeTab.value = route.query.tab ? parseInt(route.query.tab) : 0;
    if (route.query.flowName && route.query.flowType) {
      flowSelected.value = {
        name: route.query.flowName,
        type: route.query.flowType,
      };
    }
    causeSelected.value = route.query.cause ? decodeURIComponent(route.query.cause) : filteredPanelState.value.causeSelected;
  } else {
    activeTab.value = filteredPanelState.value.tabs ? parseInt(filteredPanelState.value.tabs) : 0;
    flowSelected.value = _.get(filteredPanelState.value, "flowSelected", null);
    causeSelected.value = _.get(filteredPanelState.value, "causeSelected", null);
  }
  setPersistedParams();
};

const setupWatchers = () => {
  watch([activeTab, flowSelected, causeSelected], () => {
    if (isNavigating.value) {
      isNavigating.value = false;
      return;
    }
    setPersistedParams();
  });

  // Watch for tab changes from navigation links
  watch(
    () => route.query.tab,
    (newTab) => {
      if (newTab !== undefined && newTab !== null) {
        const tabValue = parseInt(newTab);
        if (!isNaN(tabValue) && tabValue !== activeTab.value) {
          isNavigating.value = true;
          activeTab.value = tabValue;
        }
      }
    }
  );
};

const refreshButtonIcon = computed(() => {
  const classes = ["fa", "fa-sync-alt"];
  if (loading.value) classes.push("fa-spin");
  return classes.join(" ");
});

const showAllTab = (flowName, flowType, cause) => {
  if (flowName && flowType) {
    flowSelected.value = {
      name: flowName,
      type: flowTypeMap[flowType],
    };
  }

  if (cause) causeSelected.value = cause;

  activeTab.value = 0;
};

const refreshActiveTab = () => {
  switch (activeTab.value) {
    case 0:
      filterSummaryPanel.value.fetchFiltered();
      break;
    case 1:
      filterSummaryFlowPanel.value.fetchFilteredFlow();
      break;
    case 2:
      filterSummaryMessagePanel.value.fetchFilteredMessages();
      break;
  }
};

const onRefresh = async () => {
  loading.value = true;
  refreshActiveTab();
  loading.value = false;
};

onBeforeMount(() => {
  useURLSearch.value = route.fullPath.includes("filtered?");
});

onMounted(async () => {
  allFlowNames.value = await fetchAllFlowNames();
  formatFlowNames();
  uniqueMessages.value = await fetchUniqueMessages();
  await getPersistedParams();
  await nextTick();
  showTabs.value = true;
  setupWatchers();
});
</script>

<style>
.filtered-page {
  position: relative;
  display: flex;
  height: 100%;

  .main-content {
    flex: 1;
    overflow: auto;
  }

  .filter-item {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    margin-bottom: 1rem;

    label {
      font-weight: 600;
      font-size: 0.875rem;
    }
  }

  .time-range .form-control:disabled,
  .time-range .form-control[readonly] {
    background-color: #ffffff;
  }

  .p-panel {
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

    .p-panel-content {
      padding: 0;
      border: none;
    }
  }

  .p-datatable.p-datatable-striped .p-datatable-tbody > tr.p-highlight {
    color: #ffffff;

    a,
    button {
      color: #eeeeee;
    }
  }

  .p-paginator {
    background: inherit !important;
    color: inherit !important;
    border: none !important;
    padding: 0 !important;
    font-size: inherit !important;

    .p-paginator-current {
      background: unset;
      color: unset;
      border: unset;
    }
  }

  .vdpr-datepicker__calendar-dialog {
    margin-left: -276px !important;
    position: fixed !important;
  }
}
</style>
