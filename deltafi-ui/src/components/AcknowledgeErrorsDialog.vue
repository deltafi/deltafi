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
  <Dialog :header="acknowledgeButtonLabel" :maximizable="false" :modal="true" :style="{ width: '25vw' }" @update:visible="close">
    <div class="p-fluid">
      <span class="p-float-label mt-3">
        <InputText id="reason" v-model="reason" type="text" :class="{ 'p-invalid': reasonInvalid }" autofocus />
        <label for="reason">Reason</label>
      </span>
    </div>
    <template #footer>
      <Button label="Cancel" icon="pi pi-times" class="p-button-text" @click="close" />
      <Button :label="acknowledgeButtonLabel" icon="pi pi-check" @click="acknowledge" />
    </template>
  </Dialog>
</template>

<script setup>
import Button from "primevue/button";
import InputText from "primevue/inputtext";
import Dialog from "primevue/dialog";
import useUtilFunctions from "@/composables/useUtilFunctions";
import { computed, ref, defineProps, defineEmits } from "vue";
import useAcknowledgeErrors from "@/composables/useAcknowledgeErrors";

const props = defineProps({
  dids: {
    type: Array,
    required: true,
  },
});

const emit = defineEmits(["acknowledged", "update:visible"]);
const { pluralize } = useUtilFunctions();
const reason = ref("");
const reasonInvalid = ref(false);

const { post: PostAcknowledgeErrors } = useAcknowledgeErrors();

const acknowledgeButtonLabel = computed(() => {
  if (props.dids.length === 1) return "Acknowledge Error";
  let pluralized = pluralize(props.dids.length, "Error");
  return `Acknowledge ${pluralized}`;
});

const acknowledge = async () => {
  if (reason.value) {
    try {
      await PostAcknowledgeErrors(props.dids, reason.value);
      emit("acknowledged", props.dids, reason.value);
      emit("update:visible", false);
      reason.value = "";
    } catch {
      // Do Nothing
    }
  } else {
    reasonInvalid.value = true;
  }
};

const close = () => {
  emit("update:visible", false);
};
</script>

<style lang="scss">
</style>