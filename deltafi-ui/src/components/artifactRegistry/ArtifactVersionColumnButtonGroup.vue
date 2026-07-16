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
  <span>
    <Menu ref="menu" :model="menuItems" :popup="true" :pt="{ action: { class: 'py-1' } }" @hide="horizontalEllipsis = false">
      <template #item="{ item, props }">
        <a v-ripple v-tooltip.left="item.tooltip" class="flex align-items-center" :class="{ 'menu-item-blocked': item.blocked }" :aria-disabled="item.blocked ? 'true' : undefined" v-bind="props.action">
          <span :class="item.icon" aria-hidden="true" />
          <span class="ml-2">{{ item.label }}</span>
        </a>
      </template>
    </Menu>
    <Button type="button" :icon="horizontalEllipsis ? 'pi pi-ellipsis-h' : 'pi pi-ellipsis-v'" severity="secondary" outlined size="small" class="mt-n1 mb-n1" v-tooltip.top="`Version actions`" :aria-label="`Actions for version ${version}`" aria-haspopup="true" @click="toggleMenu" />
    <ConfirmDialog :group="confirmGroup" />
  </span>
</template>

<script setup lang="ts">
import useArtifactRegistry from "@/composables/useArtifactRegistry";
import { computed, inject, ref } from "vue";

import Button from "primevue/button";
import ConfirmDialog from "primevue/confirmdialog";
import Menu from "primevue/menu";
import Ripple from "primevue/ripple";
import { useConfirm } from "primevue/useconfirm";

const vRipple = Ripple;
const confirm = useConfirm();
const { downloadArtifact, rollbackArtifact, deleteVersion } = useArtifactRegistry();
const hasPermission = inject("hasPermission") as (permission: string) => boolean;
const emit = defineEmits(["refresh", "setAlias"]);
const menu = ref();
const horizontalEllipsis = ref(false);

const props = defineProps({
  owner: {
    type: String,
    required: true,
  },
  artifactName: {
    type: String,
    required: true,
  },
  version: {
    type: String,
    required: true,
  },
  active: {
    type: Boolean,
    required: false,
    default: false,
  },
});

const confirmGroup = computed(() => `delete-version-${props.owner}-${props.artifactName}-${props.version}`);

const menuItems = computed(() => [
  {
    label: "Download",
    icon: "pi pi-download",
    command: () => {
      downloadArtifact(props.owner, props.artifactName, props.version);
    },
  },
  {
    label: "Set as Active Version",
    icon: "pi pi-replay",
    visible: hasPermission("ArtifactRegistryWrite") && !props.active,
    command: async () => {
      await rollbackArtifact(props.owner, props.artifactName, props.version);
      emit("refresh");
    },
  },
  {
    label: "Set Alias",
    icon: "pi pi-tag",
    visible: hasPermission("ArtifactRegistryWrite"),
    command: () => {
      emit("setAlias", props.version);
    },
  },
  {
    label: "Delete Version",
    icon: "pi pi-trash",
    visible: hasPermission("ArtifactRegistryWrite"),
    // The active version cannot be deleted; the backend rejects it (409). Block it in the
    // UI too and explain why, rather than letting the user click through to an error.
    blocked: props.active,
    tooltip: props.active ? "The active version cannot be deleted. Set another version as active first." : null,
    command: () => {
      if (props.active) return;
      confirmDelete();
    },
  },
]);

const toggleMenu = (event: Event) => {
  horizontalEllipsis.value = !horizontalEllipsis.value;
  menu.value.toggle(event);
};

const confirmDelete = () => {
  confirm.require({
    position: "center",
    group: confirmGroup.value,
    message: `Delete version ${props.version} of ${props.artifactName}?`,
    acceptLabel: "Delete",
    rejectLabel: "Cancel",
    icon: "pi pi-exclamation-triangle",
    accept: async () => {
      await deleteVersion(props.owner, props.artifactName, props.version);
      emit("refresh");
    },
    reject: () => {},
  });
};
</script>

<style scoped>
/* Visually disable the item but keep it hoverable so its explanatory tooltip still shows
   (PrimeVue's built-in `disabled` sets pointer-events: none, which would suppress the tooltip). */
.menu-item-blocked {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
