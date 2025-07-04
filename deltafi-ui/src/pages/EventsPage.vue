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
  <div class="events-page">
    <PageHeader heading="Events">
      <div class="time-range btn-toolbar mb-2 mb-md-0">
        <Button class="p-button-text p-button-sm p-button-secondary" disabled>
          {{ shortTimezone() }}
        </Button>
        <CustomCalendar ref="customCalendarRef" @update:start-time-date:end-time-date="updateInputDateTime" />
        <Button class="p-button p-button-outlined deltafi-input-field ml-3" icon="fa fa-sync-alt" :loading="loading" label="Refresh" @click="refreshEventsData()" />
      </div>
    </PageHeader>
    <Panel :header="'Events' + eventCount" class="events-panel table-panel" @contextmenu="onPanelRightClick">
      <ContextMenu ref="menu" :model="menuItems" />
      <template #icons>
        <Paginator v-if="events.length > 0" :rows="pageSize" :first="pageOffset" template="CurrentPageReport FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown" current-page-report-template="{first} - {last} of {totalRecords}" :total-records="totalCount" :rows-per-page-options="[10, 20, 50, 100, 1000]" @page="onPage($event)" />
        <IconField iconPosition="left">
          <InputIcon class="pi pi-search"> </InputIcon>
          <InputText v-model="filters['global'].value" class="deltafi-input-field" placeholder="Search" />
        </IconField>
      </template>
      <DataTable ref="eventsTable" v-model:filters="filters" v-model:selection="selectedEvents" filter-display="menu" :value="events" data-key="id" responsive-layout="scroll" striped-rows class="p-datatable-sm p-datatable-gridlines" :row-hover="true" sort-field="timestamp" :sort-order="-1" :loading="loading">
        <template #empty> No events to display </template>
        <template #loading> Loading events. Please wait. </template>
        <Column selection-mode="multiple" header-style="width: 3rem" />
        <Column field="severity" header="Severity" filter-field="severity" :show-filter-match-modes="false" :filter-menu-style="{ width: '14rem' }" sortable class="severity-col">
          <template #body="{ data }">
            <EventSeverityBadge :severity="data.severity" style="width: 6rem" />
          </template>
          <template #filter="{ filterModel }">
            <div class="mb-3 font-bold">Severity Picker</div>
            <MultiSelect v-model="filterModel.value" :options="severityOptions" placeholder="Any" class="p-column-filter">
              <template #option="slotProps">
                <span>{{ slotProps.option }}</span>
              </template>
            </MultiSelect>
          </template>
        </Column>
        <Column field="summary" header="Summary" sortable class="summary-col">
          <template #body="{ data }">
            <span @click="showEvent(data)">
              <a class="cursor-pointer" style="color: black">{{ data.summary }}</a>
            </span>
          </template>
        </Column>
        <Column field="source" header="Source" filter-field="source" :show-filter-match-modes="false" :filter-menu-style="{ width: '14rem' }" sortable class="source-col">
          <template #body="{ data }">
            <Tag v-tooltip.top="'Source'" :value="data.source" class="p-tag-secondary" :rounded="true" icon="pi pi-arrow-circle-right" />
          </template>
          <template #filter="{ filterModel }">
            <div class="mb-3 font-bold">Source Picker</div>
            <MultiSelect v-model="filterModel.value" :options="sourceOptions" placeholder="Any" class="p-column-filter">
              <template #option="slotProps">
                <span>{{ slotProps.option }}</span>
              </template>
            </MultiSelect>
          </template>
        </Column>
        <Column field="timestamp" header="Timestamp" sortable class="timestamp-col">
          <template #body="{ data }">
            <Timestamp :timestamp="data.timestamp" format="YYYY-MM-DD HH:mm:ss" />
          </template>
        </Column>
        <Column field="notification" data-type="boolean" sortable class="notification-col">
          <template #header>
            <span class="font-weight-bold">
              <i v-tooltip.left="'Notification'" class="pi pi-bell">&nbsp;</i>
            </span>
          </template>
          <template #body="{ data }">
            <i v-if="data.notification" class="pi pi-check" />
          </template>
          <template #filter="{ filterModel }">
            <TriStateCheckbox v-model="filterModel.value" />
            &nbsp;&nbsp;
            <span v-if="filterModel.value">True</span>
            <span v-else-if="!filterModel.value && filterModel.value != null && filterModel.value != undefined">False</span>
            <span v-else />
          </template>
        </Column>
        <Column field="acknowledged" data-type="boolean" sortable class="acknowledged-col">
          <template #header>
            <span class="font-weight-bold">
              <i v-tooltip.left="'Acknowledged'" class="pi pi-thumbs-up">&nbsp;</i>
            </span>
          </template>
          <template #body="{ data }">
            <i v-if="data.acknowledged" class="pi pi-check" />
          </template>
          <template #filter="{ filterModel }">
            <TriStateCheckbox v-model="filterModel.value" />
            &nbsp;&nbsp;
            <span v-if="filterModel.value">Acknowledged</span>
            <span v-else-if="!filterModel.value && filterModel.value != null && filterModel.value != undefined">Unacknowledged</span>
            <span v-else />
          </template>
        </Column>
      </DataTable>
      <EventViewerDialog v-model:visible="showEventDialog" :event="activeEvent" />
    </Panel>
  </div>
