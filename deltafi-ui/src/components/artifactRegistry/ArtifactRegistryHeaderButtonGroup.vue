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

<template>
  <Menu
    id="ArtifactRegistryPageHeaderMenu"
    ref="menu"
    :model="items"
    :popup="true"
    :pt="{
      action: {
        class: 'py-1',
      },
    }"
    @hide="hideMenu"
    @click="hideMenu"
  >
    <template #item="{ item, props }">
      <a v-ripple class="flex align-items-center" v-bind="props.action">
        <span :class="item.icon" aria-hidden="true" />
        <span class="ml-2">{{ item.label }}</span>
      </a>
    </template>
  </Menu>
  <Button id="ArtifactRegistryPageHeaderOptions" ref="optionsButton" v-tooltip.left="`Options`" type="button" severity="secondary" outlined iconPos="right" size="small" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" aria-label="Artifact registry options" aria-haspopup="true" aria-controls="ArtifactRegistryPageHeaderMenu" @click="showMenu" />
  <DialogTemplate ref="uploadDialog" component-name="artifactRegistry/ArtifactUploadDialog" header="Upload Artifact" dialog-width="50vw" :owner="owner" :artifact-name="artifactName" @refresh-page="refresh" />
</template>

<script setup lang="ts">
import DialogTemplate from "@/components/DialogTemplate.vue";
import { computed, inject, ref } from "vue";

import Button from "primevue/button";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";

const emit = defineEmits(["reload"]);
const vRipple = Ripple;
const menu = ref();
const uploadDialog = ref<InstanceType<typeof DialogTemplate> | null>(null);
const optionsButton = ref(null);
const horizontalEllipsis = ref(false);
const hasPermission = inject("hasPermission") as (permission: string) => boolean;

defineProps({
  owner: {
    type: String,
    required: false,
    default: "shared",
  },
  artifactName: {
    type: String,
    required: false,
    default: "",
  },
});

const items = ref([
  {
    label: "Upload Artifact",
    icon: "pi pi-upload",
    visible: computed(() => hasPermission("ArtifactRegistryWrite")),
    command: () => {
      uploadDialog.value?.showDialog();
    },
  },
  {
    label: "Refresh",
    icon: "pi pi-refresh",
    command: () => {
      refresh();
    },
  },
]);

const showMenu = (event: Event) => {
  horizontalEllipsis.value = !horizontalEllipsis.value;
  menu.value.toggle(event);
};

const hideMenu = async () => {
  horizontalEllipsis.value = false;
  menu.value.hide();
};

const refresh = async () => {
  emit("reload");
};
</script>
