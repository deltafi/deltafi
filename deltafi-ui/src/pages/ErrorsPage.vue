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
        <Dropdown v-model="ingressFlowNameSelected" placeholder="Select an Ingress Flow" :options="ingressFlowNames" option-label="name" show-clear :editable="false" class="deltafi-input-field ml-3" @change="fetchErrors()" />
        <Button v-model="showAcknowledged" :icon="showAcknowledged ? 'fas fa-eye-slash' : 'fas fa-eye'" :label="showAcknowledged ? 'Hide Acknowledged' : 'Show Acknowledged'" class="p-button p-button-secondary p-button-outlined deltafi-input-field show-acknowledged-toggle ml-3" @click="toggleShowAcknowledged()" />
        <Button v-tooltip.left="refreshButtonTooltip" :icon="refreshButtonIcon" label="Refresh" :class="refreshButtonClass" :badge="refreshButtonBadge" badge-class="p-badge-danger" @click="onRefresh" />
      </div>
    </PageHeader>
    <ConfirmDialog />
    <Panel header="DeltaFiles with Errors" @contextmenu="onPanelRightClick">
      <ContextMenu ref="menu" :model="menuItems" />
      <template #icons>
        <Button class="p-panel-header-icon p-link p-mr-2" @click="toggleMenu">
          <span class="fas fa-bars" />
        </Button>
        <Menu ref="menu" :model="menuItems" :popup="true" />
        <Paginator v-if="errors.length > 0" :rows="10" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalErrors" :rows-per-page-options="[10, 20, 50, 100, 1000]" class="p-panel-header" style="float: left" @page="onPage($event)"></Paginator>
      </template>
      <DataTable id="errorsTable" v-model:expandedRows="expandedRows" v-model:selection="selectedErrors" responsive-layout="scroll" selection-mode="multiple" data-key="did" class="p-datatable-gridlines p-datatable-sm" striped-rows :meta-key-selection="false" :value="errors" :loading="loading" :rows="perPage" :lazy="true" :total-records="totalErrors" :row-hover="true" @sort="onSort($event)">
        <template #empty>No DeltaFiles with Errors to display.</template>
        <template #loading>Loading DeltaFiles with Errors. Please wait.</template>
        <Column class="expander-column" :expander="true" />
        <Column field="did" header="DID">
          <template #body="error">
            <router-link class="monospace" :to="{ path: '/deltafile/viewer/' + error.data.did }">{{ error.data.did }}</router-link>
            <ErrorAcknowledgedBadge v-if="error.data.errorAcknowledged" :reason="error.data.errorAcknowledgedReason" :timestamp="error.data.errorAcknowledged" class="ml-2" />
          </template>
        </Column>
        <Column field="sourceInfo.filename" header="Filename" :sortable="true" class="filename-column" />
        <Column field="sourceInfo.flow" header="Flow" :sortable="true" />
        <Column field="created" header="Created" :sortable="true">
          <template #body="row">
            <Timestamp :timestamp="row.data.created" />
          </template>
        </Column>
        <Column field="modified" header="Modified" :sortable="true">
          <template #body="row">
            <Timestamp :timestamp="row.data.modified" />
          </template>
        </Column>
        <Column field="last_error_cause" header="Last Error">
          <template #body="error">{{ latestError(error.data.actions).errorCause }}</template>
        </Column>
        <Column field="actions.length" header="Errors">
          <template #body="error">{{ countErrors(error.data.actions) }}</template>
        </Column>
        <template #expansion="error">
          <div class="errors-Subtable">
            <DataTable responsive-layout="scroll" :value="error.data.actions" :row-hover="false" striped-rows class="p-datatable-sm p-datatable-gridlines" :row-class="actionRowClass" @row-click="actionRowClick">
              <Column field="name" header="Action" />
              <Column field="state" header="State" />
              <Column field="created" header="Created">
                <template #body="row">
                  <Timestamp :timestamp="row.data.created" />
                </template>
              </Column>
              <Column field="modified" header="Modified">
                <template #body="row">
                  <Timestamp :timestamp="row.data.modified" />
                </template>
              </Column>
              <Column field="errorCause" header="Error Cause">
                <template #body="action">
                  <span v-if="['ERROR', 'RETRIED'].includes(action.data.state) && action.data.errorCause !== null">{{ action.data.errorCause }}</span>
                  <span v-else>N/A</span>
                </template>
              </Column>
            </DataTable>
          </div>
        </template>
      </DataTable>
    </Panel>
    <ErrorViewer v-model:visible="errorViewer.visible" :action="errorViewer.action" />
    <AcknowledgeErrorsDialog v-model:visible="ackErrorsDialog.visible" :dids="ackErrorsDialog.dids" @acknowledged="onAcknowledged" />
  </div>
