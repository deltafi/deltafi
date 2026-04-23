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
  <div class="color-palette-picker" ref="pickerRef">
    <button
      v-tooltip.bottom="'Change color theme'"
      class="picker-btn"
      aria-label="Change color theme"
      @click="togglePopup"
    >
      <i class="fas fa-palette" />
    </button>
    <Transition name="popup">
      <div v-if="open" class="palette-popup">
        <div class="palette-grid">
          <button
            v-for="color in colors"
            :key="color.name"
            class="color-swatch"
            :class="{ active: currentColor === color.name }"
            :style="{ backgroundColor: color.value }"
            :aria-label="color.name"
            v-tooltip.bottom="color.name"
            @click="selectColor(color.name)"
          />
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { updatePrimaryPalette } from "@primeuix/themes";

const STORAGE_KEY = "deltafi-primary-color";

const colors = [
  { name: "Blue", value: "#3b82f6" },
  { name: "Indigo", value: "#6366f1" },
  { name: "Violet", value: "#8b5cf6" },
  { name: "Purple", value: "#a855f7" },
  { name: "Fuchsia", value: "#d946ef" },
  { name: "Pink", value: "#ec4899" },
  { name: "Rose", value: "#f43f5e" },
  { name: "Red", value: "#ef4444" },
  { name: "Orange", value: "#f97316" },
  { name: "Amber", value: "#f59e0b" },
  { name: "Yellow", value: "#eab308" },
  { name: "Lime", value: "#84cc16" },
  { name: "Green", value: "#22c55e" },
  { name: "Emerald", value: "#10b981" },
  { name: "Teal", value: "#14b8a6" },
  { name: "Cyan", value: "#06b6d4" },
  { name: "Sky", value: "#0ea5e9" },
];

const open = ref(false);
const pickerRef = ref<HTMLElement | null>(null);
const currentColor = ref(loadStoredColor());

function loadStoredColor(): string {
  return localStorage.getItem(STORAGE_KEY) || "Blue";
}

function buildPalette(name: string): Record<string, string> {
  const key = name.toLowerCase();
  return {
    50: `{${key}.50}`,
    100: `{${key}.100}`,
    200: `{${key}.200}`,
    300: `{${key}.300}`,
    400: `{${key}.400}`,
    500: `{${key}.500}`,
    600: `{${key}.600}`,
    700: `{${key}.700}`,
    800: `{${key}.800}`,
    900: `{${key}.900}`,
    950: `{${key}.950}`,
  };
}

function selectColor(name: string) {
  currentColor.value = name;
  localStorage.setItem(STORAGE_KEY, name);
  updatePrimaryPalette(buildPalette(name));
  open.value = false;
}

function togglePopup() {
  open.value = !open.value;
}

function handleClickOutside(event: MouseEvent) {
  if (pickerRef.value && !pickerRef.value.contains(event.target as HTMLElement)) {
    open.value = false;
  }
}

// Apply stored color on mount
onMounted(() => {
  document.addEventListener("click", handleClickOutside);
  const stored = loadStoredColor();
  if (stored !== "Blue") {
    updatePrimaryPalette(buildPalette(stored));
  }
});

onUnmounted(() => {
  document.removeEventListener("click", handleClickOutside);
});
</script>

<style scoped>
.picker-btn {
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
}

.picker-btn:hover {
  background-color: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

.color-palette-picker {
  position: relative;
}

.palette-popup {
  position: absolute;
  top: calc(100% + 0.5rem);
  right: 0;
  padding: 0.75rem;
  background: var(--surface-card);
  border: 1px solid var(--df-border-color);
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  z-index: 1000;
}

.palette-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 0.4rem;
}

.color-swatch {
  width: 1.6rem;
  height: 1.6rem;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
  outline: none;
}

.color-swatch:hover {
  transform: scale(1.2);
  box-shadow: 0 0 0 2px var(--surface-card), 0 0 0 4px currentColor;
}

.color-swatch.active {
  border-color: #ffffff;
  box-shadow: 0 0 0 2px var(--surface-card), 0 0 0 4px currentColor;
  transform: scale(1.15);
}

/* Transition */
.popup-enter-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.popup-leave-active {
  transition: opacity 0.1s ease, transform 0.1s ease;
}

.popup-enter-from,
.popup-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
