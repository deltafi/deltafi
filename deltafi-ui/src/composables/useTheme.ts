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

export interface ThemeDefinition {
  /** Stable id persisted to localStorage and used by setTheme(). */
  id: string;
  /** Human-readable name shown in the picker. */
  label: string;
  /** PrimeIcons class for the picker button. */
  icon: string;
  /** Resolved color mode. "system" follows the OS preference. */
  mode: ThemeMode;
  /**
   * Classes added to <html> when this theme is active. Dark-flavored themes
   * include "dark-mode" so they inherit every existing dark component style;
   * a theme-specific class (e.g. "theme-dracula") then overrides the palette.
   */
  classes: string[];
  /** Swatch preview shown in the picker: [surface, accent]. */
  swatch: { bg: string; accent: string };
  /**
   * Name of the PrimeVue palette (matching the accent picker's color names)
   * used as this theme's built-in accent when the "Default" accent is chosen.
   */
  accent: string;
}

/**
 * Theme registry. Adding a new named theme is two steps:
 *   1. add an entry here (with its "theme-<id>" class), and
 *   2. add a matching `html.theme-<id>` token-override block in global.scss.
 */
export const THEMES: ThemeDefinition[] = [
  { id: "light", label: "Light", icon: "pi pi-sun", mode: "light", classes: [], swatch: { bg: "#ffffff", accent: "#3b82f6" }, accent: "Blue" },
  { id: "dark", label: "Dark", icon: "pi pi-moon", mode: "dark", classes: ["dark-mode"], swatch: { bg: "#0f172a", accent: "#60a5fa" }, accent: "Blue" },
  { id: "dracula", label: "Dracula", icon: "pi pi-star", mode: "dark", classes: ["dark-mode", "theme-dracula"], swatch: { bg: "#282a36", accent: "#bd93f9" }, accent: "Violet" },
  { id: "mono", label: "Mono", icon: "pi pi-code", mode: "dark", classes: ["dark-mode", "theme-mono"], swatch: { bg: "#0d0d0e", accent: "#d29922" }, accent: "Amber" },
  { id: "solarized-light", label: "Solarized Light", icon: "fas fa-cloud-sun", mode: "light", classes: ["theme-solarized-light"], swatch: { bg: "#fdf6e3", accent: "#268bd2" }, accent: "Blue" },
  { id: "solarized-dark", label: "Solarized Dark", icon: "fas fa-cloud-moon", mode: "dark", classes: ["dark-mode", "theme-solarized-dark"], swatch: { bg: "#002b36", accent: "#2aa198" }, accent: "Teal" },
  { id: "high-contrast", label: "High Contrast", icon: "pi pi-eye", mode: "dark", classes: ["dark-mode", "theme-high-contrast"], swatch: { bg: "#000000", accent: "#ffff00" }, accent: "Yellow" },
  { id: "system", label: "System", icon: "pi pi-desktop", mode: "system", classes: [], swatch: { bg: "#64748b", accent: "#cbd5e1" }, accent: "Blue" },
];

/** Every class this composable may add to <html>, for clean removal. */
const MANAGED_CLASSES = ["dark-mode", "theme-dracula", "theme-mono", "theme-solarized-light", "theme-solarized-dark", "theme-high-contrast"];

const STORAGE_KEY = "deltafi-theme";
const LEGACY_STORAGE_KEY = "deltafi-theme-mode";
const MONO_STYLE_KEY = "deltafi-mono-style";

/**
 * Independent of the color theme: a "terminal" treatment (monospace font +
 * sharp corners) that can be layered on top of any theme via the `mono-style`
 * class on <html>.
 */
const MONO_STYLE_CLASS = "mono-style";

const themeId = ref<string>(loadStoredTheme());
const monoStyle = ref<boolean>(localStorage.getItem(MONO_STYLE_KEY) === "true");

function loadStoredTheme(): string {
  const stored = localStorage.getItem(STORAGE_KEY) ?? localStorage.getItem(LEGACY_STORAGE_KEY);
  if (stored && THEMES.some((t) => t.id === stored)) {
    return stored;
  }
  return "light";
}

function getThemeDef(id: string): ThemeDefinition {
  return THEMES.find((t) => t.id === id) ?? THEMES[0];
}

const activeTheme = computed(() => getThemeDef(themeId.value));

function getSystemPrefersDark(): boolean {
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
}

const isDark = computed(() => {
  if (activeTheme.value.mode === "system") {
    return getSystemPrefersDark();
  }
  return activeTheme.value.mode === "dark";
});

function applyTheme() {
  const html = document.documentElement;
  html.classList.remove(...MANAGED_CLASSES);

  const def = activeTheme.value;
  const classes = def.mode === "system" ? (getSystemPrefersDark() ? ["dark-mode"] : []) : def.classes;
  if (classes.length) {
    html.classList.add(...classes);
  }

  html.classList.toggle(MONO_STYLE_CLASS, monoStyle.value);
}

// Persist and re-apply whenever the selected theme changes.
watch(themeId, (id) => {
  localStorage.setItem(STORAGE_KEY, id);
  applyTheme();
});

// Persist and re-apply whenever the terminal style is toggled.
watch(monoStyle, (enabled) => {
  localStorage.setItem(MONO_STYLE_KEY, String(enabled));
  applyTheme();
});

// React to OS preference changes while the "system" theme is active.
if (typeof window !== "undefined") {
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
    if (activeTheme.value.mode === "system") {
      applyTheme();
    }
  });
}

// Apply on load.
applyTheme();

export default function useTheme() {
  function setTheme(id: string) {
    if (THEMES.some((t) => t.id === id)) {
      themeId.value = id;
    }
  }

  // Backwards-compatible binary toggle used by DarkModeToggle.
  function toggleDarkMode() {
    themeId.value = isDark.value ? "light" : "dark";
  }

  function setMonoStyle(enabled: boolean) {
    monoStyle.value = enabled;
  }

  function toggleMonoStyle() {
    monoStyle.value = !monoStyle.value;
  }

  return {
    themeId,
    themes: THEMES,
    activeTheme,
    isDark,
    monoStyle,
    setTheme,
    toggleDarkMode,
    setMonoStyle,
    toggleMonoStyle,
  };
}
