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
  <div class="errors-page">
    <FilterSidebar
      v-model="sidebarOpen"
      title="Error Filters"
      tab-label="Filters"
      width="350px"
      :has-active-filters="filterOptionsSelected"
      @clear="clearOptions"
    >
      <div class="filter-item">
        <label for="errors-flow-filter">Flow</label>
        <Select id="errors-flow-filter" v-model="flowSelected" placeholder="Select a Flow" show-clear :options="formattedFlows" option-group-label="label" option-group-children="sources" option-label="name" :editable="false" filter filter-placeholder="Filter flows" class="w-full" />
      </div>
      <div class="filter-item">
        <label for="errors-message-filter">Error Message</label>
        <Select id="errors-message-filter" v-model="errorMessageSelected" placeholder="Select an Error Message" show-clear :options="uniqueErrorMessages" filter filter-placeholder="Filter messages" class="w-full" />
      </div>
      <div class="filter-item">
        <label for="errors-status-filter">Status</label>
        <Select id="errors-status-filter" v-model="selectedAckOption" :options="ackOptions" option-label="name" option-value="value" :editable="false" class="w-full" />
      </div>
    </FilterSidebar>

    <div class="main-content">
      <PageHeader heading="Errors">
        <div class="btn-toolbar mb-2 mb-md-0">
          <Button v-tooltip.left="refreshButtonTooltip" :icon="refreshButtonIcon" label="Refresh" class="deltafi-input-field ml-1" :severity="refreshButtonSeverity" :variant="refreshButtonVariant" :badge="refreshButtonBadge" badge-class="p-badge-danger" @click="onRefresh" />
        </div>
      </PageHeader>
      <ProgressBar v-if="!showTabs" mode="indeterminate" style="height: 0.5em" />
      <Tabs v-if="showTabs" v-model:value="activeTab" @update:value="unSelectAllRows">
        <TabList>
          <Tab :value="0">All</Tab>
          <Tab :value="1">By Flow</Tab>
          <Tab :value="2">By Message</Tab>
        </TabList>
        <TabPanels>
          <TabPanel :value="0">
            <AllErrorsPanel ref="errorsSummaryPanel" :active="activeTab === 0" :acknowledged="acknowledged" :flow="flowSelected" :errors-message-selected="errorMessageSelected" @refresh-errors="onRefresh()" />
          </TabPanel>
          <TabPanel :value="1">
            <ErrorsSummaryByFlowPanel ref="errorSummaryFlowPanel" :active="activeTab === 1" :acknowledged="acknowledged" :flow="flowSelected" @refresh-errors="onRefresh()" @change-tab:show-errors="showErrors" />
          </TabPanel>
          <TabPanel :value="2">
            <ErrorsSummaryByMessagePanel ref="errorSummaryMessagePanel" :active="activeTab === 2" :acknowledged="acknowledged" :flow="flowSelected" @refresh-errors="onRefresh()" @change-tab:show-errors="showErrors" />
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>
  </div>
</template>

<script setup>
import AllErrorsPanel from "@/components/errors/AllPanel.vue";
import ErrorsSummaryByFlowPanel from "@/components/errors/SummaryByFlowPanel.vue";
import ErrorsSummaryByMessagePanel from "@/components/errors/SummaryByMessagePanel.vue";
import FilterSidebar from "@/components/FilterSidebar.vue";
import PageHeader from "@/components/PageHeader.vue";
import ProgressBar from "primevue/progressbar";
import useErrorCount from "@/composables/useErrorCount";
import useErrorsSummary from "@/composables/useErrorsSummary";
import useFlows from "@/composables/useFlows";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, inject, nextTick, onBeforeMount, onMounted, onUnmounted, ref, watch } from "vue";
import { StorageSerializers, useStorage, useUrlSearchParams } from "@vueuse/core";
import { useRoute, useRouter } from "vue-router";

import _ from "lodash";

