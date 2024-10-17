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
  <div class="filtered-page">
    <PageHeader heading="Filtered">
      <div class="time-range btn-toolbar mb-2 mb-md-0 align-items-center">
        <Button v-tooltip.right="{ value: `Clear Filters`, disabled: !filterOptionsSelected }" rounded :class="`mx-1 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${filterOptionsSelected ? 'p-column-filter-menu-button-active' : null}`" :disabled="!filterOptionsSelected" @click="clearOptions()">
          <i class="pi pi-filter" style="font-size: 1rem"></i>
        </Button>
        <Dropdown v-model="dataSourceNameSelected" placeholder="Select a Data Source" :options="formattedDataSourceNames" option-group-label="label" option-group-children="sources" show-clear :editable="false" class="deltafi-input-field ml-3 flow-dropdown" />
        <AutoComplete v-model="selectedMessageValue" :suggestions="filteredMessages" placeholder="Select Cause" class="deltafi-input-field ml-3" force-selection @complete="messageSearch" />
        <Button :icon="refreshButtonIcon" label="Refresh" class="p-button deltafi-input-field ml-3 p-button-outlined" @click="onRefresh" />
      </div>
    </PageHeader>
    <TabView v-model:activeIndex="activeTab">
      <TabPanel header="All">
        <AllFilteredPanel ref="filterSummaryPanel" :data-source-name="dataSourceNameSelected" :filtered-cause-selected="filteredCauseSelected" @refresh-filters="onRefresh()" @filter-cause-changed:filtered-cause="messageSelected" />
      </TabPanel>
      <TabPanel header="By Flow">
        <SummaryByFlowPanel ref="filterSummaryFlowPanel" :data-source-flow-name="dataSourceNameSelected" @refresh-filters="onRefresh()" />
      </TabPanel>
      <TabPanel header="By Cause">
        <SummaryByMessagePanel ref="filterSummaryMessagePanel" :data-source-flow-name="dataSourceNameSelected" @refresh-filters="onRefresh()" @change-tab:filtered-cause:flow-selected="tabChange" />
      </TabPanel>
    </TabView>
  </div>
</template>

<script setup>
import AllFilteredPanel from "@/components/filtered/AllPanel.vue";
import SummaryByFlowPanel from "@/components/filtered/SummaryByFlowPanel.vue";
import SummaryByMessagePanel from "@/components/filtered/SummaryByMessagePanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import useFlows from "@/composables/useFlows";
import { ref, computed, onMounted, watch, onBeforeMount, nextTick } from "vue";
import { useStorage, StorageSerializers, useUrlSearchParams } from "@vueuse/core";
import Dropdown from "primevue/dropdown";
import Button from "primevue/button";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";
import { useRoute } from "vue-router";
import AutoComplete from "primevue/autocomplete";
import useFiltered from "@/composables/useFiltered";

const filterSummaryMessagePanel = ref();
const filterSummaryFlowPanel = ref();
const filterSummaryPanel = ref();
const { allDataSourceFlowNames, fetchAllDataSourceFlowNames } = useFlows();
const formattedDataSourceNames = ref([]);
const loading = ref(false);
const dataSourceNameSelected = ref(null);
const filteredCauseSelected = ref(null);
const activeTab = ref(0);
const params = useUrlSearchParams("history");
const useURLSearch = ref(false);
const route = useRoute();
const filteredPanelState = useStorage("filtered-store", {}, sessionStorage, { serializer: StorageSerializers.object });
const { fetchAllMessage, allCauses: filteredCauses } = useFiltered();
const filteredMessages = ref([]);
const selectedMessageValue = ref("");
const messageValues = ref();

const setPersistedParams = () => {
  // Session Storage
  filteredPanelState.value = {
    tabs: activeTab.value,
    dataSourceNameSelected: dataSourceNameSelected.value,
    filteredCauseSelected: filteredCauseSelected.value,
  };
  // URL
  params.tab = activeTab.value;
  params.dataSource = dataSourceNameSelected.value;
  if (activeTab.value === 0) {
    params.filtered = filteredCauseSelected.value ? encodeURIComponent(filteredCauseSelected.value) : null;
  } else {
    params.filtered = null;
  }
};

