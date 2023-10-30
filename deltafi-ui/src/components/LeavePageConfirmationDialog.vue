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
  <ConfirmDialog group="positioned" />
</template>
    
<script setup>
import { defineProps } from "vue";
import { onBeforeRouteLeave } from "vue-router";

import ConfirmDialog from "primevue/confirmdialog";
import { useConfirm } from "primevue/useconfirm";

const confirm = useConfirm();
const props = defineProps({
  header: {
    type: String,
    required: true,
  },
  message: {
    type: String,
    required: true,
  },
  matchCondition: {
    type: Boolean,
    required: true,
  },
});

onBeforeRouteLeave((to, from, next) => {
  if (props.matchCondition) {
    confirm.require({
      group: "positioned",
      message: `${props.message}`,
      header: `${props.header}`,
      icon: "pi pi-info-circle",
      position: "top",
      accept: () => {
        next();
      },
      reject: () => {},
    });
  } else {
    next();
  }
});
</script>

    