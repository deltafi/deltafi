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
  <div class="dark-mode-toggle">
    <button
      v-tooltip.bottom="tooltipText"
      class="toggle-btn"
      :aria-label="tooltipText"
      @click="toggleDarkMode"
    >
      <i :class="iconClass" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import useTheme from "@/composables/useTheme";

const { isDark, toggleDarkMode } = useTheme();

const iconClass = computed(() => (isDark.value ? "pi pi-moon" : "pi pi-sun"));

const tooltipText = computed(() =>
  isDark.value ? "Dark mode (click for light)" : "Light mode (click for dark)"
);
</script>

<style scoped lang="scss">
.toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  background: rgba(255, 255, 255, 0.1);
  border: none;
  border-radius: 50%;
  cursor: pointer;
  color: var(--df-clock-color);
  font-size: 1rem;
  transition: background-color 0.2s, color 0.2s;

  &:hover {
    background-color: rgba(255, 255, 255, 0.2);
    color: #ffffff;
  }
}
</style>
