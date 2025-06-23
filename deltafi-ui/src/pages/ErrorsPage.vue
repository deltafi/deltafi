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
  <div class="errors-page">
    <PageHeader heading="Errors">
      <div class="time-range btn-toolbar mb-2 mb-md-0 align-items-center">
        <Button v-tooltip.right="{ value: `Clear Filters`, disabled: !filterOptionsSelected }" rounded :class="`mx-1 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${filterOptionsSelected ? 'p-column-filter-menu-button-active' : null}`" :disabled="!filterOptionsSelected" @click="clearOptions()">
          <i class="pi pi-filter" style="font-size: 1rem" />
        </Button>
        <Dropdown v-model="flowSelected" placeholder="Select a Flow" show-clear :options="formattedFlows" option-group-label="label" option-group-children="sources" option-label="name" :editable="false" class="deltafi-input-field mx-1 flow-dropdown" />
        <Dropdown v-model="errorMessageSelected" placeholder="Select an Error Message" show-clear :options="uniqueErrorMessages" class="deltafi-input-field mx-1 flow-dropdown" />
        <Dropdown v-model="selectedAckOption" :options="ackOptions" option-label="name" option-value="value" :editable="false" class="deltafi-input-field mx-1 ack-dropdown" />
        <Button v-tooltip.left="refreshButtonTooltip" :icon="refreshButtonIcon" label="Refresh" :class="refreshButtonClass" :badge="refreshButtonBadge" badge-class="p-badge-danger" @click="onRefresh" />
      </div>
    </PageHeader>
    <ProgressBar v-if="!showTabs" mode="indeterminate" style="height: 0.5em" />
    <TabView v-if="showTabs" v-model:active-index="activeTab" @tab-change="unSelectAllRows">
      <TabPanel header="All">
        <AllErrorsPanel ref="errorsSummaryPanel" :acknowledged="acknowledged" :flow="flowSelected" :errors-message-selected="errorMessageSelected" @refresh-errors="onRefresh()" />
      </TabPanel>
      <TabPanel header="By Flow">
        <ErrorsSummaryByFlowPanel ref="errorSummaryFlowPanel" :acknowledged="acknowledged" :flow="flowSelected" @refresh-errors="onRefresh()" @change-tab:show-errors="showErrors" />
      </TabPanel>
      <TabPanel header="By Message">
        <ErrorsSummaryByMessagePanel ref="errorSummaryMessagePanel" :acknowledged="acknowledged" :flow="flowSelected" @refresh-errors="onRefresh()" @change-tab:show-errors="showErrors" />
      </TabPanel>
    </TabView>
  </div>
</template>

<script setup>
import AllErrorsPanel from "@/components/errors/AllPanel.vue";
import ErrorsSummaryByFlowPanel from "@/components/errors/SummaryByFlowPanel.vue";
import ErrorsSummaryByMessagePanel from "@/components/errors/SummaryByMessagePanel.vue";
import PageHeader from "@/components/PageHeader.vue";
import useErrorCount from "@/composables/useErrorCount";
import useFlows from "@/composables/useFlows";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { ref, computed, onUnmounted, onMounted, inject, watch, onBeforeMount, nextTick } from "vue";
import { useStorage, StorageSerializers, useUrlSearchParams } from "@vueuse/core";
import Button from "primevue/button";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";
import { useRoute } from "vue-router";
import useErrorsSummary from "@/composables/useErrorsSummary";
import Dropdown from "primevue/dropdown";
import ProgressBar from "@/components/deprecatedPrimeVue/ProgressBar.vue";

import _ from "lodash";

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
const params = useUrlSearchParams("history");
const useURLSearch = ref(false);
const route = useRoute();
const errorPanelState = useStorage("error-store", {}, sessionStorage, { serializer: StorageSerializers.object });
const { fetchUniqueErrorMessages } = useErrorsSummary();
const formattedFlows = ref([]);
const allFlowNames = ref();
const showTabs = ref(false);
const uniqueErrorMessages = ref([]);

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
  // URL
  params.tab = activeTab.value > 0 ? activeTab.value : null;
  params.ack = selectedAckOption.value > 0 ? selectedAckOption.value : null;
  params.flowName = flowSelected.value?.name;
  params.flowType = flowSelected.value?.type;
  if (activeTab.value === 0) {
    params.errorMsg = errorMessageSelected.value ? encodeURIComponent(errorMessageSelected.value) : null;
  } else {
    params.errorMsg = null;
  }
};

const getPersistedParams = async () => {
  if (useURLSearch.value) {
    activeTab.value = params.tab ? parseInt(params.tab) : 0;
    selectedAckOption.value = params.ack ? parseInt(params.ack) : 0;
    if (params.flowName && params.flowType) {
      flowSelected.value = {
        name: params.flowName,
        type: params.flowType,
      };
    }
    errorMessageSelected.value = params.errorMsg ? decodeURIComponent(params.errorMsg) : errorPanelState.value.errorMessageSelected;
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
    setPersistedParams();
  });
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

const refreshButtonClass = computed(() => {
  const classes = ["p-button", "deltafi-input-field", "mx-1"];
  if (newErrorsCount.value > 0) {
    classes.push("p-button-warning");
  } else {
    classes.push("p-button-outlined");
  }
  return classes.join(" ");
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

const onRefresh = () => {
  loading.value = true;
  newErrorsCount.value = 0;
  errorsSummaryPanel.value.fetchErrors();
  errorSummaryFlowPanel.value.fetchErrorsFlow();
  errorSummaryMessagePanel.value.fetchErrorsMessages();
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

  .flow-dropdown {
    width: 16rem;
  }

  .ack-dropdown {
    width: 11rem;
  }
}
</style>
