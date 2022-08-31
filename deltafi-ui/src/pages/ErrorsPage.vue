<!--
   DeltaFi - Data transformation and enrichment platform

   Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>

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
  <div class="errors">
    <PageHeader heading="Errors">
      <div class="time-range btn-toolbar mb-2 mb-md-0">
        <Dropdown v-model="ingressFlowNameSelected" placeholder="Select an Ingress Flow" :options="ingressFlowNames" option-label="name" show-clear :editable="false" class="deltafi-input-field ml-3" />
        <Button v-model="showAcknowledged" :icon="showAcknowledged ? 'fas fa-eye-slash' : 'fas fa-eye'" :label="showAcknowledged ? 'Hide Acknowledged' : 'Show Acknowledged'" class="p-button p-button-secondary p-button-outlined deltafi-input-field show-acknowledged-toggle ml-3" @click="toggleShowAcknowledged()" />
        <Button v-tooltip.left="refreshButtonTooltip" :icon="refreshButtonIcon" label="Refresh" :class="refreshButtonClass" :badge="refreshButtonBadge" badge-class="p-badge-danger" @click="onRefresh" />
      </div>
    </PageHeader>
    <TabView>
      <TabPanel header="All">
        <AllErrorsPanel ref="errorsSummaryPanel" :awknowledged="showAcknowledged" :ingress-flow-name="ingressFlowNameSelected" @refresh-errors="onRefresh()" />
      </TabPanel>
      <TabPanel header="By Flow">
        <ErrorsSummaryByFlowPanel ref="errorSummaryFlowPanel" :awknowledged="showAcknowledged" :ingress-flow-name="ingressFlowNameSelected" @refresh-errors="onRefresh()" />
      </TabPanel>
      <TabPanel header="By Message">
        <ErrorsSummaryByMessagePanel ref="errorSummaryMessagePanel" :awknowledged="showAcknowledged" :ingress-flow-name="ingressFlowNameSelected" @refresh-errors="onRefresh()" />
      </TabPanel>
    </TabView>
  </div>
</template>

<script setup>
import Dropdown from "primevue/dropdown";
import Button from "primevue/button";
import PageHeader from "@/components/PageHeader.vue";
import useErrorCount from "@/composables/useErrorCount";
import useFlows from "@/composables/useFlows";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { ref, computed, onUnmounted, onMounted, inject } from "vue";
import TabPanel from "primevue/tabpanel";
import TabView from "primevue/tabview";
import AllErrorsPanel from "@/components/errors/AllPanel.vue";
import ErrorsSummaryByFlowPanel from "@/components/errors/SummaryByFlowPanel.vue";
import ErrorsSummaryByMessagePanel from "@/components/errors/SummaryByMessagePanel.vue";

const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");
const errorSummaryMessagePanel = ref();
const errorSummaryFlowPanel = ref();
const errorsSummaryPanel = ref();
const { ingressFlows: ingressFlowNames, fetchIngressFlows } = useFlows();
const { pluralize } = useUtilFunctions();
const { fetchErrorCountSince } = useErrorCount();
const loading = ref(false);
const newErrorsCount = ref(0);
const lastServerContact = ref(new Date());
const showAcknowledged = ref(false);
const ingressFlowNameSelected = ref(null);
const selectedErrors = ref([]);

const refreshButtonIcon = computed(() => {
  let classes = ["fa", "fa-sync-alt"];
  if (loading.value) classes.push("fa-spin");
  return classes.join(" ");
});

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

fetchIngressFlows();

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

onMounted(async () => {
  pollNewErrors();
  autoRefresh = setInterval(() => {
    if (!isIdle.value && !loading.value) {
      pollNewErrors();
    }
  }, refreshInterval);
});
</script>

<style lang="scss">
@import "@/styles/pages/errors-page.scss";
</style>
