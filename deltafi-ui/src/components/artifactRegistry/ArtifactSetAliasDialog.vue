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
  <div class="artifact-set-alias-dialog">
    <div class="mb-3">
      <label for="aliasName" class="font-weight-bold mb-1 d-block">Alias Name<span class="required-indicator" aria-hidden="true">*</span></label>
      <InputText id="aliasName" v-model="aliasName" class="w-100" placeholder="e.g. latest, stable" aria-required="true" />
    </div>
    <div class="mb-3">
      <label for="aliasVersion" class="font-weight-bold mb-1 d-block">Version<span class="required-indicator" aria-hidden="true">*</span></label>
      <Select id="aliasVersion" v-model="selectedVersion" :options="versions" class="w-100" placeholder="Select a version" aria-required="true" />
    </div>
    <div class="d-flex justify-content-end">
      <Button label="Set Alias" icon="pi pi-tag" :disabled="!canSubmit" @click="submit" />
    </div>
  </div>
</template>

<script setup lang="ts">
import useArtifactRegistry from "@/composables/useArtifactRegistry";
import { computed, ref } from "vue";

import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Select from "primevue/select";

const { setAlias } = useArtifactRegistry();
const emit = defineEmits(["refreshAndClose"]);

const props = defineProps({
  owner: {
    type: String,
    required: true,
  },
  artifactName: {
    type: String,
    required: true,
  },
  versions: {
    type: Array<string>,
    required: true,
  },
  initialVersion: {
    type: String,
    required: false,
    default: "",
  },
});

const aliasName = ref("");
const selectedVersion = ref(props.initialVersion || "");

const canSubmit = computed(() => {
  return aliasName.value.trim() !== "" && selectedVersion.value !== "";
});

const submit = async () => {
  if (!canSubmit.value) return;
  await setAlias(props.owner, props.artifactName, aliasName.value.trim(), selectedVersion.value);
  emit("refreshAndClose");
};
</script>
