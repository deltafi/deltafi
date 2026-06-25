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

<template class="time-range btn-toolbar mb-2 mb-md-0">
  <div class="date-picker-container">
    <input :value="formattedValue" placeholder="Select Date" aria-label="Date range" class="p-inputtext p-component deltafi-input-field input-area-width" readonly @click="showCalendar" />
    <input v-show="showRefreshRange" type="text" :value="helperButtonText" placeholder="Select Date" aria-label="Refresh range" class="p-inputtext p-component deltafi-input-field input-area-width refresh-range-input" readonly @click="showCalendar()" />
    <CalendarDialog v-if="showCalendarDialog" :key="computedKey" ref="calendarDialogRef" :date-input="calendarDateInput" :calendar-date-input="calendarDateInput" switch-button-label="All Day" :format="timestampFormat" :same-date-format="sameDateFormat" :initial-dates="[new Date(startTimeDate), new Date(endTimeDate)]" :show-helper-buttons="true" :helper-buttons="helperButtons" class="test" @on-apply="updateInputDateTime" @on-reset="resetDateTime(true)" @select-date="dateSelected" />
  </div>
</template>

<script setup>
import { CalendarDialog } from "vue-time-date-range-picker";
import { computed, inject, onBeforeMount, onMounted, onBeforeUnmount, ref, watch } from "vue";
import { useNow } from "@vueuse/core";
import _ from "lodash";

import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

const emit = defineEmits(["update:startTimeDate:endTimeDate"]);
const now = useNow();
const newNow = ref(now.value);
const uiConfig = inject("uiConfig");
const calendarDialogRef = ref(null);
const timestampFormat = "YYYY-MM-DD HH:mm:ss";
const startTimeDate = ref();
const endTimeDate = ref();
const randomKey = ref(Math.random());
const helperButtonSelected = ref(false);
const tmpHelperButtonSelected = ref(false);
const showCalendarDialog = ref(false);

const props = defineProps({
  startTimeDate: {
    type: Date,
    required: false,
    default: null,
  },
  endTimeDate: {
    type: Date,
    required: false,
    default: null,
  },
  resetDefault: {
    type: Array,
    required: false,
    default: null,
  },
});

onBeforeMount(() => {
  setDateTimeToday();
});

watch(now, () => {
  newNow.value = uiConfig.useUTC ? now.value.getTime() + now.value.getTimezoneOffset() * 60000 : now.value;
});

const formattedValue = computed(() => {
  return `${dayjs(new Date(startTimeDate.value)).format(timestampFormat)}  -  ${dayjs(new Date(endTimeDate.value)).format(timestampFormat)}`;
});

// Keep the displayed range in sync when the bound dates change externally
watch(
  () => [props.startTimeDate, props.endTimeDate],
  ([newStart, newEnd]) => {
    if (newStart) startTimeDate.value = newStart;
    if (newEnd) endTimeDate.value = newEnd;

    const resetDefault = props.resetDefault;
    const isDefaultRange = resetDefault && newStart && newEnd && newStart.getTime() === resetDefault[0].getTime() && newEnd.getTime() === resetDefault[1].getTime();
    if (isDefaultRange) {
      tmpHelperButtonSelected.value = false;
      helperButtonSelected.value = false;
      helperButtonText.value = null;
    }
  }
);

const defaultStartTimeDate = computed(() => {
  const date = dayjs().utc();
  return props.startTimeDate || (uiConfig.useUTC ? date : date.local()).startOf("day");
});

const defaultEndTimeDate = computed(() => {
  const date = dayjs().utc();
  return props.endTimeDate || (uiConfig.useUTC ? date : date.local()).endOf("day");
});

const setDateTimeToday = () => {
  startTimeDate.value = props.startTimeDate || new Date(defaultStartTimeDate.value.format(timestampFormat));
  endTimeDate.value = props.endTimeDate || new Date(defaultEndTimeDate.value.format(timestampFormat));
};

const sameDateFormat = {
  from: timestampFormat,
  to: timestampFormat,
};

