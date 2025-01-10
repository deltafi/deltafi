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
  <div class="filtered-page">
    <PageHeader heading="Filtered">
      <div class="time-range btn-toolbar mb-2 mb-md-0 align-items-center">
        <Button v-tooltip.right="{ value: `Clear Filters`, disabled: !filterOptionsSelected }" rounded :class="`mx-1 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${filterOptionsSelected ? 'p-column-filter-menu-button-active' : null}`" :disabled="!filterOptionsSelected" @click="clearOptions()">
          <i class="pi pi-filter" style="font-size: 1rem"></i>
        </Button>
        <Dropdown v-model="flowSelected" placeholder="Select a Flow" show-clear :options="formattedFlows" option-group-label="label" option-group-children="sources" option-label="name" :editable="false" class="deltafi-input-field ml-3 flow-dropdown" />
        <Dropdown v-model="causeSelected" placeholder="Select an Filter Cause" show-clear :options="uniqueMessages" class="deltafi-input-field ml-3 flow-dropdown" />
        <Button :icon="refreshButtonIcon" label="Refresh" class="p-button deltafi-input-field ml-3 p-button-outlined" @click="onRefresh" />
      </div>
    </PageHeader>
    <ProgressBar v-if="!showTabs" mode="indeterminate" style="height: 0.5em" />
    <TabView v-if="showTabs" v-model:activeIndex="activeTab">
      <TabPanel header="All">
        <AllFilteredPanel ref="filterSummaryPanel" :flow="flowSelected" :cause="causeSelected" @refresh-filters="onRefresh()" />
      </TabPanel>
      <TabPanel header="By Flow">
        <SummaryByFlowPanel ref="filterSummaryFlowPanel" :flow="flowSelected" @refresh-filters="onRefresh()" @show-all-tab="showAllTab" />
      </TabPanel>
      <TabPanel header="By Cause">
        <SummaryByCausePanel ref="filterSummaryMessagePanel" :flow="flowSelected" @refresh-filters="onRefresh()" @show-all-tab="showAllTab" />
      </TabPanel>
    </TabView>
  </div>
</template>

<script setup>
import AllFilteredPanel from "@/components/filtered/AllPanel.vue";
import SummaryByFlowPanel from "@/components/filtered/SummaryByFlowPanel.vue";
import SummaryByCausePanel from "@/components/filtered/SummaryByCausePanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import useFlows from "@/composables/useFlows";
import { ref, computed, onMounted, watch, onBeforeMount, nextTick } from "vue";
import { useStorage, StorageSerializers, useUrlSearchParams } from "@vueuse/core";
import Dropdown from "primevue/dropdown";
import Button from "primevue/button";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";
import { useRoute } from "vue-router";
import useFiltered from "@/composables/useFiltered";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar";

const filterSummaryMessagePanel = ref();
const filterSummaryFlowPanel = ref();
const filterSummaryPanel = ref();
const { fetchAllFlowNames } = useFlows();
const loading = ref(false);
const flowSelected = ref();
const causeSelected = ref();
const activeTab = ref(0);
const params = useUrlSearchParams("history");
const useURLSearch = ref(false);
const route = useRoute();
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
  DATA_SINK: "dataSink",
}

const setPersistedParams = () => {
  // Session Storage
  filteredPanelState.value = {
    tabs: activeTab.value,
    flowSelected: flowSelected.value,
    causeSelected: causeSelected.value,
  };
  // URL
  params.tab = activeTab.value;
  params.flowName = flowSelected.value?.name;
  params.flowType = flowSelected.value?.type;
  if (activeTab.value === 0) {
    params.cause = causeSelected.value ? encodeURIComponent(causeSelected.value) : null;
  } else {
    params.cause = null;
  }
};

const formatFlowNames = () => {
  const map = {
    restDataSource: "Rest Data Sources",
    timedDataSource: "Timed Data Sources",
    transform: "Transforms",
    egress: "Data Sinks"
  };
  for (const [key, label] of Object.entries(map)) {
    if (!_.isEmpty(allFlowNames.value[key])) {
      let flows = _.map(allFlowNames.value[key], (name) => {
        return { name: name, type: key }
      })
      flows = _.sortBy(flows, ['name']);
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
    activeTab.value = params.tab ? parseInt(params.tab) : 0;
    if (params.flowName && params.flowType) {
      flowSelected.value = {
        name: params.flowName,
        type: params.flowType
      }
    }
    causeSelected.value = causeSelected.value = params.cause ? decodeURIComponent(params.cause) : filteredPanelState.value.causeSelected;
  } else {
    activeTab.value = filteredPanelState.value.tabs ? parseInt(filteredPanelState.value.tabs) : 0;
    flowSelected.value = _.get(filteredPanelState.value, "flowSelected", null);
    causeSelected.value = causeSelected.value = _.get(filteredPanelState.value, "causeSelected", null);
  }
  setPersistedParams();
};

const setupWatchers = () => {
  watch([activeTab, flowSelected, causeSelected], () => {
    setPersistedParams();
  });
};

const refreshButtonIcon = computed(() => {
  let classes = ["fa", "fa-sync-alt"];
  if (loading.value) classes.push("fa-spin");
  return classes.join(" ");
});

const showAllTab = (flowName, flowType, cause) => {
  if (flowName && flowType) {
    flowSelected.value = {
      name: flowName,
      type: flowTypeMap[flowType]
    };
  }

  if (cause) causeSelected.value = cause;

  activeTab.value = 0;
};

const onRefresh = () => {
  loading.value = true;
  filterSummaryPanel.value.fetchFiltered();
  filterSummaryFlowPanel.value.fetchFilteredFlow();
  filterSummaryMessagePanel.value.fetchFilteredMessages();
  loading.value = false;
};

onBeforeMount(() => {
  useURLSearch.value = route.fullPath.includes("filteredD?");
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

<style lang="scss">
.filtered-page {
  .p-autocomplete-empty-message {
    margin-left: 0.5rem;
  }

  .p-autocomplete-input {
    width: 16rem;
  }

  .flow-dropdown {
    width: 16rem;
  }

  .time-range .form-control:disabled,
  .time-range .form-control[readonly] {
    background-color: #ffffff;
  }

  .show-acknowledged-toggle {
    width: 14rem;
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
}
</style>
