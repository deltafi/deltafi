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
  <span>
    <Avatar v-tooltip.left="tooltip" :label="label" shape="circle" class="user-badge" />
  </span>
</template>

<script setup>
import Avatar from "primevue/avatar";
import useCurrentUser from "@/composables/useCurrentUser";
import { ref, onMounted } from "vue";

const { currentUser } = useCurrentUser();

const tooltip = ref("Unknown");
const label = ref("?");

onMounted(() => {
  tooltip.value = currentUser.name;
  label.value = currentUser.name.charAt(0).toUpperCase();
});
</script>

<style scoped>
.user-badge {
  color: var(--white);
  background-color: var(--gray-700);
  border: 1px solid var(--gray-600);
  font-weight: 500;
  cursor: default;
}
</style>
