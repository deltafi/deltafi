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
  <div class="artifact-upload-dialog">
    <div class="mb-3">
      <label for="owner" class="font-weight-bold mb-1 d-block">Owner<span class="required-indicator" aria-hidden="true">*</span></label>
      <InputText id="owner" v-model="owner" class="w-100" placeholder="shared" aria-required="true" :disabled="!!props.artifactName" />
    </div>
    <div class="mb-3">
      <label for="artifactName" class="font-weight-bold mb-1 d-block">Artifact Name<span class="required-indicator" aria-hidden="true">*</span></label>
      <InputText id="artifactName" v-model="artifactName" class="w-100" placeholder="my-artifact" aria-required="true" :disabled="!!props.artifactName" />
    </div>
    <div class="mb-3">
      <label for="artifactFile" class="font-weight-bold mb-1 d-block">File<span class="required-indicator" aria-hidden="true">*</span></label>
      <input id="artifactFile" ref="fileInput" type="file" class="form-control" aria-required="true" @change="onFileSelected" />
    </div>
    <div class="mb-3">
      <label for="version" class="font-weight-bold mb-1 d-block">Version (optional)</label>
      <InputText id="version" v-model="version" class="w-100" placeholder="Auto-generated if empty" />
    </div>
    <div class="mb-3">
      <label for="description" class="font-weight-bold mb-1 d-block">Description (optional)</label>
      <InputText id="description" v-model="description" class="w-100" placeholder="Describe this version" />
    </div>
    <div class="mb-3">
      <label class="font-weight-bold mb-1 d-block">Labels (optional)</label>
      <MapEdit v-model="labels" />
      <Message v-if="labelError" severity="error" :closable="false" class="mt-2" role="alert">{{ labelError }}</Message>
    </div>
    <div class="mb-3 d-flex align-items-center">
      <ToggleSwitch id="makeActive" v-model="makeActive" />
      <label for="makeActive" class="ml-2 mb-0">Make Active</label>
    </div>
    <div v-if="uploadResult && uploadResult.loading" class="mb-3" role="status" aria-live="polite">
      <ProgressBar :value="uploadResult.percentComplete" :aria-label="`Upload progress: ${uploadResult.percentComplete}%`" />
    </div>
    <div v-if="uploadResult && uploadResult.error" class="mb-3" role="alert" aria-live="assertive">
      <Message severity="error" :closable="false">Upload failed. Check notifications for details.</Message>
    </div>
    <div class="d-flex justify-content-end">
      <Button label="Upload" icon="pi pi-upload" :disabled="!canUpload" @click="upload" />
    </div>
  </div>
</template>

<script setup lang="ts">
import MapEdit from "@/components/editPanels/MapEdit.vue";
import useArtifactRegistry from "@/composables/useArtifactRegistry";
import type { UploadResult } from "@/composables/useArtifactRegistry";
import { computed, ref, watch } from "vue";

import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import ProgressBar from "primevue/progressbar";
import ToggleSwitch from "primevue/toggleswitch";

const { uploadArtifact } = useArtifactRegistry();
const emit = defineEmits(["refreshAndClose"]);

const props = defineProps({
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

const owner = ref(props.owner || "shared");
const artifactName = ref(props.artifactName || "");
const version = ref("");
const description = ref("");
const makeActive = ref(true);
// MapEdit's model is a flat "key: value, key2: value2" string (it emits null when empty,
// which the labelPairs empty-check below absorbs at runtime).
const labels = ref<string>("");
const selectedFile = ref<File | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const uploadResult = ref<UploadResult | null>(null);

// Field names the backend reserves; they cannot double as label keys or the label is dropped.
const RESERVED_LABEL_KEYS = ["file", "version", "description", "makeActive", "alias"];

// Split MapEdit's string back into pairs (on the first ':' so values keep any later colons),
// dropping fully-empty rows the user hasn't filled in yet.
const labelPairs = computed(() => {
  const raw = labels.value;
  if (!raw) return [] as Array<{ key: string; value: string }>;
  return raw
    .split(",")
    .map((segment) => {
      const idx = segment.indexOf(":");
      const key = (idx === -1 ? segment : segment.slice(0, idx)).trim();
      const value = idx === -1 ? "" : segment.slice(idx + 1).trim();
      return { key, value };
    })
    .filter((pair) => pair.key !== "" || pair.value !== "");
});

const labelError = computed(() => {
  const keys = labelPairs.value.map((pair) => pair.key);
  if (labelPairs.value.some((pair) => pair.key === "")) return "Label keys cannot be empty.";
  const reserved = keys.find((key) => RESERVED_LABEL_KEYS.includes(key));
  if (reserved) return `"${reserved}" is a reserved field name and cannot be used as a label key.`;
  const duplicate = keys.find((key, i) => keys.indexOf(key) !== i);
  if (duplicate) return `Duplicate label key: "${duplicate}".`;
  return "";
});

const parsedLabels = computed<Record<string, string>>(() => {
  const result: Record<string, string> = {};
  for (const { key, value } of labelPairs.value) {
    if (key) result[key] = value;
  }
  return result;
});

const canUpload = computed(() => {
  return owner.value.trim() !== "" && artifactName.value.trim() !== "" && selectedFile.value !== null && labelError.value === "" && (!uploadResult.value || !uploadResult.value.loading);
});

const onFileSelected = (event: Event) => {
  const target = event.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] || null;
};

const upload = () => {
  if (!selectedFile.value || !artifactName.value.trim() || !owner.value.trim()) return;

  const result = uploadArtifact(owner.value.trim(), artifactName.value.trim(), selectedFile.value, {
    version: version.value || undefined,
    description: description.value || undefined,
    makeActive: makeActive.value,
    labels: Object.keys(parsedLabels.value).length ? parsedLabels.value : undefined,
  });

  uploadResult.value = result;

  watch(
    () => result.loading,
    (isLoading) => {
      if (!isLoading && !result.error) {
        emit("refreshAndClose");
      }
    }
  );
};
</script>
