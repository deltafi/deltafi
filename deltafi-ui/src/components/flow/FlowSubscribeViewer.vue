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
  <div class="flow-subscribe-viewer">
    <span v-for="(value, index) in props.subscribe" :key="value">
      <b>Condition: </b>
      <a v-if="value.condition">{{ value.condition }}</a>
      <a v-else>None</a><br />
      <b>Topic: </b>
      <ul>
        <div v-for="topic in value.topic" :key="topic">
          <li>{{ topic }}</li>
        </div>
      </ul>
      <hr v-if="index !== props.subscribe.length - 1" />
    </span>
  </div>
</template>

<script setup>
import { defineProps } from "vue";

const props = defineProps({
  subscribe: {
    type: Array,
    required: true,
  },
  header: {
    type: String,
    required: true,
  },
});
</script>
