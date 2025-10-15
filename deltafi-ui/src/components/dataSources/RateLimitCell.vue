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
  <div class="rate-limiting-cell">
    <div class="d-flex w-100 justify-content-between value-clickable">
      <template v-if="!_.isEmpty(props.rowDataProp) && _.every(props.rowDataProp)">
        {{ props.rowDataProp.maxAmount }} {{ _.toLower(props.rowDataProp.unit) }} every {{ formatSeconds(props.rowDataProp.durationSeconds).human }}
      </template>
      <template v-else>
        - 
      </template>
    </div>
  </div>
</template>

<script setup>
import dayjs from "dayjs";
import duration from "dayjs/plugin/duration";
import _ from "lodash";

dayjs.extend(duration);

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: null,
  },
});

const formatSeconds = (totalSeconds, { verbose = false } = {}) => {
  let sec = Math.floor(totalSeconds);

  const days = Math.floor(sec / (24 * 3600));
  sec %= 24 * 3600;

  const hours = Math.floor(sec / 3600);
  sec %= 3600;

  const minutes = Math.floor(sec / 60);
  const seconds = sec % 60;
  const pluralize = (val, unit) => (verbose ? `${val} ${unit}${val !== 1 ? "s" : ""}` : `${val}${unit[0]}`);
  const parts = [];
  if (days) parts.push(pluralize(days, "day"));
  if (hours) parts.push(pluralize(hours, "hour"));
  if (minutes) parts.push(pluralize(minutes, "minute"));
  if (seconds || parts.length === 0) parts.push(pluralize(seconds, "second"));

  return {
    days,
    hours,
    minutes,
    seconds,
    human: parts.join(verbose ? " " : " "),
  };
};
</script>
<style>
.rate-limiting-cell {
  .value-clickable {
    cursor: pointer;
    padding: 0.5rem !important;
    width: 100%;
    display: flex;
  }
}
</style>
