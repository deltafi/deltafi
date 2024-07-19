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

<template class="time-range btn-toolbar mb-2 mb-md-0">
  <div class="date-picker-container">
    <DatePicker :key="computedKey" ref="datePickerRef" :date-input="dateInput" :calendar-date-input="calendarDateInput" switch-button-label="All Day" :format="timestampFormat" :same-date-format="sameDateFormat" :initial-dates="[new Date(startTimeDate), new Date(endTimeDate)]" :show-helper-buttons="true" :helper-buttons="helperButtons" @date-applied="updateInputDateTime" @on-reset="resetDateTime" @datepicker-closed="pickerClosed" @select-date="dateSelected" />

    <input v-show="showRefreshRange" type="text" :value="helperButtonText" placeholder="Select Date" class="p-inputtext p-component deltafi-input-field input-area-width refresh-range-input" readonly @click="showCalendar()" />
  </div>
</template>

<script setup>
import DatePicker from "vue-time-date-range-picker/src/Components/DatePicker";

import { computed, defineExpose, defineProps, inject, onBeforeMount, ref, watch } from "vue";
import { useNow } from "@vueuse/core";

import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import $ from "jquery";

dayjs.extend(utc);

const emit = defineEmits(["update:startTimeDate:endTimeDate"]);
const now = useNow();
const newNow = ref(now.value);
const uiConfig = inject("uiConfig");
const datePickerRef = ref(null);
const timestampFormat = "YYYY-MM-DD HH:mm:ss";
const startTimeDate = ref();
const endTimeDate = ref();
const randomKey = ref(Math.random());
const helperButtonSelected = ref(false);
const tmpHelperButtonSelected = ref(false);

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

const resetDateTime = async () => {
  tmpHelperButtonSelected.value = false;
  if (props.resetDefault) {
    datePickerRef.value.onApply(props.resetDefault[0], props.resetDefault[1]);
    emit("update:startTimeDate:endTimeDate", props.resetDefault[0], props.resetDefault[1]);
  } else if (props.startTimeDate) {
    datePickerRef.value.onApply(props.startTimeDate, props.endTimeDate);
  } else {
    datePickerRef.value.onApply(new Date(defaultStartTimeDate.value.format(timestampFormat)), new Date(defaultEndTimeDate.value.format(timestampFormat)));
  }
  randomKey.value = Math.random();
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
  emit("update:startTimeDate:endTimeDate", startDate, endDate);
};

const dateInput = {
  placeholder: "Select Date",
  inputClass: "p-inputtext p-component deltafi-input-field input-area-width",
};

const pickerClosed = () => {
  tmpHelperButtonSelected.value = helperButtonSelected.value ? true : false;
  randomKey.value = Math.random();
};

const tempEndDate = ref(null);

// This JQuery code is used to watch for click events in the calendar table of the datePicker. If a date is clicked on the calendar, the selectedEndDate is taken and
// the time of that date is set to the end of the day(23:59)
$("body").on("click", "table.vdpr-datepicker__calendar-table > tbody > tr", function () {
  const domComponent = document.getElementsByClassName("vdpr-datepicker__calendar-actions");

  domComponent["0"].__vnode.ctx.data.selectedEndDate = dayjs(tempEndDate.value).endOf("day").toDate();
});

const helperButtonText = ref(null);
const tmpHelperButtonText = ref(null);
$("body").on("click", "button.vdpr-datepicker__button.vdpr-datepicker__button--block.vdpr-datepicker__button-default", function (e) {
  tmpHelperButtonSelected.value = true;
  tmpHelperButtonText.value = e.currentTarget.textContent;
});

$("body").on("click", ".vdpr-datepicker__calendar-input-time-control", function () {
  tmpHelperButtonSelected.value = false;
});

// The dateSelected function is called every time a new date is selected. It is used to hold the new end date(newEndDate) to be used if the calendar date was selected.
const dateSelected = (newStartDate, newEndDate) => {
  tmpHelperButtonSelected.value = false;
  tempEndDate.value = newEndDate;
};

const showCalendar = () => {
  datePickerRef.value.onClickDateInput();
};

const refreshUpdateDateTime = () => {
  let refreshValue = _.find(helperButtons.value, { name: helperButtonText.value });

  if (refreshValue) {
    datePickerRef.value.onApply(refreshValue.from, refreshValue.to);
    emit("update:startTimeDate:endTimeDate", refreshValue.from, refreshValue.to);
  }
};

defineExpose({
  refreshUpdateDateTime,
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
.date-picker-container {
  position: relative;
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
</style>