const resetDateTime = async (value) => {
  tmpHelperButtonSelected.value = false;
  helperButtonSelected.value = false;
  helperButtonText.value = null;
  if (props.resetDefault) {
    emit("update:startTimeDate:endTimeDate", props.resetDefault[0], props.resetDefault[1]);
  } else if (props.startTimeDate) {
    emit("update:startTimeDate:endTimeDate", props.startTimeDate, props.endTimeDate);
  } else {
    emit("update:startTimeDate:endTimeDate", new Date(defaultStartTimeDate.value.format(timestampFormat)), new Date(defaultEndTimeDate.value.format(timestampFormat)));
  }
  randomKey.value = Math.random();
  if (value) {
    showCalendar();
  }
};

const computedKey = computed(() => {
  return randomKey.value;
});

const showRefreshRange = computed(() => {
  if (helperButtonSelected.value) {
    return true;
  }
  return false;
});

const calendarDateInput = {
  labelStarts: "Start",
  labelEnds: "End",
  inputClass: null,
  format: "YYYY-MM-DD",
};

const updateInputDateTime = async (startDate, endDate) => {
  helperButtonSelected.value = tmpHelperButtonSelected.value ? true : false;
  helperButtonText.value = helperButtonSelected.value ? tmpHelperButtonText.value : null;
  startTimeDate.value = startDate;
  endTimeDate.value = endDate;
  showCalendar();
  emit("update:startTimeDate:endTimeDate", startDate, endDate);
};

const tempEndDate = ref(null);

const helperButtonText = ref(null);
const tmpHelperButtonText = ref(null);

// Native event delegation handlers (replacing jQuery delegated events)
const onBodyClick = (e) => {
  // Calendar table row click: set end date to end of day
  if (e.target.closest("table.vdpr-datepicker__calendar-table > tbody > tr")) {
    calendarDialogRef.value.selectedEndDate = dayjs(tempEndDate.value).endOf("day").toDate();
  }

  // Helper button click: capture button text for display
  const helperBtn = e.target.closest("button.vdpr-datepicker__button.vdpr-datepicker__button--block.vdpr-datepicker__button-default");
  if (helperBtn) {
    tmpHelperButtonSelected.value = true;
    tmpHelperButtonText.value = helperBtn.textContent;
  }

  // Time control input click: reset helper button selection
  if (e.target.closest(".vdpr-datepicker__calendar-input-time-control")) {
    tmpHelperButtonSelected.value = false;
  }
};

// All-day switch change: set end date to end of day when toggled off
const onBodyChange = (e) => {
  if (e.target.closest(".vdpr-datepicker__switch")) {
    if (!calendarDialogRef.value.isAllDay) {
      calendarDialogRef.value.selectedEndDate = dayjs(calendarDialogRef.value.selectedEndDate).endOf("day").toDate();
    }
  }
};

onMounted(() => {
  document.addEventListener("click", onBodyClick);
  document.addEventListener("change", onBodyChange);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", onBodyClick);
  document.removeEventListener("change", onBodyChange);
});

// The dateSelected function is called every time a new date is selected. It is used to hold the new end date(newEndDate) to be used if the calendar date was selected.
const dateSelected = (newStartDate, newEndDate) => {
  tmpHelperButtonSelected.value = false;
  tempEndDate.value = newEndDate;
};

const showCalendar = () => {
  if (showCalendarDialog.value) {
    tmpHelperButtonSelected.value = helperButtonSelected.value ? true : false;
    randomKey.value = Math.random();
  }
  showCalendarDialog.value = !showCalendarDialog.value;
};

const refreshUpdateDateTime = () => {
  const refreshValue = _.find(helperButtons.value, { name: helperButtonText.value });

  if (refreshValue) {
    emit("update:startTimeDate:endTimeDate", refreshValue.from, refreshValue.to);
  }
};

defineExpose({
  refreshUpdateDateTime,
  setDateTimeToday,
  resetDateTime,
});

const calculateFromTime = (fromTime) => {
  return new Date(dayjs(newNow.value).subtract(fromTime, "hour"));
};

const calculateToTime = () => {
  return new Date(newNow.value);
};