</template>

<script setup>
import EventViewerDialog from "@/components/events/EventViewerDialog.vue";
import EventSeverityBadge from "@/components/events/EventSeverityBadge.vue";
import PageHeader from "@/components/PageHeader.vue";
import Timestamp from "@/components/Timestamp.vue";
import useEvents from "@/composables/useEvents";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, inject, onBeforeMount, ref, watch } from "vue";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import _ from "lodash";
import CustomCalendar from "@/components/CustomCalendar.vue";

import Button from "primevue/button";
import Column from "primevue/column";
import ContextMenu from "primevue/contextmenu";
import DataTable from "primevue/datatable";
import { FilterMatchMode } from "primevue/api";
import IconField from "primevue/iconfield";
import InputIcon from "primevue/inputicon";
import InputText from "primevue/inputtext";
import MultiSelect from "primevue/multiselect";
import Paginator from "primevue/paginator";
import Panel from "primevue/panel";
import Tag from "primevue/tag";
import TriStateCheckbox from "primevue/tristatecheckbox";

dayjs.extend(utc);

const uiConfig = inject("uiConfig");
const watchEnabled = ref(false);
const hasPermission = inject("hasPermission");
const loading = ref(true);
const selectedEvents = ref([]);
const activeEvent = ref({});
const showEventDialog = ref(false);
const customCalendarRef = ref(null);
const eventsTable = ref(null);
const { data: events, fetch: fetchEvents, acknowledgeEvent, unacknowledgeEvent, totalCount } = useEvents();
const { shortTimezone } = useUtilFunctions();
const filters = ref({
  global: { value: null, matchMode: FilterMatchMode.CONTAINS },
  severity: { value: null, matchMode: FilterMatchMode.IN },
  source: { value: null, matchMode: FilterMatchMode.IN },
  notification: { value: null, matchMode: FilterMatchMode.EQUALS },
  acknowledged: { value: null, matchMode: FilterMatchMode.EQUALS },
});

const initFilters = JSON.parse(JSON.stringify(filters.value));
const menu = ref();

const timestampFormat = "YYYY-MM-DD HH:mm:ss";
const defaultStartTimeDate = computed(() => {
  const date = dayjs().utc();
  return (uiConfig.useUTC ? date : date.local()).startOf("day");
});
const defaultEndTimeDate = computed(() => {
  const date = dayjs().utc();
  return (uiConfig.useUTC ? date : date.local()).endOf("day");
});
const startTimeDate = ref();
const endTimeDate = ref();
const setDateTimeToday = () => {
  startTimeDate.value = new Date(defaultStartTimeDate.value.format(timestampFormat));
  endTimeDate.value = new Date(defaultEndTimeDate.value.format(timestampFormat));
};

const pageSize = ref(10); // Default page size, can be adjusted as needed
const pageOffset = ref(0); // Default page number, can be adjusted as needed

const updateInputDateTime = async (startDate, endDate) => {
  startTimeDate.value = startDate;
  endTimeDate.value = endDate;
};

