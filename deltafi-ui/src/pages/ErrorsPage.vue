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
  <div class="errors-page">
    <PageHeader heading="Errors">
      <div class="time-range btn-toolbar mb-2 mb-md-0 align-items-center">
        <Button v-tooltip.right="{ value: `Clear Filters`, disabled: !filterOptionsSelected }" rounded :class="`mr-0 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${filterOptionsSelected ? 'p-column-filter-menu-button-active' : null}`" :disabled="!filterOptionsSelected" @click="clearOptions()">
          <i class="pi pi-filter" style="font-size: 1rem"></i>
        </Button>
        <Dropdown v-model="dataSourceNameSelected" placeholder="Select a Data Source" :options="dataSourceFlowNames" show-clear :editable="false" class="deltafi-input-field flow-dropdown mx-1" />
        <AutoComplete v-model="selectedMessageValue" :suggestions="filteredErrorMessages" placeholder="Select Last Error" class="deltafi-input-field mx-1" force-selection @complete="messageSearch" />
        <Dropdown v-model="selectedAckOption" :options="ackOptions" option-label="name" option-value="value" :editable="false" class="deltafi-input-field ack-dropdown mx-1" />
        <Button v-tooltip.left="refreshButtonTooltip" :icon="refreshButtonIcon" label="Refresh" :class="refreshButtonClass" :badge="refreshButtonBadge" badge-class="p-badge-danger" @click="onRefresh" />
      </div>
    </PageHeader>
    <TabView v-model:activeIndex="activeTab">
      <TabPanel header="All">
        <AllErrorsPanel ref="errorsSummaryPanel" :acknowledged="acknowledged" :data-source-name="dataSourceNameSelected" :errors-message-selected="errorMessageSelected" @refresh-errors="onRefresh()" @error-message-changed:error-message="messageSelected" />
      </TabPanel>
      <TabPanel header="By Data Source">
        <ErrorsSummaryByFlowPanel ref="errorSummaryFlowPanel" :acknowledged="acknowledged" :data-source-flow-name="dataSourceNameSelected" @refresh-errors="onRefresh()" />
      </TabPanel>
      <TabPanel header="By Message">
        <ErrorsSummaryByMessagePanel ref="errorSummaryMessagePanel" :acknowledged="acknowledged" :data-source-flow-name="dataSourceNameSelected" @refresh-errors="onRefresh()" @change-tab:error-message:flow-selected="tabChange" />
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
import AutoComplete from "primevue/autocomplete";
import useErrorsSummary from "@/composables/useErrorsSummary";
import Dropdown from "primevue/dropdown";

const messageValues = ref();
const filteredErrorMessages = ref([]);
const selectedMessageValue = ref("");
const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const errorSummaryMessagePanel = ref();
const errorSummaryFlowPanel = ref();
const errorsSummaryPanel = ref();
const { dataSourceFlows: dataSourceFlowNames, fetchDataSourceFlowNames } = useFlows();
const { pluralize } = useUtilFunctions();
const { fetchErrorCountSince } = useErrorCount();
const loading = ref(false);
const newErrorsCount = ref(0);
const lastServerContact = ref(new Date());
const dataSourceNameSelected = ref(null);
const errorMessageSelected = ref("");
const activeTab = ref(0);
const params = useUrlSearchParams("history");
const useURLSearch = ref(false);
const route = useRoute();
const errorPanelState = useStorage("error-store", {}, sessionStorage, { serializer: StorageSerializers.object });
const { data: errorsMessages, fetchAllMessage: getAllErrorsMessage } = useErrorsSummary();

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