const messageSearch = (event) => {
  setTimeout(() => {
    if (!event.query.trim().length) {
      filteredMessages.value = [...messageValues.value];
    } else {
      filteredMessages.value = messageValues.value.filter((message) => {
        return message.toLowerCase().includes(event.query.toLowerCase());
      });
    }
  }, 1000);
};

const clearOptions = () => {
  filteredMessages.value = [];
  selectedMessageValue.value = "";
  filteredCauseSelected.value = "";
  dataSourceNameSelected.value = null;
  setPersistedParams();
};

const filterOptionsSelected = computed(() => {
  return _.some([selectedMessageValue.value, dataSourceNameSelected.value], (value) => !(value === "" || value === null || value === undefined));
});

const getPersistedParams = async () => {
  if (useURLSearch.value) {
    activeTab.value = params.tab ? parseInt(params.tab) : 0;
    dataSourceNameSelected.value = params.dataSource ? params.dataSource : null;
    selectedMessageValue.value = filteredCauseSelected.value = params.filtered ? decodeURIComponent(params.filtered) : filteredPanelState.value.filteredCauseSelected;
  } else {
    activeTab.value = filteredPanelState.value.tabs ? parseInt(filteredPanelState.value.tabs) : 0;
    dataSourceNameSelected.value = _.get(filteredPanelState.value, "dataSourceNameSelected", null);
    selectedMessageValue.value = filteredCauseSelected.value = _.get(filteredPanelState.value, "filteredCauseSelected", null);
  }
  setPersistedParams();
};

const setupWatchers = () => {
  watch([activeTab, dataSourceNameSelected, filteredCauseSelected], () => {
    setPersistedParams();
  });
  watch([selectedMessageValue], () => {
    filteredCauseSelected.value = selectedMessageValue.value;
  });
};

const refreshButtonIcon = computed(() => {
  let classes = ["fa", "fa-sync-alt"];
  if (loading.value) classes.push("fa-spin");
  return classes.join(" ");
});

const tabChange = (filteredCause, flowSelected) => {
  dataSourceNameSelected.value = flowSelected;
  filteredCauseSelected.value = filteredCause;
  selectedMessageValue.value = filteredCause;

  activeTab.value = 0;
};

const messageSelected = (cause) => {
  if (filteredCauseSelected.value !== cause) filteredCauseSelected.value = cause;
};

const onRefresh = () => {
  loading.value = true;
  filterSummaryPanel.value.fetchFiltered();
  filterSummaryFlowPanel.value.fetchFilteredFlow();
  filterSummaryMessagePanel.value.fetchFilteresMessages();
  loading.value = false;
};

onBeforeMount(() => {
  useURLSearch.value = route.fullPath.includes("filteredD?");
});

onMounted(async () => {
  await fetchAllDataSourceFlowNames();
  formatDataSourceNames();
  await getPersistedParams();
  await nextTick();
  await fetchAllMessage();
  const uniqueMessages = [];
  for (let i = 0; i < filteredCauses.value.length; i++) {
    if (!uniqueMessages.includes(filteredCauses.value[i].message)) {
      uniqueMessages.push(filteredCauses.value[i].message);
    }
  }
  messageValues.value = uniqueMessages;

  setupWatchers();
});

const formatDataSourceNames = () => {
  if (!_.isEmpty(allDataSourceFlowNames.value.restDataSource)) {
    formattedDataSourceNames.value.push({ label: "Rest Data Sources", sources: allDataSourceFlowNames.value.restDataSource });
  }
  if (!_.isEmpty(allDataSourceFlowNames.value.timedDataSource)) {
    formattedDataSourceNames.value.push({ label: "Timed Data Sources", sources: allDataSourceFlowNames.value.timedDataSource });
  }
};
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

  tr.action-filtered {
    cursor: pointer !important;
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
