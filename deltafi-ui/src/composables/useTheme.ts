/*
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
*/

import { ref, computed, watch } from "vue";

export type ThemeMode = "light" | "dark" | "system";

const STORAGE_KEY = "deltafi-theme-mode";
const DARK_MODE_CLASS = "dark-mode";

const mode = ref<ThemeMode>(loadStoredMode());

function loadStoredMode(): ThemeMode {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === "light" || stored === "dark" || stored === "system") {
    return stored;
  }
  return "light";
}

function getSystemPrefersDark(): boolean {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

const isDark = computed(() => {
  if (mode.value === "system") {
    return getSystemPrefersDark();
  }
  return mode.value === "dark";
});

function applyTheme() {
  const html = document.documentElement;
  if (isDark.value) {
    html.classList.add(DARK_MODE_CLASS);
  } else {
    html.classList.remove(DARK_MODE_CLASS);
  }
}

// Watch for mode changes
watch(mode, (newMode) => {
  localStorage.setItem(STORAGE_KEY, newMode);
  applyTheme();
});

// Listen for system preference changes
if (typeof window !== "undefined") {
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
    if (mode.value === "system") {
      applyTheme();
    }
  });
}

// Apply on load
applyTheme();

export default function useTheme() {
  function setMode(newMode: ThemeMode) {
    mode.value = newMode;
  }

  function toggleDarkMode() {
    if (mode.value === "dark") {
      mode.value = "light";
    } else {
      mode.value = "dark";
    }
  }

  function cycleMode() {
    const modes: ThemeMode[] = ["light", "dark", "system"];
    const currentIndex = modes.indexOf(mode.value);
    mode.value = modes[(currentIndex + 1) % modes.length];
  }

  return {
    mode,
    isDark,
    setMode,
    toggleDarkMode,
    cycleMode,
  };
}