</template>

<script setup>
import Column from "primevue/column";
import DataTable from "primevue/datatable";
import Dropdown from "primevue/dropdown";
import Button from "primevue/button";
import Panel from "primevue/panel";
import Menu from "primevue/menu";
import ConfirmDialog from "primevue/confirmdialog";
import ContextMenu from "primevue/contextmenu";
import { useConfirm } from "primevue/useconfirm";
import ErrorViewer from "@/components/ErrorViewer.vue";
import AcknowledgeErrorsDialog from "@/components/AcknowledgeErrorsDialog.vue";
import ErrorAcknowledgedBadge from "@/components/ErrorAcknowledgedBadge.vue";
import PageHeader from "@/components/PageHeader.vue";
import Paginator from "primevue/paginator";
import Timestamp from "@/components/Timestamp.vue";
import useErrors from "@/composables/useErrors";
import useErrorCount from "@/composables/useErrorCount";
import useErrorRetry from "@/composables/useErrorRetry";
import useFlows from "@/composables/useFlows";
import useNotifications from "@/composables/useNotifications";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { ref, computed, onUnmounted, onMounted, inject } from "vue";

const maxRetrySuccessDisplay = 10;
const refreshInterval = 5000; // 5 seconds
const isIdle = inject("isIdle");

const { ingressFlows: ingressFlowNames, fetchIngressFlows } = useFlows();
const { pluralize } = useUtilFunctions();
const { fetchErrorCount, fetchErrorCountSince } = useErrorCount();
const { retry } = useErrorRetry();
const confirm = useConfirm();
const notify = useNotifications();
const loading = ref(true);
const menu = ref();
const errors = ref([]);
const newErrorsCount = ref(0);
const lastServerContact = ref(new Date());
const showAcknowledged = ref(false);
const ingressFlowNameSelected = ref(null);
const expandedRows = ref([]);
const totalErrors = ref(0);
const offset = ref(0);
const perPage = ref(10);
const sortField = ref("modified");
const sortDirection = ref("DESC");
const selectedErrors = ref([]);
const ackErrorsDialog = ref({
  dids: [],
  visible: false,
});
const errorViewer = ref({
  visible: false,
  action: {},
});
const menuItems = ref([
  {
    label: "Clear Selected",
    icon: "fas fa-times fa-fw",
    command: () => {
      selectedErrors.value = [];
    },
  },
  {
    label: "Select All Visible",
    icon: "fas fa-check-double fa-fw",
    command: () => {
      selectedErrors.value = errors.value;
    },
  },
  {
    separator: true,
  },
  {
    label: "Acknowledge Selected",
    icon: "fas fa-check-circle fa-fw",
    command: () => {
      acknowledgeClickConfirm();
    },
    disabled: () => {
      return selectedErrors.value.length == 0;
    },
  },
  {
    label: "Retry Selected",
    icon: "fas fa-redo fa-fw",
    command: () => {
      RetryClickConfirm();
    },
    disabled: () => {
      return selectedErrors.value.length == 0;
    },
  },
]);

const { data: response, fetch: getErrors } = useErrors();

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