import Button from "primevue/button";
import Select from "primevue/select";
import Tab from "primevue/tab";
import TabList from "primevue/tablist";
import TabPanel from "primevue/tabpanel";
import TabPanels from "primevue/tabpanels";
import Tabs from "primevue/tabs";

const errorMessageSelected = ref(null);
const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const errorSummaryMessagePanel = ref();
const errorSummaryFlowPanel = ref();
const errorsSummaryPanel = ref();
const { fetchAllFlowNames } = useFlows();
const { pluralize } = useUtilFunctions();
const { fetchErrorCountSince } = useErrorCount();
const loading = ref(false);
const newErrorsCount = ref(0);
const lastServerContact = ref(new Date());
const flowSelected = ref(null);
const activeTab = ref(0);
const isNavigating = ref(false);
const params = useUrlSearchParams("history");
const useURLSearch = ref(false);
const route = useRoute();
const router = useRouter();
const errorPanelState = useStorage("error-store", {}, sessionStorage, { serializer: StorageSerializers.object });
const { fetchUniqueErrorMessages } = useErrorsSummary();
const formattedFlows = ref([]);
const allFlowNames = ref();
const showTabs = ref(false);
const uniqueErrorMessages = ref([]);
const sidebarOpen = ref(false);

const ackOptions = [
  { name: "Errors", value: 0 },
  { name: "Acknowledged", value: 1 },
  { name: "All", value: 2 },
];
const selectedAckOption = ref(0);
const acknowledged = computed(() => {
  if (selectedAckOption.value == 0) return false;
  if (selectedAckOption.value == 1) return true;
  return null;
});
const flowTypeMap = {
  TRANSFORM: "transform",
  REST_DATA_SOURCE: "restDataSource",
  TIMED_DATA_SOURCE: "timedDataSource",
  ON_ERROR_DATA_SOURCE: "onErrorDataSource",
  DATA_SINK: "dataSink",
};

const setPersistedParams = () => {
  // Session Storage
  errorPanelState.value = {
    tabs: activeTab.value,
    ack: selectedAckOption.value,
    flowSelected: flowSelected.value,
    errorMessageSelected: errorMessageSelected.value,
  };
  // URL - use router.replace to keep route.query in sync
  const query = {
    ...route.query,
    tab: String(activeTab.value),
    ack: selectedAckOption.value > 0 ? String(selectedAckOption.value) : undefined,
    flowName: flowSelected.value?.name || undefined,
    flowType: flowSelected.value?.type || undefined,
    errorMsg: activeTab.value === 0 && errorMessageSelected.value ? encodeURIComponent(errorMessageSelected.value) : undefined,
  };
  router.replace({ query });
};

const getPersistedParams = async () => {
  if (useURLSearch.value) {
    activeTab.value = route.query.tab ? parseInt(route.query.tab) : 0;
    selectedAckOption.value = route.query.ack ? parseInt(route.query.ack) : 0;
    if (route.query.flowName && route.query.flowType) {
      flowSelected.value = {
        name: route.query.flowName,
        type: route.query.flowType,
      };
    }
    errorMessageSelected.value = route.query.errorMsg ? decodeURIComponent(route.query.errorMsg) : errorPanelState.value.errorMessageSelected;
  } else {
    activeTab.value = errorPanelState.value.tabs ? parseInt(errorPanelState.value.tabs) : 0;
    selectedAckOption.value = _.get(errorPanelState.value, "ack", 0);
    flowSelected.value = _.get(errorPanelState.value, "flowSelected", null);
    errorMessageSelected.value = _.get(errorPanelState.value, "errorMessageSelected", null);
  }
  setPersistedParams();
};

const clearOptions = () => {
  errorMessageSelected.value = null;
  flowSelected.value = null;
  selectedAckOption.value = 0;
  setPersistedParams();
};

