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

<!-- ABOUTME: Shared toolbar component for search pages with date type, date picker, timezone, and refresh. -->
<!-- ABOUTME: Used by DeltaFileSearchPage and FleetSearchPage. -->

<template>
  <div class="search-date-toolbar btn-toolbar mb-2 mb-md-0">
    <Dropdown v-model="dateTypeModel" :options="dateTypeOptions" class="deltafi-input-field date-type-dropdown" />
    <CustomCalendar ref="customCalendarRef" :start-time-date="startTimeDate" :end-time-date="endTimeDate" :reset-default="resetDefault" class="ml-0" @update:start-time-date:end-time-date="onDateChange" />
    <Button class="p-button-text p-button-sm p-button-secondary" disabled>
      {{ shortTimezone() }}
    </Button>
    <Button class="p-button p-button-outlined deltafi-input-field ml-1" icon="fa fa-sync-alt" :loading="loading" label="Refresh" @click="$emit('refresh')" />
  </div>
</template>

<script setup>
import CustomCalendar from "@/components/CustomCalendar.vue";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, ref } from "vue";

import Button from "primevue/button";
import Dropdown from "primevue/dropdown";

const props = defineProps({
  dateType: { type: String, required: true },
  dateTypeOptions: { type: Array, required: true },
  startTimeDate: { type: Date, required: true },
  endTimeDate: { type: Date, required: true },
  resetDefault: { type: Array, required: true },
  loading: { type: Boolean, default: false },
});

const emit = defineEmits(["update:dateType", "dateChange", "refresh"]);

const { shortTimezone } = useUtilFunctions();
const customCalendarRef = ref(null);

const dateTypeModel = computed({
  get: () => props.dateType,
  set: (value) => emit("update:dateType", value),
});

const onDateChange = (startDate, endDate) => {
  emit("dateChange", startDate, endDate);
};

const refreshUpdateDateTime = () => {
  return customCalendarRef.value?.refreshUpdateDateTime();
};

defineExpose({
  refreshUpdateDateTime,
});
</script>

<style>
.search-date-toolbar {
  .date-type-dropdown {
    width: 8rem;
    margin-right: 0.5rem;
  }
}
</style>