const eventCount = computed(() => {
  const count = _.get(eventsTable.value, "totalRecordsLength", 0);
  return " (" + count + ")";
});

const severityOptions = computed(() => {
  return _.uniq(_.map(events.value, "severity"));
});

const sourceOptions = computed(() => {
  return _.uniq(_.map(events.value, "source"));
});

const menuItems = ref([
  {
    label: "Clear Selected",
    icon: "fas fa-times fa-fw",
    command: () => {
      clearSelectedEvents();
    },
  },
  {
    label: "Select All Visible",
    icon: "fas fa-check-double fa-fw",
    command: () => {
      selectedEvents.value = events.value;
    },
  },
  {
    label: "Clear Filters",
    icon: "pi pi-filter-slash",
    command: () => {
      clearFilters();
    },
  },
  {
    separator: true,
    visible: computed(() => hasPermission("EventAcknowledge")),
  },
  {
    label: "Acknowledge Selected",
    icon: "fas fa-check-circle fa-fw",
    command: () => {
      acknowledgeEvents();
    },
    visible: computed(() => hasPermission("EventAcknowledge")),
    disabled: computed(() => selectedEvents.value.length == 0),
  },
  {
    label: "Unacknowledge Selected",
    icon: "fas fa-redo fa-fw",
    command: () => {
      unacknowledgeEvents();
    },
    visible: computed(() => hasPermission("EventAcknowledge")),
    disabled: computed(() => selectedEvents.value.length == 0),
  },
]);

const clearFilters = () => {
  filters.value = JSON.parse(JSON.stringify(initFilters));
};

const onPanelRightClick = (event) => {
  menu.value.show(event);
};

const acknowledgeEvents = async () => {
  await acknowledgeEvent(_.uniq(_.map(selectedEvents.value, "id")));
  getEvents();
  clearSelectedEvents();
};

const unacknowledgeEvents = async () => {
  await unacknowledgeEvent(_.uniq(_.map(selectedEvents.value, "id")));
  getEvents();
  clearSelectedEvents();
};

const showEvent = (event) => {
  activeEvent.value = event;
  showEventDialog.value = true;
};

const clearSelectedEvents = () => {
  selectedEvents.value = [];
};

const refreshEventsData = async () => {
  await customCalendarRef.value.refreshUpdateDateTime();
  getEvents();
};

const getEvents = async () => {
  loading.value = true;
  await fetchEvents({ start: dateToISOString(startTimeDate.value), end: dateToISOString(endTimeDate.value), size: pageSize.value, offset: pageOffset.value }, true);
  loading.value = false;
};

const dateToISOString = (dateData) => {
  return dayjs(dateData).utc(uiConfig.useUTC).toISOString();
};

const onPage = (event) => {
  pageSize.value = event.rows;
  pageOffset.value = event.first;
  getEvents();
};

onBeforeMount(() => {
  setDateTimeToday();
  watchEnabled.value = true;
  getEvents();
});

watch(startTimeDate, () => {
  if (watchEnabled.value) getEvents();
});

watch(endTimeDate, () => {
  if (watchEnabled.value) getEvents();
});
</script>

<style>
.events-page {
  .input-area-width {
    width: 335px;
  }

  .vdpr-datepicker__calendar-dialog {
    margin-left: -275px;
  }

  .vdpr-datepicker__button-reset {
    color: white;
    background-color: #dc3545;
    border-color: #d00f27;
  }

  .vdpr-datepicker__switch {
    margin-top: 6px;
  }

  .vdpr-datepicker {
    position: relative;
  }

  .events-panel {
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
        margin-right: 0.75rem;
        font-weight: 500;
      }
    }

    .p-panel-header {
      padding: 0 1.25rem;

      .p-panel-icons {
        display: flex;
      }

      .p-panel-title {
        padding: 1rem 0;
      }

      .p-panel-header-icon {
        margin-top: 0.25rem;
        margin-right: 0;
        background-color: #d00f27;
      }
    }

    td.severity-col,
    td.source-col,
    td.notification-col,
    td.acknowledged-col {
      width: 1rem;
    }

    td.severity-col {
      padding: 0 0.5rem !important;
    }

    td.timestamp-col {
      font-size: 90%;
      width: 12rem;
    }
  }
}
</style>