const setPersistedParams = () => {
  // Session Storage
  errorPanelState.value = {
    tabs: activeTab.value,
    ack: selectedAckOption.value,
    dataSourceNameSelected: dataSourceNameSelected.value,
    errorMessageSelected: errorMessageSelected.value,
  };
  // URL
  params.tab = activeTab.value > 0 ? activeTab.value : null;
  params.ack = selectedAckOption.value > 0 ? selectedAckOption.value : null;
  params.dataSource = dataSourceNameSelected.value;
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
    dataSourceNameSelected.value = params.dataSource ? params.dataSource : null;
    selectedMessageValue.value = errorMessageSelected.value = params.errorMsg ? decodeURIComponent(params.errorMsg) : errorPanelState.value.errorMessageSelected;
  } else {
    activeTab.value = errorPanelState.value.tabs ? parseInt(errorPanelState.value.tabs) : 0;
    selectedAckOption.value = _.get(errorPanelState.value, "ack", 0);
    dataSourceNameSelected.value = _.get(errorPanelState.value, "dataSourceNameSelected", null);
    selectedMessageValue.value = errorMessageSelected.value = _.get(errorPanelState.value, "errorMessageSelected", null);
  }
  setPersistedParams();
};

const messageSearch = (event) => {
  setTimeout(() => {
    if (!event.query.trim().length) {
      filteredErrorMessages.value = [...messageValues.value];
    } else {
      filteredErrorMessages.value = messageValues.value.filter((message) => {
        return message.toLowerCase().includes(event.query.toLowerCase());
      });
    }
  }, 1000);
};
const clearOptions = () => {
  filteredErrorMessages.value = [];
  selectedMessageValue.value = "";
  errorMessageSelected.value = "";
  dataSourceNameSelected.value = null;
  selectedAckOption.value = 0;
  setPersistedParams();
};

const filterOptionsSelected = computed(() => {
  const formDirty = _.some([selectedMessageValue.value, dataSourceNameSelected.value], (value) => !(value === "" || value === null || value === undefined));

  return selectedAckOption.value > 0 || formDirty;
});

const setupWatchers = () => {
  watch([activeTab, selectedAckOption, dataSourceNameSelected, errorMessageSelected], () => {
    setPersistedParams();
  });
  watch([selectedMessageValue], () => {
    errorMessageSelected.value = selectedMessageValue.value;
  });
};

const refreshButtonIcon = computed(() => {
  let classes = ["fa", "fa-sync-alt"];
  if (loading.value) classes.push("fa-spin");
  return classes.join(" ");
});

const tabChange = (errorMessage, flowSelected) => {
  dataSourceNameSelected.value = flowSelected;
  errorMessageSelected.value = errorMessage;
  selectedMessageValue.value = errorMessage;
  activeTab.value = 0;
};

const messageSelected = (errorMsg) => {
  if (errorMessageSelected.value !== errorMsg) errorMessageSelected.value = errorMsg;
};

const refreshButtonClass = computed(() => {
  let classes = ["p-button", "deltafi-input-field", "ml-1"];
  if (newErrorsCount.value > 0) {
    classes.push("p-button-warning");
  } else {
    classes.push("p-button-outlined");
  }
  return classes.join(" ");
});

const refreshButtonTooltip = computed(() => {
  let pluralized = pluralize(newErrorsCount.value, "error");
  return {
    value: `${pluralized} occurred since last refresh.`,
    disabled: newErrorsCount.value === 0,
  };
});

const refreshButtonBadge = computed(() => {
  return newErrorsCount.value > 0 ? newErrorsCount.value.toString() : null;
});

fetchDataSourceFlowNames();

const onRefresh = () => {
  loading.value = true;
  newErrorsCount.value = 0;
  errorsSummaryPanel.value.fetchErrors();
  errorSummaryFlowPanel.value.fetchErrorsFlow();
  errorSummaryMessagePanel.value.fetchErrorsMessages();
  loading.value = false;
};

const pollNewErrors = async () => {
  let count = await fetchErrorCountSince(lastServerContact.value);
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
  await getPersistedParams();
  await nextTick();
  pollNewErrors();
  await getAllErrorsMessage();
  const uniqueMessages = [];
  for (let i = 0; i < errorsMessages.value.length; i++) {
    if (!uniqueMessages.includes(errorsMessages.value[i].message)) {
      uniqueMessages.push(errorsMessages.value[i].message);
    }
  }
  messageValues.value = uniqueMessages;
  autoRefresh = setInterval(() => {
    if (!isIdle.value && !loading.value) {
      pollNewErrors();
    }
  }, refreshInterval);
  setupWatchers();
});
</script>

<style lang="scss">
.errors-page {
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

@import "@/styles/pages/errors-page.scss";
</style>