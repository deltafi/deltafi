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
  <div>
    <span @click="showDialog()">
      <slot />
    </span>
    <Dialog id="dialogTemplate" ref="dialogTemplate" v-model:visible="dialogVisible" :header="$attrs['header']" :position="modelPosition" :style="{ width: dialogSize }" :maximizable="true" :modal="!disableModel" :dismissable-mask="dismissableMask" :draggable="false" :closable="isClosable" @hide="closeDialogTemplate" @show="openDialogTemplate">
      <Component :is="loadComponent" :key="Math.random()" v-bind="$attrs" :close-dialog-command="closeDialogCommand" />
    </Dialog>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, inject, nextTick, ref, useAttrs } from "vue";
import Dialog from "primevue/dialog";
import _ from "lodash";

const components = import.meta.glob('@/components/**/*.vue')
const emit = defineEmits(["openDialogTemplate", "closeDialogTemplate"]);
const hasPermission = inject("hasPermission");
const dialogTemplate = ref(null);

//View dynamic props being sent down
const attrs = useAttrs();

const closeDialogCommand = ref({
  command: () => {
    closeDialog();
  },
});

const loadComponent = computed(() => {
  return defineAsyncComponent(async () => await components[`/src/components/${attrs["component-name"]}.vue`]());
});

const dialogSize = computed(() => {
  return _.isEmpty(_.get(attrs, "dialog-width")) ? "75vw" : attrs["dialog-width"];
});

const dismissableMask = computed(() => {
  return _.get(attrs, "dismissable-mask", false);
});

const disableModel = computed(() => {
  return _.get(attrs, "disable-model", false);
});

const modelPosition = computed(() => {
  return _.get(attrs, "model-position", "top");
});

const isClosable = computed(() => {
  return _.get(attrs, "closable", true);
});

const closeDialogTemplate = () => {
  emit("closeDialogTemplate");
};

const openDialogTemplate = () => {
  emit("openDialogTemplate");
};

const dialogVisible = ref(false);
const showDialog = async () => {
  // The PrimeVue Dialog component automatically tries to focus the first focusable element inside the dialog when it opens.
  // Since we are trying to load in a dynamic component it's not there yet and its trying to access .nextSibling on null,
  // causing the error. Ensure a focusable element exists.
  await nextTick();
  if (dialogTemplate.value) dialogTemplate.value.focus();

  const requiredPermission = _.get(attrs, "required-permission", null);

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

<style />
