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
        <div class="popup-label">Theme</div>
        <div class="theme-list">
          <button
            v-for="theme in themes"
            :key="theme.id"
            class="theme-option"
            :class="{ active: themeId === theme.id }"
            :aria-label="theme.label"
            @click="selectTheme(theme.id)"
          >
            <span class="theme-name"><i :class="theme.icon" /> {{ theme.label }}</span>
            <span class="theme-swatch" :style="{ backgroundColor: theme.swatch.bg }">
              <span class="theme-swatch-accent" :style="{ backgroundColor: theme.swatch.accent }" />
            </span>
            <span class="theme-check-slot">
              <i v-if="themeId === theme.id" class="fas fa-check theme-check" />
            </span>
          </button>
        </div>
        <div class="popup-label">Style</div>
        <button
          class="mono-toggle"
          :class="{ active: monoStyle }"
          role="switch"
          :aria-checked="monoStyle"
          aria-label="Terminal style (monospace and sharp corners)"
          @click="toggleMonoStyle()"
        >
          <span class="theme-name"><i class="pi pi-desktop" /> Terminal style</span>
          <span class="switch" :class="{ on: monoStyle }"><span class="switch-knob" /></span>
        </button>
        <div class="popup-label">Accent</div>
        <div class="palette-grid">
          <button
            class="color-swatch default-swatch"
            :class="{ active: currentColor === 'default' }"
            aria-label="Default (match theme)"
            v-tooltip.bottom="'Default (match theme)'"
            @click="selectDefault()"
          />
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
import { ref, watch, onMounted, onUnmounted } from "vue";
import { updatePrimaryPalette } from "@primeuix/themes";
import useTheme from "@/composables/useTheme";

const { themes, themeId, setTheme, activeTheme, monoStyle, toggleMonoStyle } = useTheme();

const STORAGE_KEY = "deltafi-primary-color";
const DEFAULT_ACCENT = "default";

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
  return localStorage.getItem(STORAGE_KEY) || DEFAULT_ACCENT;
}

// Resolve which named palette to apply: the theme's accent when on "default",
// otherwise the explicitly chosen color.
function paletteNameFor(selection: string): string {
  return selection === DEFAULT_ACCENT ? activeTheme.value.accent : selection;
}

function applyPalette() {
  updatePrimaryPalette(buildPalette(paletteNameFor(currentColor.value)));
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
  applyPalette();
  open.value = false;
}

function selectDefault() {
  currentColor.value = DEFAULT_ACCENT;
  localStorage.setItem(STORAGE_KEY, DEFAULT_ACCENT);
  applyPalette();
  open.value = false;
}

function selectTheme(id: string) {
  setTheme(id);
}

// When on the Default accent, follow the theme as it changes.
watch(activeTheme, () => {
  if (currentColor.value === DEFAULT_ACCENT) {
    applyPalette();
  }
});

function togglePopup() {
  open.value = !open.value;
}

function handleClickOutside(event: MouseEvent) {
  if (pickerRef.value && !pickerRef.value.contains(event.target as HTMLElement)) {
    open.value = false;
  }
}

// Apply stored accent on mount (resolves "default" against the active theme).
onMounted(() => {
  document.addEventListener("click", handleClickOutside);
  applyPalette();
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
  min-width: 19rem;
  padding: 0.75rem;
  background: var(--surface-card);
  border: 1px solid var(--df-border-color);
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  z-index: 1000;
}

.popup-label {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--df-muted-color);
  margin-bottom: 0.4rem;
}

.theme-list {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  margin-bottom: 0.75rem;
}

.theme-option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.35rem 0.4rem;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  color: var(--df-body-color);
  font-size: 0.85rem;
  text-align: left;
  transition: background-color 0.15s, border-color 0.15s;
}

.theme-option:hover {
  background-color: var(--df-dropdown-hover);
}

.theme-option.active {
  border-color: var(--primary-color);
}

.theme-swatch {
  position: relative;
  flex-shrink: 0;
  width: 1.1rem;
  height: 1.1rem;
  border-radius: 4px;
  border: 1px solid var(--df-border-color);
}

.theme-swatch-accent {
  position: absolute;
  right: 1px;
  bottom: 1px;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 2px;
}

.theme-name {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  white-space: nowrap;
}

.theme-name i {
  width: 1rem;
  text-align: center;
  font-size: 0.9rem;
  line-height: 1;
}

.theme-check-slot {
  flex-shrink: 0;
  width: 0.9rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.theme-check {
  color: var(--primary-color);
  font-size: 0.75rem;
}

.mono-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  width: 100%;
  padding: 0.35rem 0.4rem;
  margin-bottom: 0.75rem;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  color: var(--df-body-color);
  font-size: 0.85rem;
  text-align: left;
  transition: background-color 0.15s;
}

.mono-toggle:hover {
  background-color: var(--df-dropdown-hover);
}

.switch {
  flex-shrink: 0;
  position: relative;
  width: 1.9rem;
  height: 1.05rem;
  border-radius: 999px;
  background-color: var(--df-border-color);
  transition: background-color 0.15s;
}

.switch.on {
  background-color: var(--primary-color);
}

.switch-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 0.8rem;
  height: 0.8rem;
  border-radius: 50%;
  background-color: #ffffff;
  transition: transform 0.15s;
}

.switch.on .switch-knob {
  transform: translateX(0.85rem);
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

.default-swatch {
  position: relative;
  overflow: hidden;
  background-color: transparent;
  border: 2px solid var(--df-muted-color);
}

.default-swatch::after {
  content: "";
  position: absolute;
  top: 50%;
  left: -2px;
  right: -2px;
  height: 2px;
  margin-top: -1px;
  background-color: #ef4444;
  transform: rotate(-45deg);
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