const unSelectAllRows = () => {
  errorsSummaryPanel.value.unSelectAllRows();
  errorSummaryFlowPanel.value.unSelectAllRows();
  errorSummaryMessagePanel.value.unSelectAllRows();
};

const filterOptionsSelected = computed(() => {
  const formDirty = _.some([errorMessageSelected.value, flowSelected.value], (value) => !(value === "" || value === null || value === undefined));

  return selectedAckOption.value > 0 || formDirty;
});

const setupWatchers = () => {
  watch([activeTab, selectedAckOption, flowSelected, errorMessageSelected], () => {
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

const showErrors = (errorMessage, flowName, flowType) => {
  if (flowName && flowType) {
    flowSelected.value = { name: flowName, type: flowTypeMap[flowType] };
  }
  errorMessageSelected.value = errorMessage;
  activeTab.value = 0;
};

const refreshButtonSeverity = computed(() => {
  return newErrorsCount.value > 0 ? "warn" : undefined;
});

const refreshButtonVariant = computed(() => {
  return newErrorsCount.value > 0 ? undefined : "outlined";
});

const refreshButtonTooltip = computed(() => {
  const pluralized = pluralize(newErrorsCount.value, "error");
  return {
    value: `${pluralized} occurred since last refresh.`,
    disabled: newErrorsCount.value === 0,
  };
});

const refreshButtonBadge = computed(() => {
  return newErrorsCount.value > 0 ? newErrorsCount.value.toString() : null;
});

const refreshActiveTab = () => {
  switch (activeTab.value) {
    case 0:
      errorsSummaryPanel.value.fetchErrors();
      break;
    case 1:
      errorSummaryFlowPanel.value.fetchErrorsFlow();
      break;
    case 2:
      errorSummaryMessagePanel.value.fetchErrorsMessages();
      break;
  }
};

const onRefresh = () => {
  loading.value = true;
  newErrorsCount.value = 0;
  refreshActiveTab();
  loading.value = false;
};

const pollNewErrors = async () => {
  const count = await fetchErrorCountSince(lastServerContact.value);
  if (count > 0) {
    lastServerContact.value = new Date();
    newErrorsCount.value += count;
  }
};

let autoRefresh = null;
onUnmounted(() => {
  clearInterval(autoRefresh);
});

onBeforeMount(() => {
  useURLSearch.value = route.fullPath.includes("errors?");
});

onMounted(async () => {
  allFlowNames.value = await fetchAllFlowNames();
  formatFlowNames();
  await getPersistedParams();
  await nextTick();
  showTabs.value = true;
  pollNewErrors();
  uniqueErrorMessages.value = await fetchUniqueErrorMessages();
  autoRefresh = setInterval(() => {
    if (!isIdle.value && !loading.value) {
      pollNewErrors();
    }
  }, refreshInterval);
  setupWatchers();
});

const formatFlowNames = () => {
  const map = {
    restDataSource: "Rest Data Sources",
    timedDataSource: "Timed Data Sources",
    transform: "Transforms",
    dataSink: "Data Sinks",
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
</script>

<style>
.errors-page {
  position: relative;
  display: flex;
  height: 100%;

  .time-range .form-control:disabled,
  .time-range .form-control[readonly] {
    background-color: var(--surface-card, #ffffff);
  }

  .show-acknowledged-toggle {
    width: 14rem;
  }

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

      td.filename-column {
        overflow-wrap: anywhere;
      }
    }
  }

  .p-datatable.p-datatable-striped .p-datatable-tbody > tr.p-highlight {
    color: #ffffff;

    a,
    button {
      color: #eeeeee;
    }
  }

  tr.action-error {
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

  .p-autocomplete-empty-message {
    margin-left: 0.5rem;
  }

  .p-autocomplete-input {
    width: 16rem;
  }

  .vdpr-datepicker {
    position: relative;
  }

  .vdpr-datepicker__calendar-dialog {
    margin-left: -276px !important;
    position: fixed !important;
  }
}
</style>
