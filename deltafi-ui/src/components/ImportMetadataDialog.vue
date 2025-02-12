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
  <Dialog header="Import Metadata from DeltaFile" :maximizable="false" :modal="true" :dismissable-mask="false" :draggable="false" class="did-import-dialog">
    <div class="meata-data-import" />

    <div class="d-flex justify-content-between">
      <div>
        <InputText v-model="did" placeholder="DID (UUID)" :class="{ 'p-invalid': did && !validDID }" style="width: 24rem" @keyup.enter="importFromDID" />
        <div>
          <small v-if="did && !validDID" class="p-error ml-1">Invalid UUID</small>
        </div>
      </div>
    </div>
    <template #footer>
      <Button class="p-button-primary" :disabled="!did || !validDID" @click="importFromDID">
        Import
      </Button>
    </template>
  </Dialog>
</template>

<script setup>
import { ref, computed } from "vue";
import InputText from "primevue/inputtext";
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import useDeltaFiles from "@/composables/useDeltaFiles";

const emit = defineEmits(["metaDataValue"]);
const did = ref();
const uuidRegex = new RegExp(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}$/i);

const { data: deltaFile, getDeltaFile } = useDeltaFiles();

const importFromDID = async () => {
  if (did.value === null) {
    return;
  } else if (validDID.value) {
    await getDeltaFile(did.value);
    const raw = JSON.parse(JSON.stringify(deltaFile));
    const metadata = Object.entries(raw.flows[0].actions[0].metadata).map(([key, value]) => {
      return { key, value };
    })
    const dataValue = {
      metadata: metadata,
      dataSource: raw.dataSource,
    }
    emit("metaDataValue", dataValue);
    did.value = null;
  }
};

const validDID = computed(() => {
  return uuidRegex.test(did.value);
});
</script>