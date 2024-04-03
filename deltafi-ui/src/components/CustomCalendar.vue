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
  <date-picker :key="Math.random()" ref="datePickerRef" :date-input="dateInput" :calendar-date-input="calendarDateInput" switch-button-label="All Day" :format="timestampFormat" :same-date-format="sameDateFormat" :initial-dates="[new Date(startTimeDate), new Date(endTimeDate)]" :show-helper-buttons="true" :helper-buttons="helperButtons" @date-applied="updateInputDateTime" @on-reset="resetDateTime" @datepicker-opened="updateHelperButtons" />
</template>

<script setup>
import DatePicker from "vue-time-date-range-picker/src/Components/DatePicker";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { onBeforeMount, computed, inject, ref, defineProps } from "vue";
dayjs.extend(utc);

onBeforeMount(() => {
  setDateTimeToday();
  updateHelperButtons();
});
const emit = defineEmits(["update:startTimeDate:endTimeDate"]);
const helperButtons = ref([]);
const uiConfig = inject("uiConfig");
const datePickerRef = ref(null);
const timestampFormat = "YYYY-MM-DD HH:mm:ss";
const startTimeDate = ref();
const endTimeDate = ref();
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
  customButtons: {
    type: Array,
    required: false,
    default() {
      return ["Last Hour", "Last 4 Hours", "Last 8 Hours", "Last 12 Hours", "Last 24 Hours", "Last 3 Days", "Last 3 Days", "Last 7 Days", "Last 14 Days"];
    },
  },
  resetDefault: {
    type: Array,
    required: false,
    default: null,
  },
});
const defaultStartTimeDate = computed(() => {
  if (props.startTimeDate) {
    return props.startTimeDate;
  } else {
    const date = dayjs().utc();
    return (uiConfig.useUTC ? date : date.local()).startOf("day");
  }
});
const defaultEndTimeDate = computed(() => {
  if (props.endTimeDate) {
    return props.endTimeDate;
  } else {
    const date = dayjs().utc();
    return (uiConfig.useUTC ? date : date.local()).endOf("day");
  }
});
const setDateTimeToday = () => {
  if (props.startTimeDate) {
    startTimeDate.value = props.startTimeDate;
  } else {
    startTimeDate.value = new Date(defaultStartTimeDate.value.format(timestampFormat));
  }
  if (props.endTimeDate) {
    endTimeDate.value = props.endTimeDate;
  } else {
    endTimeDate.value = new Date(defaultEndTimeDate.value.format(timestampFormat));
  }
};
const sameDateFormat = {
  from: timestampFormat,
  to: timestampFormat,
};
const resetDateTime = async () => {
  if (props.resetDefault) {
    datePickerRef.value.onApply(props.resetDefault[0], props.resetDefault[1]);
    emit("update:startTimeDate:endTimeDate", props.resetDefault[0], props.resetDefault[1]);
  } else if (props.startTimeDate) {
    datePickerRef.value.onApply(props.startTimeDate, props.endTimeDate);
  } else {
    datePickerRef.value.onApply(new Date(defaultStartTimeDate.value.format(timestampFormat)), new Date(defaultEndTimeDate.value.format(timestampFormat)));
  }
};
const calendarDateInput = {
  labelStarts: "Start",
  labelEnds: "End",
  inputClass: null,
  format: "YYYY-MM-DD",
};

const updateInputDateTime = async (startDate, endDate) => {
  startTimeDate.value = startDate;
  endTimeDate.value = endDate;
  emit("update:startTimeDate:endTimeDate", startDate, endDate);
};

const dateInput = {
  placeholder: "Select Date",
  inputClass: "p-inputtext p-component deltafi-input-field input-area-width",
};

const updateHelperButtons = () => {
  let now = new Date();
  // If running in UTC mode, set dates in the future because the DatePicker does not have a UTC mode.
  if (uiConfig.useUTC) {
    now = now.getTime() + now.getTimezoneOffset() * 60000;
  }
  const buttons = [
    {
      name: "Last Hour",
      from: new Date(now - 1 * 3600000),
      to: new Date(now),
    },
    {
      name: "Last 4 Hours",
      from: new Date(now - 4 * 3600000),
      to: new Date(now),
    },
    {
      name: "Last 8 Hours",
      from: new Date(now - 8 * 3600000),
      to: new Date(now),
    },
    {
      name: "Last 12 Hours",
      from: new Date(now - 12 * 3600000),
      to: new Date(now),
    },
    {
      name: "Last 24 Hours",
      from: new Date(now - 24 * 3600000),
      to: new Date(now),
    },
    {
      name: "Last 3 Days",
      from: new Date(now - 3 * 24 * 3600000),
      to: new Date(now),
    },
    {
      name: "Last 7 Days",
      from: new Date(now - 7 * 24 * 3600000),
      to: new Date(now),
    },
    {
      name: "Last 14 Days",
      from: new Date(now - 14 * 24 * 3600000),
      to: new Date(now),
    },
  ];
  helperButtons.value.length = 0;
  buttons.forEach((button) => {
    if (props.customButtons.includes(button.name)) helperButtons.value.push(button);
  });
};
</script>
<style lang="scss">
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
