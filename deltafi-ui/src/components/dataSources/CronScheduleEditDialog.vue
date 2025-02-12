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
  <Dialog v-bind="$attrs" :header="header" :style="{ width: '60vw', height: 'auto' }" :maximizable="true" :modal="true" :dismissable-mask="true" :draggable="false" :position="modelPosition">
    <div>
      <dl>
        <dd>
          <CronLight v-model="model['cronSchedule']" format="quartz" @error="errors.push($event)" />
          <InputText v-model="model['cronSchedule']" placeholder="e.g.	*/5 * * * * *" style="margin-top: 0.5rem" class="inputWidth" />
        </dd>
      </dl>
      <div class="p-dialog-footer">
        <Button label="Submit" @click="submit()" />
      </div>
    </div>
  </Dialog>
</template>

<script setup>
import Dialog from "primevue/dialog";
import { computed, ref, useAttrs, watch } from "vue";
import { CronLight } from "@vue-js-cron/light";
import "@vue-js-cron/light/dist/light.css";

import InputText from "primevue/inputtext";
import Button from "primevue/button";
import _ from "lodash";
import useDataSource from "@/composables/useDataSource";
import useNotifications from "@/composables/useNotifications";

const { setTimedDataSourceCronSchedule } = useDataSource();
const attrs = useAttrs();
const model = ref({});
const emit = defineEmits(["reloadDataSources", "close"]);
const errors = ref([]);
const notify = useNotifications();
const props = defineProps({
  data: {
    type: Object,
    required: true,
  },
});

watch(
  () => props.data,
  (newValue) => {
    model.value = _.cloneDeep(newValue);
  }
);

const submit = async () => {
  errors.value = [];
  const response = await setTimedDataSourceCronSchedule(model.value["name"], model.value["cronSchedule"]);
  if (!_.isEmpty(_.get(response, "errors", null))) {
    errors.value.push(response["errors"][0]["message"]);
  }
  if (errors.value.length === 0) {
    notify.success("Cron Schedule Set Successfully", `Cron Schedule for ${model.value["name"]} set to ${model.value["cronSchedule"]}`);
    emit("reloadDataSources");
    emit("close");
  } else {
    notify.error("Error Setting Cron Schedule", errors.value);
  }
};

const header = computed(() => `Set Cron Schedule for ${props.data.name}`);

const modelPosition = computed(() => {
  return _.get(attrs, "model-position", "top");
});
</script>

<style>
.inputWidth {
  width: 60% !important;
}

.p-filled.capitalizeText {
  text-transform: uppercase;
}
</style>