const fetchErrors = async () => {
  lastServerContact.value = new Date();
  newErrorsCount.value = 0;
  let ingressFlowName = ingressFlowNameSelected.value != null ? ingressFlowNameSelected.value.name : null;
  let showAcknowled = showAcknowledged.value ? null : false;
  loading.value = true;
  await getErrors(showAcknowled, offset.value, perPage.value, sortField.value, sortDirection.value, ingressFlowName);
  errors.value = response.value.deltaFiles.deltaFiles;
  totalErrors.value = response.value.deltaFiles.totalCount;
  loading.value = false;
};

fetchIngressFlows();

const toggleMenu = (event) => {
  menu.value.toggle(event);
};
const toggleShowAcknowledged = () => {
  showAcknowledged.value = !showAcknowledged.value;
  selectedErrors.value = [];
  fetchErrors();
};
const onPanelRightClick = (event) => {
  menu.value.show(event);
};

const acknowledgeClickConfirm = () => {
  ackErrorsDialog.value.dids = selectedErrors.value.map((selectedError) => {
    return selectedError.did;
  });
  ackErrorsDialog.value.visible = true;
};
const onAcknowledged = (dids, reason) => {
  selectedErrors.value = [];
  ackErrorsDialog.value.dids = [];
  ackErrorsDialog.value.visible = false;
  let pluralized = pluralize(dids.length, "Error");
  notify.success(`Successfully acknowledged ${pluralized}`, reason);
  fetchErrorCount();
  fetchErrors();
};
const RetryClickConfirm = () => {
  let dids = selectedErrors.value.map((selectedError) => {
    return selectedError.did;
  });
  let pluralized = pluralize(dids.length, "DeltaFile");
  let pluralizedErrors = pluralize(dids.length, "Error");
  confirm.require({
    header: `Retry ${pluralizedErrors}`,
    message: `Are you sure you want to retry ${pluralized}?`,
    accept: () => {
      RetryClickAction(dids);
    },
  });
};
const RetryClickAction = async (dids) => {
  loading.value = true;
  try {
    const response = await retry(dids);
    const result = response.value.data.retry.find((r) => {
      return r.did == dids.value;
    });
    if (response.value.data !== undefined && response.value.data !== null) {
      let successRetry = new Array();
      for (const retryStatus of response.value.data.retry) {
        if (retryStatus.success) {
          successRetry.push(retryStatus);
        } else {
          notify.error(`Retry request failed for ${retryStatus.did}`, retryStatus.error);
        }
      }
      if (successRetry.length > 0) {
        let successfulDids = successRetry.map((retryStatus) => {
          return retryStatus.did;
        });
        if (successfulDids.length > maxRetrySuccessDisplay) {
          successfulDids = successfulDids.slice(0, maxRetrySuccessDisplay);
          successfulDids.push("...");
        }
        let pluralized = pluralize(dids.length, "DeltaFile");
        notify.success(`Retry request sent successfully for ${pluralized}`, successfulDids.join(", "));
      }
      fetchErrors();
      selectedErrors.value = [];
      fetchErrorCount();
      loading.value = false;
    } else {
      throw Error(result.error);
    }
  } catch (error) {
    notify.error("Retry request failed", error);
  }
};

const filterErrors = (actions) => {
  return actions.filter((action) => {
    return action.state === "ERROR";
  });
};

const latestError = (actions) => {
  return filterErrors(actions).sort((a, b) => (a.modified < b.modified ? 1 : -1))[0];
};

const countErrors = (actions) => {
  return filterErrors(actions).length;
};

const actionRowClass = (action) => {
  if (action.state === "ERROR") return "table-danger action-error";
  if (action.state === "RETRIED") return "table-warning action-error";
};

const actionRowClick = (event) => {
  let action = event.data;
  if (["ERROR", "RETRIED"].includes(action.state)) {
    errorViewer.value.action = action;
    errorViewer.value.visible = true;
  }
};

const onPage = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  fetchErrors();
};

const onSort = (event) => {
  offset.value = event.first;
  perPage.value = event.rows;
  sortField.value = event.sortField;
  sortDirection.value = event.sortOrder > 0 ? "DESC" : "ASC";
  fetchErrors();
};

const onRefresh = () => {
  fetchErrors();
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
  await fetchErrors();
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
