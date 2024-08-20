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
  <iframe :src="src" :width="props.width" :height="props.height" frameborder="0"></iframe>
</template>

<script setup>
import { computed, defineProps } from "vue";

const props = defineProps({
  panelId: {
    type: Number,
    required: true,
  },
  from: {
    type: Number,
    required: true,
  },
  to: {
    type: Number,
    required: true,
  },
  width: {
    type: String,
    required: false,
    default: "100%",
  },
  height: {
    type: String,
    required: false,
    default: "200px",
  },
  orgId: {
    type: Number,
    required: false,
    default: 1,
  },
  theme: {
    type: String,
    required: false,
    default: "light",
  },
  refresh: {
    type: String,
    required: false,
    default: "5m",
  },
  dashboardId: {
    type: String,
    required: false,
    default: "ui-charts",
  },
  keyPairs: {
    type: Object,
    required: false,
    default: null,
  },
});

const getKeys = () => {
  const keyPairs = props.keyPairs;
  let expendedProps = {
    ...keyPairs,
    ...props,
  };
  expendedProps.keyPairs = null;
  return expendedProps;
};

const src = computed(() => {
  let params = null;
  if (props.keyPairs) {
    let expandedParms = getKeys();
    params = new URLSearchParams(expandedParms);
  } else {
    params = new URLSearchParams(props);
  }
  return `/visualization/d-solo/${props.dashboardId}?${params}`;
});
</script>
