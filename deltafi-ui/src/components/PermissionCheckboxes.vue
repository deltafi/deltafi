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
  <div class="permission-checkboxes">
    <div v-for="permissions, category in appPermissionsByCategory" :key="category">
      <Divider type="dashed">
        <span class="text-muted font-italic font-weight-light">{{ category }}</span>
      </Divider>
      <span v-for="permission in permissions" :key="permission" class="field-checkbox">
        <Checkbox v-model="selectedPermissions" :input-id="permission.name" name="permission" :value="permission.name" :disabled="readOnly" />
        <label :for="permission.name">
          <PermissionPill :permission="permission" :enabled="modelValue.includes(permission.name)" />
        </label>
      </span>
      <div class="spacer" />
    </div>
  </div>
</template>

<script setup>
import { watch, onMounted, ref } from 'vue'
import Divider from 'primevue/divider';
import Checkbox from 'primevue/checkbox';
import PermissionPill from "@/components/PermissionPill.vue";
import usePermissions from "@/composables/usePermissions";

const emit = defineEmits(['update:modelValue'])
const { appPermissionsByCategory } = usePermissions();
const selectedPermissions = ref([]);
const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  readOnly: {
    type: Boolean,
    required: false,
    default: false
  }
});

onMounted(() => {
  selectedPermissions.value = props.modelValue
})

watch(selectedPermissions, () => {
  emit('update:modelValue', selectedPermissions.value)
})
</script>

<style>
.permission-checkboxes {
  .spacer {
    clear: both;
  }

  .p-divider {
    margin-top: 8px;
    margin-bottom: 8px;
  }

  .field-checkbox {
    display: flex;
    float: left;
    width: 25%;

    label {
      display: flex;
      align-items: center;
      margin-top: .15rem;
      margin-left: 0.4rem;
    }
  }
}
</style>
