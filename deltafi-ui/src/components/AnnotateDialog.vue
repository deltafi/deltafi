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
    <Dialog v-model:visible="dialogVisible" header="Annotate DeltaFile" :maximizable="false" :modal="true" :draggable="false" :style="{ width: '30vw' }">
      <MapEdit v-model="metadata" />
      <template #footer>
        <span style="float: left" class="field-checkbox">
          <Checkbox v-model="allowOverwrites" input-id="allOverwrite" :binary="true" />
          <label for="allowOverwrite" class="ml-2">Overwrite?</label>
        </span>
        <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="cancel" />
        <Button :label="saveButtonLabel" icon="pi pi-check" :loading="saving" @click="onSaveClick" />
      </template>
    </Dialog>
  </div>
</template>

<script setup>
import { ref, computed } from "vue";
import MapEdit from "@/components/plugin/MapEdit.vue";
import Dialog from "primevue/dialog";
import Button from "primevue/button";
import Checkbox from "primevue/checkbox";
import useAnnotate from "@/composables/useAnnotate";

const props = defineProps({
  dids: {
    type: Array,
    required: true,
  },
});

const emit = defineEmits(["refreshPage"]);
const { annotate } = useAnnotate();
const metadata = ref(":");
const dialogVisible = ref(false);
const saving = ref(false);
const saveButtonLabel = computed(() => (saving.value ? "Saving" : "Save"));
const allowOverwrites = ref(false);

const onSaveClick = async () => {
  saving.value = true;
  await annotate(props.dids, metadata.value, allowOverwrites.value);
  dialogVisible.value = false;
  saving.value = false;
  emit("refreshPage");
};

const cancel = () => {
  dialogVisible.value = false;
  metadata.value = ":";
};

const showDialog = () => {
  metadata.value = ":";
  dialogVisible.value = true;
};

defineExpose({
  showDialog,
});
</script>

<style>
.field-checkbox {
  display: flex;

  label {
    display: flex;
    align-items: center;
    margin-top: 0.15rem;
    margin-left: 0.4rem;
  }
}
</style>