const helperButtons = ref([
  {
    name: "Last Hour",
    get from() {
      return calculateFromTime(1);
    },
    get to() {
      return calculateToTime();
    },
  },
  {
    name: "Last 4 Hours",
    get from() {
      return calculateFromTime(4);
    },
    get to() {
      return calculateToTime();
    },
  },
  {
    name: "Last 8 Hours",
    get from() {
      return calculateFromTime(8);
    },
    get to() {
      return calculateToTime();
    },
  },
  {
    name: "Last 12 Hours",
    get from() {
      return calculateFromTime(12);
    },
    get to() {
      return calculateToTime();
    },
  },
  {
    name: "Last 24 Hours",
    get from() {
      return calculateFromTime(24);
    },
    get to() {
      return calculateToTime();
    },
  },
  {
    name: "Last 3 Days",
    get from() {
      return calculateFromTime(72);
    },
    get to() {
      return calculateToTime();
    },
  },
  {
    name: "Last 7 Days",
    get from() {
      return calculateFromTime(168);
    },
    get to() {
      return calculateToTime();
    },
  },
  {
    name: "Last 14 Days",
    get from() {
      return calculateFromTime(336);
    },
    get to() {
      return calculateToTime();
    },
  },
]);
</script>
<style lang="scss">
@use "vue-time-date-range-picker/dist/style.css";

.date-picker-container {
  position: relative;

  .input-area-width {
    width: 335px;
    border: 1px solid var(--df-input-border, #ced4da);
    border-radius: var(--p-inputtext-border-radius, 6px);
    padding: 0.5rem 0.75rem;
    cursor: pointer;

    &:hover {
      border-color: var(--primary-color);
    }
  }
}

.refresh-range-input {
  top: 0px;
  left: 0px;
  position: absolute;
}

.vdpr-datepicker__calendar-dialog {
  margin-left: -430px;
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

/* ── Dark mode overrides for the third-party date range picker ─────────── */
html.dark-mode {
  .vdpr-datepicker__calendar,
  .vdpr-datepicker__calendar-dialog {
    background-color: var(--surface-card);
    color: var(--text-color);
  }

  .vdpr-datepicker__calendar-dialog {
    box-shadow: 0 6px 12px rgba(0, 0, 0, 0.5);
  }

  .vdpr-datepicker__calendar-month-year {
    color: var(--text-color);
  }

  .vdpr-datepicker__calendar-control-prev,
  .vdpr-datepicker__calendar-control-next {
    border-color: var(--surface-border);
    color: var(--text-color);
  }

  .vdpr-datepicker__calendar-table th {
    color: var(--text-color);
  }

  .vdpr-datepicker__calendar-table td {
    background-color: var(--surface-ground);
    color: var(--text-color);
  }

  .vdpr-datepicker__calendar-table td:hover {
    background-color: var(--surface-hover);
  }

  .vdpr-datepicker__calendar-table .faded {
    color: var(--df-muted-color);
  }

  .vdpr-datepicker__calendar-table .disabled,
  .vdpr-datepicker__calendar-table .disabled:hover {
    background-color: var(--surface-700);
    color: var(--df-muted-color);
  }

  .vdpr-datepicker__button-default {
    color: var(--text-color);
    background-color: var(--surface-card);
    border-color: var(--surface-border);
  }

  .vdpr-datepicker__button-default:hover {
    background-color: var(--surface-hover);
  }

  .vdpr-datepicker__calendar-input-date-elem,
  .vdpr-datepicker__calendar-input-time {
    background-color: var(--df-input-bg);
    color: var(--text-color);
  }

  .vdpr-datepicker__calendar-input-time-elem {
    color: var(--text-color);
  }

  .vdpr-datepicker__calendar-input-time-control-up,
  .vdpr-datepicker__calendar-input-time-control-down {
    color: var(--df-muted-color);
  }

  .vdpr-datepicker__calendar-input-time-control-up:hover,
  .vdpr-datepicker__calendar-input-time-control-down:hover {
    color: var(--text-color);
  }
}
</style>
