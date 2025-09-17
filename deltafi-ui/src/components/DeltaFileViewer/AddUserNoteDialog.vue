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
  <div id="error-message" class="add-user-note-dialog">
    <div class="user-note-panel">
      <div class="pb-0">
        <div v-if="!_.isEmpty(errors)" class="pt-2">
          <Message severity="error" :sticky="true" class="mb-2 mt-0" @close="clearErrors()">
            <ul>
              <div v-for="(error, key) in errors" :key="key">
                <li class="text-wrap text-break">
                  {{ error }}
                </li>
              </div>
            </ul>
          </Message>
        </div>
        <dl>
          <dt>User Note*</dt>
          <dd>
            <TextArea v-model="message" placeholder="Add User Note" class="inputWidth" rows="5" />
          </dd>
        </dl>
      </div>
    </div>
    <teleport v-if="isMounted" to="#dialogTemplate">
      <div class="p-dialog-footer">
        <Button label="Submit" @click="submit()" />
      </div>
    </teleport>
  </div>
</template>

<script setup>
import useUserNotes from "@/composables/useUserNotes";
import { useMounted } from "@vueuse/core";
import { nextTick, ref } from "vue";

import Button from "primevue/button";
import Message from "primevue/message";
import TextArea from "primevue/textarea";
import _ from "lodash";

const props = defineProps({
  did: {
    type: String,
    required: true,
  },
});

const { addUserNote } = useUserNotes();

const emit = defineEmits(["refreshAndClose"]);

const errors = ref([]);
const message = ref(null);
const isMounted = ref(useMounted());

const scrollToErrors = async () => {
  const errorMessages = document.getElementById("error-message");
  await nextTick();
  errorMessages.scrollIntoView();
};

const submit = async () => {
  const userNoteObject = {
    dids: props.did,
    message: message.value,
  };

  errors.value = [];
  if (_.isEmpty(userNoteObject["message"])) {
    errors.value.push("Message is a required field.");
  }

  if (!_.isEmpty(errors.value)) {
    scrollToErrors();
    return;
  }

  let response = await addUserNote(userNoteObject);
  response = response.data.userNote[0];

  if (!_.isEmpty(_.get(response, "errors", null))) {
    errors.value.push(response["errors"][0]);
    scrollToErrors();
    return;
  }

  emit("refreshAndClose");
};

const clearErrors = () => {
  errors.value = [];
};
</script>

<style>
.add-user-note-dialog {
  width: 98%;
}
</style>
