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
  <div id="error-message" class="lookup-table-configuration-dialog">
    <div class="lookup-table-panel">
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
          <dt>Name*</dt>
          <dd>
            <InputText v-model.trim="model['name']" placeholder="Lookup Table Name" :disabled="editLookupTable" class="inputWidth" />
          </dd>
          <dt>Columns*</dt>
          <dd>
            <Chips
              v-model="model['columns']"
              separator=","
              :placeholder="!_.isEmpty(model['columns']) ? null : 'Lookup Table Columns'"
              class="inputWidth"
              :pt="{
                container: {
                  class: 'chip-container',
                },
              }"
            />
          </dd>
          <dt>Key Columns*</dt>
          <dd>
            <Chips
              v-model="model['keyColumns']"
              separator=","
              :placeholder="!_.isEmpty(model['keyColumns']) ? null : 'Lookup Table Key Columns'"
              class="inputWidth"
              :pt="{
                container: {
                  class: 'chip-container',
                },
              }"
            />
          </dd>
          <dt>Service Backed*</dt>
          <dd>
            <Checkbox v-model="model['serviceBacked']" :binary="true" />
          </dd>
          <dt>Backing Service Active*</dt>
          <dd>
            <Checkbox v-model="model['backingServiceActive']" :binary="true" />
          </dd>
          <dt>Pull Through*</dt>
          <dd>
            <Checkbox v-model="model['pullThrough']" :binary="true" />
          </dd>
          <dt>Refresh Duration</dt>
          <dd>
            <InputText v-model="model['refreshDuration']" placeholder="Duration in ISO 1806 notation. e.g. PT1H, P23DT23H, PT1M" class="inputWidth" />
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
import useLookupTable from "@/composables/useLookupTable";
import useNotifications from "@/composables/useNotifications";
import { computed, nextTick, onBeforeMount, reactive, ref } from "vue";
import { useMounted } from "@vueuse/core";

import Button from "primevue/button";
import Checkbox from "primevue/checkbox";
import Chips from "primevue/chips";
import InputText from "primevue/inputtext";
import Message from "primevue/message";
import _ from "lodash";

const props = defineProps({
  rowDataProp: {
    type: Object,
    required: false,
    default: null,
  },
  editLookupTable: {
    type: Boolean,
    required: false,
    default: false,
  },
});

const { createLookupTable } = useLookupTable();
const notify = useNotifications();
const { editLookupTable } = reactive(props);
const emit = defineEmits(["refreshAndClose"]);

const errors = ref([]);

const lookupTableTemplate = {
  name: null,
  columns: null,
  keyColumns: null,
  serviceBacked: false,
  backingServiceActive: false,
  pullThrough: false,
  refreshDuration: null,
  lastRefresh: null,
};

const rowData = ref(_.cloneDeepWith(_.isEmpty(props.rowDataProp) ? lookupTableTemplate : props.rowDataProp));

const model = computed({
  get() {
    return new Proxy(rowData.value, {
      set(obj, key, value) {
        model.value = { ...obj, [key]: value };
        return true;
      },
    });
  },
  set(newValue) {
    Object.assign(
      rowData.value,
      _.mapValues(newValue, (v) => (v === "" ? null : v))
    );
  },
});

onBeforeMount(async () => {});

const isMounted = ref(useMounted());

const scrollToErrors = async () => {
  const errorMessages = document.getElementById("error-message");
  await nextTick();
  errorMessages.scrollIntoView();
};

const submit = async () => {
  let lookupTableObject = _.cloneDeepWith(model.value);
  lookupTableObject = _.omit(lookupTableObject, "totalRows");

  errors.value = [];
  if (_.isEmpty(lookupTableObject["name"])) {
    errors.value.push("Name is a required field.");
  } else {
    if (_.includes(lookupTableObject["name"], " ")) {
      errors.value.push("Name cannot contain spaces.");
    }
  }

  if (_.isEmpty(lookupTableObject["columns"])) {
    errors.value.push("Columns is a required field.");
  } else {
    if (_.uniq(lookupTableObject["columns"]).length !== lookupTableObject["columns"].length) {
      errors.value.push("Cannot have duplicate Columns.");
    }
  }

  if (_.isEmpty(lookupTableObject["keyColumns"])) {
    errors.value.push("Key Columns is a required field.");
  } else {
    if (_.uniq(lookupTableObject["keyColumns"]).length !== lookupTableObject["keyColumns"].length) {
      errors.value.push("Cannot have duplicate Key Columns.");
    }
  }

  if (!_.every(lookupTableObject["keyColumns"], (str) => _.includes(lookupTableObject["columns"], str))) {
    errors.value.push("Key Columns must be a subset of Columns.");
  }

  if (!_.isEmpty(errors.value)) {
    scrollToErrors();
    return;
  }

  const response = await createLookupTable(lookupTableObject);
  if (!_.isEmpty(response.errors) || _.isEmpty(response.data) || !_.isEmpty(response.data.createLookupTable.errors)) {
    errors.value = response.data?.createLookupTable?.errors || _.map(response.errors, (error) => error.message);
    scrollToErrors();
    return;
  }
  notify.success(`Table ${lookupTableObject["name"]} added successfully`);
  emit("refreshAndClose");
};

const clearErrors = () => {
  errors.value = [];
};
</script>

<style>
.lookup-table-configuration-dialog {
  width: 98%;

  .lookup-table-panel {
    dt {
      margin-bottom: 0rem;
    }

    dd {
      margin-bottom: 1.2rem;
    }

    dl {
      margin-bottom: 1rem;
    }

    .p-panel-content {
      padding-bottom: 0.25rem !important;
    }
  }

  .inputWidth {
    width: 90% !important;
  }

  .chip-container {
    width: 100% !important;
  }
}
</style>
