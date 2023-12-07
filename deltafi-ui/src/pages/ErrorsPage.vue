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
      <div class="time-range btn-toolbar mb-2 mb-md-0">
        <Button v-tooltip.right="{ value: `Clear Filters`, disabled: !filterOptionsSelected }" rounded :class="`ml-2 p-column-filter-menu-button p-link p-column-filter-menu-button-open ${filterOptionsSelected ? 'p-column-filter-menu-button-active' : null}`" :disabled="!filterOptionsSelected" @click="clearOptions()">
          <i class="pi pi-filter" style="font-size: 1rem"></i>
        </Button>
        <Dropdown v-model="ingressFlowNameSelected" placeholder="Select an Ingress Flow" :options="ingressFlowNames" show-clear :editable="false" class="deltafi-input-field ml-3 flow-dropdown" />
        <AutoComplete v-model="selectedMessageValue" :suggestions="filteredErrorMessages" placeholder="Select Last Error" class="deltafi-input-field ml-3" force-selection @complete="messageSearch" />
        <Button v-model="showAcknowledged" :icon="showAcknowledged ? 'fas fa-eye-slash' : 'fas fa-eye'" :label="showAcknowledged ? 'Hide Acknowledged' : 'Show Acknowledged'" class="p-button p-button-secondary p-button-outlined deltafi-input-field show-acknowledged-toggle ml-3" @click="toggleShowAcknowledged()" />
        <Button v-tooltip.left="refreshButtonTooltip" :icon="refreshButtonIcon" label="Refresh" :class="refreshButtonClass" :badge="refreshButtonBadge" badge-class="p-badge-danger" @click="onRefresh" />
      </div>
    </PageHeader>
    <TabView v-model:activeIndex="activeTab">
      <TabPanel header="All">
        <AllErrorsPanel ref="errorsSummaryPanel" :acknowledged="showAcknowledged" :ingress-flow-name="ingressFlowNameSelected" :errors-message-selected="errorMessageSelected" @refresh-errors="onRefresh()" @error-message-changed:error-message="messageSelected" />
      </TabPanel>
      <TabPanel header="By Flow">
        <ErrorsSummaryByFlowPanel ref="errorSummaryFlowPanel" :acknowledged="showAcknowledged" :ingress-flow-name="ingressFlowNameSelected" @refresh-errors="onRefresh()" />
      </TabPanel>
      <TabPanel header="By Message">
        <ErrorsSummaryByMessagePanel ref="errorSummaryMessagePanel" :acknowledged="showAcknowledged" :ingress-flow-name="ingressFlowNameSelected" @refresh-errors="onRefresh()" @change-tab:error-message:flow-selected="tabChange" />
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
import Dropdown from "primevue/dropdown";
import Button from "primevue/button";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";
import { useRoute } from "vue-router";
import AutoComplete from "primevue/autocomplete";
import useErrorsSummary from "@/composables/useErrorsSummary";

const messageValues = ref();
const filteredErrorMessages = ref([]);
const selectedMessageValue = ref("");
const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const errorSummaryMessagePanel = ref();
const errorSummaryFlowPanel = ref();
const errorsSummaryPanel = ref();
const { ingressFlows: ingressFlowNames, fetchIngressFlowNames } = useFlows();
const { pluralize } = useUtilFunctions();
const { fetchErrorCountSince } = useErrorCount();
const loading = ref(false);
const newErrorsCount = ref(0);
const lastServerContact = ref(new Date());
const showAcknowledged = ref(false);
const ingressFlowNameSelected = ref(null);
const errorMessageSelected = ref("");
const selectedErrors = ref([]);
const activeTab = ref(0);
const params = useUrlSearchParams("history");
const useURLSearch = ref(false);
const route = useRoute();
const errorPanelState = useStorage("error-store", {}, sessionStorage, { serializer: StorageSerializers.object });
const { data: errorsMessages, fetchAllMessage: getAllErrorsMessage } = useErrorsSummary();

const setPersistedParams = () => {
  // Session Storage
  errorPanelState.value = {
    tabs: activeTab.value,
    showAcknowledged: showAcknowledged.value,
    ingressFlowNameSelected: ingressFlowNameSelected.value,
    errorMessageSelected: errorMessageSelected.value,
  };
  // URL
  params.tab = activeTab.value;
  params.showAck = showAcknowledged.value ? "true" : null;
  params.ingressFlow = ingressFlowNameSelected.value;
  if (activeTab.value === 0) {
    params.errorMsg = errorMessageSelected.value ? encodeURIComponent(errorMessageSelected.value) : null;
  } else {
    params.errorMsg = null;
  }
};

const getPersistedParams = async () => {
  if (useURLSearch.value) {
    activeTab.value = params.tab ? parseInt(params.tab) : 0;
    showAcknowledged.value = params.showAck === "true" ? true : false;
    ingressFlowNameSelected.value = params.ingressFlow ? params.ingressFlow : null;
    selectedMessageValue.value = errorMessageSelected.value = params.errorMsg ? decodeURIComponent(params.errorMsg) : errorPanelState.value.errorMessageSelected;
  } else {
    activeTab.value = errorPanelState.value.tabs ? parseInt(errorPanelState.value.tabs) : 0;
    showAcknowledged.value = _.get(errorPanelState.value, "showAcknowledged", false);
    ingressFlowNameSelected.value = _.get(errorPanelState.value, "ingressFlowNameSelected", null);
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
  ingressFlowNameSelected.value = null;
  setPersistedParams();
};

const filterOptionsSelected = computed(() => {
  return _.some([
    selectedMessageValue.value,
    ingressFlowNameSelected.value,
  ], (value) => !(value === "" || value === null || value === undefined))
});

const setupWatchers = () => {
  watch([activeTab, showAcknowledged, ingressFlowNameSelected, errorMessageSelected], () => {
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
  ingressFlowNameSelected.value = flowSelected;
  errorMessageSelected.value = errorMessage;
  selectedMessageValue.value = errorMessage;
  activeTab.value = 0;
};

const messageSelected = (errorMsg) => {
  if (errorMessageSelected.value !== errorMsg) errorMessageSelected.value = errorMsg;
};

const refreshButtonClass = computed(() => {
  let classes = ["p-button", "deltafi-input-field", "ml-3"];
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

fetchIngressFlowNames();

const toggleShowAcknowledged = () => {
  showAcknowledged.value = !showAcknowledged.value;
  selectedErrors.value = [];
};

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
}

@import "@/styles/pages/errors-page.scss";
</style>
