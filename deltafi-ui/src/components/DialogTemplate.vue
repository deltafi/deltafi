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
  <div>
    <span @click="showDialog()">
      <slot />
    </span>
    <Dialog id="dialogTemplate" v-model:visible="dialogVisible" :header="$attrs['header']" position="top" :style="{ width: dialogSize }" :maximizable="true" :modal="true" :dismissable-mask="true" :draggable="false">
      <Component :is="loadComponent" v-bind="$attrs" :close-dialog-command="closeDialogCommand" />
    </Dialog>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, inject, ref, useAttrs } from "vue";
import Dialog from "primevue/dialog";
import _ from "lodash";

const hasPermission = inject("hasPermission");

//View dynamic props being sent down
const attrs = useAttrs();
//console.log(attrs);

const closeDialogCommand = ref({
  command: () => {
    closeDialog();
  },
});

const loadComponent = computed(() => {
  return defineAsyncComponent(() => import(`./${attrs["component-name"]}.vue`));
});

const dialogSize = computed(() => {
  return _.isEmpty(_.get(attrs, "dialog-width")) ? "75vw" : attrs["dialog-width"];
});

const dialogVisible = ref(false);
const showDialog = () => {
  let requiredPermission = _.get(attrs, "required-permission", null);

  if (requiredPermission) {
    if (hasPermission(requiredPermission)) {
      dialogVisible.value = true;
    } else {
      dialogVisible.value = false;
    }
  } else {
    dialogVisible.value = true;
  }
};

const closeDialog = () => {
  dialogVisible.value = false;
};
</script>

<style lang="scss"></style>