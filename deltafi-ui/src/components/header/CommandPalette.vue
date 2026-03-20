<!--
   ABOUTME: Command palette modal for quick navigation and DeltaFile search.
   ABOUTME: Triggered by Cmd+K (Mac) or Ctrl+K (Windows), provides filter pills for search modes.
-->
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
  <Dialog v-model:visible="visible" modal :dismissableMask="!pendingAction" :closable="false" :showHeader="false" aria-label="Command palette" class="command-palette-dialog" :pt="{ root: { class: 'command-palette-root' } }">
    <div class="command-palette">
      <!-- Confirmation Overlay -->
      <div v-if="pendingAction" class="confirmation-overlay" @keydown="handleConfirmationKeydown">
        <div class="confirmation-content">
          <div class="confirmation-icon">
            <i class="fas fa-exclamation-triangle" />
          </div>
          <h3 class="confirmation-title">{{ pendingAction.name }}</h3>
          <p class="confirmation-message">{{ pendingAction.message }}</p>
          <p class="confirmation-question">Are you sure you want to continue?</p>
          <div class="confirmation-actions">
            <button ref="cancelBtn" class="confirm-btn cancel" @click="cancelPendingAction">Cancel</button>
            <button class="confirm-btn confirm" @click="confirmPendingAction">Confirm</button>
          </div>
        </div>
      </div>

      <!-- Search Input -->
      <div class="search-section">
        <div class="search-input-wrapper">
          <span class="search-prefix">/</span>
          <input
            ref="searchInput"
            v-model="searchQuery"
            type="text"
            class="search-input"
            aria-label="Search pages and DeltaFiles"
            :placeholder="searchPlaceholder"
            @keydown="handleKeydown"
          >
          <span class="search-hint">Esc</span>
        </div>

        <!-- Filter Pills -->
        <div class="filter-pills">
          <button :class="['filter-pill', { active: activeFilter === 'filename' }]" @click="toggleFilter('filename')">
            Filename <kbd>{{ isMac ? '⌘' : '^' }}1</kbd>
          </button>
          <button :class="['filter-pill', { active: activeFilter === 'annotations' }]" @click="toggleFilter('annotations')">
            Annotations <kbd>{{ isMac ? '⌘' : '^' }}2</kbd>
          </button>
          <button :class="['filter-pill', { active: activeFilter === 'did' }]" @click="toggleFilter('did')">
            DID <kbd>{{ isMac ? '⌘' : '^' }}3</kbd>
          </button>
        </div>
      </div>

      <!-- Results Section -->
      <div ref="resultsSection" class="results-section">
        <!-- Filtered Search command (when filter is active and has query) -->
        <div v-if="activeFilter && searchQuery.trim() && !didValidationError" class="filtered-search-section">
          <div
            :data-index="0"
            :class="['command-item selectable-item filtered-search-item', { selected: selectedIndex === 0 }]"
            @click="executeFilteredSearch"
            @mouseenter="selectedIndex = 0"
          >
            <span class="command-left">
              <i class="fas fa-search command-icon" />
              <span class="command-name">Search {{ filterLabel }} for "{{ searchQuery }}"</span>
            </span>
            <span class="command-right">
              <kbd class="command-hotkey">↵</kbd>
            </span>
          </div>
        </div>

        <!-- DeltaFile Search Results (when filter is active and has query) -->
        <div v-if="activeFilter && searchQuery.trim()" class="deltafile-results-section">
          <!-- Loading indicator -->
          <div v-if="searchLoading" class="search-loading">
            <i class="fas fa-spinner fa-spin" />
            <span>Searching DeltaFiles...</span>
          </div>

          <!-- Search Results -->
          <template v-else-if="deltaFileResults.length > 0">
            <div class="group-header">DeltaFiles</div>
            <div
              v-for="(result, idx) in deltaFileResults"
              :key="result.did"
              :data-index="idx + filteredSearchOffset"
              :class="['result-item selectable-item', { selected: idx + filteredSearchOffset === selectedIndex }]"
              @click="executeDeltaFile(result)"
              @mouseenter="selectedIndex = idx + filteredSearchOffset"
            >
              <div class="result-main">
                <i class="far fa-file result-icon" />
                <span class="result-name">{{ result.name || '(unnamed)' }}</span>
                <span class="result-did">{{ result.did }}</span>
              </div>
              <div class="result-meta">
                <span class="result-datasource">{{ result.dataSource }}</span>
                <span class="result-date">{{ formatTimestamp(result.modified, "YYYY-MM-DD HH:mm") }}</span>
              </div>
              <div v-if="activeFilter === 'annotations' && getMatchingAnnotations(result).length > 0" class="result-annotations">
                <span v-for="anno in getMatchingAnnotations(result)" :key="anno.key" class="result-annotation">
                  <span class="anno-key">{{ anno.key }}</span><span class="anno-sep">=</span><span class="anno-value">{{ anno.value }}</span>
                </span>
              </div>
            </div>
          </template>

          <!-- DID validation error -->
          <div v-else-if="didValidationError" class="validation-error">
            <i class="fas fa-exclamation-triangle" />
            <span>{{ didValidationError }}</span>
          </div>

          <!-- No DeltaFile results -->
          <div v-else class="search-empty-inline">
            <span>No DeltaFiles found for "{{ searchQuery }}"</span>
          </div>
        </div>

        <!-- Commands List (always shown if there are matches) -->
        <div v-if="groupedCommands.length > 0" class="commands-section">
          <template v-for="group in groupedCommands" :key="group.name">
            <div v-if="group.commands.length > 0" class="command-group">
              <div class="group-header">{{ group.name }}</div>
              <div
                v-for="(cmd, idx) in group.commands"
                :key="cmd.path || cmd.url || cmd.name"
                :data-index="getCommandSelectedIndex(group.name, idx)"
                :class="['command-item selectable-item', { selected: getCommandSelectedIndex(group.name, idx) === selectedIndex }]"
                @click="selectAndExecute(cmd)"
                @mouseenter="selectedIndex = getCommandSelectedIndex(group.name, idx)"
              >
                <span class="command-left">
                  <i :class="cmd.icon" class="command-icon" />
                  <span class="command-name">{{ cmd.name }}</span>
                </span>
                <span class="command-right">
                  <span v-if="cmd.badge && cmd.badge().visible" :class="cmd.badge().class">{{ cmd.badge().value }}</span>
                  <span v-if="cmd.hotkey" class="command-hotkey">{{ formatHotkey(cmd.hotkey) }}</span>
                  <i v-if="cmd.url" class="fas fa-external-link-alt external-icon" />
                </span>
              </div>
            </div>
          </template>
        </div>

        <!-- Empty state when no commands match and user has typed something -->
        <div v-if="flatCommandList.length === 0 && searchQuery.trim() && !activeFilter" class="search-empty">
          <span>No pages match "{{ searchQuery }}"</span>
        </div>
      </div>

      <!-- Footer -->
      <div class="command-footer">
        <span class="footer-hint"><kbd>&#8629;</kbd> to select</span>
        <span class="footer-hint"><kbd>&uarr;</kbd><kbd>&darr;</kbd> to navigate</span>
        <span class="footer-hint"><kbd>Esc</kbd> to close</span>
      </div>
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import Dialog from "primevue/dialog";
import { computed, ref, watch, nextTick } from "vue";
import { useRouter } from "vue-router";
import useCommandPalette, { type CommandItem, type SearchFilterType, type DeltaFileResult } from "@/composables/useCommandPalette";
import useUtilFunctions from "@/composables/useUtilFunctions";

const router = useRouter();

const {
  visible,
  searchQuery,
  activeFilter,
  filteredCommands,
  deltaFileResults,
  searchLoading,
  selectedIndex,
  didValidationError,
  pendingAction,
  close,
  setFilter,
  clearFilter,
  executeCommand,
  executeDeltaFile,
  executeSearch,
  moveSelection,
  confirmPendingAction,
  cancelPendingAction,
} = useCommandPalette();

const { formatTimestamp } = useUtilFunctions();

const searchInput = ref<HTMLInputElement | null>(null);
const resultsSection = ref<HTMLElement | null>(null);
const cancelBtn = ref<HTMLButtonElement | null>(null);

const isMac = navigator.platform.toUpperCase().indexOf("MAC") >= 0;

// Global shortcuts that work even in the command palette
const globalShortcuts: Record<string, string> = {
  d: "/",
  s: "/deltafile/search",
  e: "/errors",
  u: "/deltafile/upload/",
  m: "/system-map",
  p: "/config/plugins",
  t: "/config/transforms",
};

const formatHotkey = (hotkey: string): string => {
  // Check if hotkey already contains a modifier symbol
  const hasModifier = /[⌥⌘⇧⌃]/.test(hotkey);
  if (hasModifier) {
    if (isMac) {
      return hotkey;
    }
    // Convert Mac symbols to Windows equivalents
    return hotkey.replace("⌥", "Alt+").replace("⌘", "Ctrl+").replace("⇧", "Shift+").replace("⌃", "Ctrl+");
  }
  // No modifier, add the standard one
  return (isMac ? "⌘" : "Ctrl+") + hotkey;
};

const searchPlaceholder = computed(() => {
  if (activeFilter.value === "did") return "Enter DeltaFile ID (full UUID required)...";
  if (activeFilter.value === "filename") return "Enter filename to search...";
  if (activeFilter.value === "annotations") return "Enter exact key or key=value (value is fuzzy)...";
  return "Search pages...";
});

const filterLabel = computed(() => {
  if (activeFilter.value === "did") return "DID";
  if (activeFilter.value === "filename") return "Filename";
  if (activeFilter.value === "annotations") return "Annotations";
  return "";
});

// Whether the "Filtered Search" item is shown (adds 1 to indices)
const filteredSearchOffset = computed(() => {
  return (activeFilter.value && searchQuery.value.trim() && !didValidationError.value) ? 1 : 0;
});

const executeFilteredSearch = () => {
  const query = searchQuery.value.trim();
  if (!query || !activeFilter.value) return;

  switch (activeFilter.value) {
    case "did":
      router.push(`/deltafile/viewer/${encodeURIComponent(query)}`);
      break;
    case "filename":
      router.push(`/deltafile/search?fileName=${encodeURIComponent(query)}`);
      break;
    case "annotations":
      router.push(`/deltafile/search?annotations=${encodeURIComponent(query)}`);
      break;
  }
  close();
};

interface CommandGroup {
  name: string;
  commands: CommandItem[];
}

const groupedCommands = computed<CommandGroup[]>(() => {
  const groups: Record<string, CommandItem[]> = {};
  const ungrouped: CommandItem[] = [];

  for (const cmd of filteredCommands.value) {
    if (cmd.group) {
      if (!groups[cmd.group]) {
        groups[cmd.group] = [];
      }
      groups[cmd.group].push(cmd);
    } else {
      ungrouped.push(cmd);
    }
  }

  const result: CommandGroup[] = [];

  if (ungrouped.length > 0) {
    result.push({ name: "Quick Access", commands: ungrouped });
  }

  const groupOrder = ["DeltaFiles", "System Map", "Fleet", "Metrics", "Configuration", "Administration", "External Links", "Commands"];
  for (const groupName of groupOrder) {
    if (groups[groupName]) {
      result.push({ name: groupName, commands: groups[groupName] });
    }
  }

  // Add any remaining groups not in the order
  for (const [groupName, commands] of Object.entries(groups)) {
    if (!groupOrder.includes(groupName)) {
      result.push({ name: groupName, commands });
    }
  }

  return result;
});

// Flat list of commands in display order for proper index tracking
const flatCommandList = computed<CommandItem[]>(() => {
  const result: CommandItem[] = [];
  for (const group of groupedCommands.value) {
    result.push(...group.commands);
  }
  return result;
});

// Total selectable items: filtered search (if shown) + DeltaFile results (if shown) + commands
const totalSelectableItems = computed(() => {
  const deltaFileCount = (activeFilter.value && searchQuery.value.trim()) ? deltaFileResults.value.length : 0;
  return filteredSearchOffset.value + deltaFileCount + flatCommandList.value.length;
});

// Offset for command indices (after filtered search + DeltaFile results)
const commandIndexOffset = computed(() => {
  const deltaFileCount = (activeFilter.value && searchQuery.value.trim()) ? deltaFileResults.value.length : 0;
  return filteredSearchOffset.value + deltaFileCount;
});

const getCommandSelectedIndex = (groupName: string, localIdx: number): number => {
  let index = commandIndexOffset.value;
  for (const group of groupedCommands.value) {
    if (group.name === groupName) {
      return index + localIdx;
    }
    index += group.commands.length;
  }
  return 0;
};

// Check if selected index is the filtered search item
const isSelectedFilteredSearch = computed(() => {
  return filteredSearchOffset.value > 0 && selectedIndex.value === 0;
});

// Check if selected index is in DeltaFile results
const isSelectedDeltaFile = computed(() => {
  if (!activeFilter.value || !searchQuery.value.trim()) return false;
  const idx = selectedIndex.value - filteredSearchOffset.value;
  return idx >= 0 && idx < deltaFileResults.value.length;
});

// Get annotations that match the search query
const getMatchingAnnotations = (result: DeltaFileResult): Array<{ key: string; value: string }> => {
  if (!result.annotations || activeFilter.value !== 'annotations') return [];

  const query = searchQuery.value.trim().toLowerCase();
  if (!query) return [];

  const matches: Array<{ key: string; value: string }> = [];

  // Parse query - could be "key" or "key=value"
  const parts = query.split('=');
  const searchKey = parts[0].toLowerCase();
  const searchValue = parts.length > 1 ? parts[1].toLowerCase() : null;

  for (const [key, value] of Object.entries(result.annotations)) {
    const keyLower = key.toLowerCase();
    const valueLower = (value || '').toLowerCase();

    if (searchValue !== null) {
      // Match both key and value
      if (keyLower.includes(searchKey) && valueLower.includes(searchValue)) {
        matches.push({ key, value });
      }
    } else {
      // Match key only
      if (keyLower.includes(searchKey)) {
        matches.push({ key, value });
      }
    }
  }

  return matches;
};

// Get the selected command (if selection is in commands section)
const getSelectedCommand = (): CommandItem | null => {
  if (isSelectedDeltaFile.value) return null;
  const commandIdx = selectedIndex.value - commandIndexOffset.value;
  if (commandIdx >= 0 && commandIdx < flatCommandList.value.length) {
    return flatCommandList.value[commandIdx];
  }
  return null;
};

const toggleFilter = (filter: SearchFilterType) => {
  if (activeFilter.value === filter) {
    clearFilter();
  } else {
    setFilter(filter);
  }
};

const selectAndExecute = (cmd: CommandItem) => {
  executeCommand(cmd);
};

const handleKeydown = (event: KeyboardEvent) => {
  // If confirmation is showing, handle confirmation-specific keys
  if (pendingAction.value) {
    if (event.key === "Escape") {
      event.preventDefault();
      cancelPendingAction();
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      confirmPendingAction();
      return;
    }
    // Block other keys during confirmation
    return;
  }

  // Handle global shortcuts (Cmd/Ctrl + key)
  if (event.metaKey || event.ctrlKey) {
    const key = event.key.toLowerCase();

    // Cmd+K toggles the palette (close it)
    if (key === "k") {
      event.preventDefault();
      event.stopPropagation();
      close();
      return;
    }

    // Cmd+1/2/3 for filter selection (palette-only shortcuts)
    if (key === "1") {
      event.preventDefault();
      toggleFilter("filename");
      return;
    }
    if (key === "2") {
      event.preventDefault();
      toggleFilter("annotations");
      return;
    }
    if (key === "3") {
      event.preventDefault();
      toggleFilter("did");
      return;
    }

    // Other global shortcuts
    const path = globalShortcuts[key];
    if (path) {
      event.preventDefault();
      close();
      router.push(path);
      return;
    }
  }

  switch (event.key) {
    case "ArrowDown":
      event.preventDefault();
      if (totalSelectableItems.value > 0) {
        moveSelection("down", totalSelectableItems.value);
      }
      break;
    case "ArrowUp":
      event.preventDefault();
      if (totalSelectableItems.value > 0) {
        moveSelection("up", totalSelectableItems.value);
      }
      break;
    case "Enter":
      event.preventDefault();
      if (isSelectedFilteredSearch.value) {
        executeFilteredSearch();
      } else if (isSelectedDeltaFile.value) {
        const deltaFileIdx = selectedIndex.value - filteredSearchOffset.value;
        executeDeltaFile(deltaFileResults.value[deltaFileIdx]);
      } else {
        const cmd = getSelectedCommand();
        if (cmd) {
          executeCommand(cmd);
        } else if (activeFilter.value && searchQuery.value.trim()) {
          // No selection but filter active - go to search page
          executeFilteredSearch();
        }
      }
      break;
    case "Escape":
      event.preventDefault();
      if (activeFilter.value) {
        clearFilter();
      } else {
        close();
      }
      break;
    case "Tab":
      event.preventDefault();
      cycleFilter(event.shiftKey ? -1 : 1);
      break;
  }
};

const filters: SearchFilterType[] = [null, "filename", "annotations", "did"];
const handleConfirmationKeydown = (event: KeyboardEvent) => {
  if (event.key === "Escape") {
    event.preventDefault();
    cancelPendingAction();
  } else if (event.key === "Enter") {
    event.preventDefault();
    confirmPendingAction();
  }
};

const cycleFilter = (direction: number) => {
  const currentIdx = filters.indexOf(activeFilter.value);
  const nextIdx = (currentIdx + direction + filters.length) % filters.length;
  const nextFilter = filters[nextIdx];
  if (nextFilter) {
    setFilter(nextFilter);
  } else {
    clearFilter();
  }
};

watch(visible, (isVisible) => {
  if (isVisible) {
    nextTick(() => {
      searchInput.value?.focus();
    });
  }
});

watch(pendingAction, (action) => {
  if (action) {
    nextTick(() => {
      cancelBtn.value?.focus();
    });
  } else {
    nextTick(() => {
      searchInput.value?.focus();
    });
  }
});

watch(searchQuery, () => {
  selectedIndex.value = 0;
});

watch(selectedIndex, (newIndex) => {
  nextTick(() => {
    if (!resultsSection.value) return;
    const selectedEl = resultsSection.value.querySelector(`[data-index="${newIndex}"]`);
    if (selectedEl) {
      selectedEl.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
  });
});
</script>

<style>
.command-palette-root {
  width: 600px !important;
  max-width: 90vw !important;
}

.command-palette-dialog .p-dialog-content {
  padding: 0 !important;
  border-radius: 8px;
  overflow: hidden;
}
</style>

<style scoped lang="scss">
.command-palette {
  display: flex;
  flex-direction: column;
  background: white;
  position: relative;
}

.search-section {
  padding: 1rem;
  border-bottom: 1px solid #e9ecef;
}

.search-input-wrapper {
  display: flex;
  align-items: center;
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  margin-bottom: 0.75rem;
}

.search-prefix {
  color: #6c757d;
  font-weight: 500;
  margin-right: 0.5rem;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 1rem;

  &::placeholder {
    color: #adb5bd;
  }
}

.search-hint {
  color: #adb5bd;
  font-size: 0.75rem;
  padding: 0.125rem 0.375rem;
  background: #e9ecef;
  border-radius: 3px;
}

.filter-pills {
  display: flex;
  gap: 0.5rem;
}

.filter-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  border: 1px solid #dee2e6;
  border-radius: 16px;
  background: white;
  color: #495057;
  font-size: 0.875rem;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: #f8f9fa;
    border-color: #adb5bd;
  }

  &.active {
    background: #007bff;
    border-color: #007bff;
    color: white;

    kbd {
      background: rgba(255, 255, 255, 0.2);
      color: white;
    }
  }

  kbd {
    font-size: 0.7rem;
    padding: 0.1rem 0.3rem;
    background: #e9ecef;
    border-radius: 3px;
    font-family: system-ui, -apple-system, sans-serif;
    color: #495057;
  }
}

.filtered-search-section {
  border-bottom: 1px solid #e9ecef;
  padding: 0.5rem 0;
}

.filtered-search-item {
  background: #f0f7ff;

  &.selected {
    background: #e3f2fd;
  }

  .command-name {
    color: #007bff;
    font-weight: 500;
  }
}

.commands-section {
  max-height: 400px;
  overflow-y: auto;
  padding: 0.5rem 0;
}

.command-group {
  margin-bottom: 0.5rem;
}

.group-header {
  padding: 0.5rem 1rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: #6c757d;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.command-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 1rem;
  cursor: pointer;
  transition: background-color 0.1s;

  &:hover,
  &.selected {
    background: #f8f9fa;
  }

  &.selected {
    background: #e3f2fd;
  }
}

.command-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.command-icon {
  width: 1.25rem;
  color: #6c757d;
  text-align: center;
}

.command-name {
  color: #212529;
}

.command-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.command-hotkey {
  font-size: 0.75rem;
  color: #6c757d;
  padding: 0.125rem 0.375rem;
  background: #e9ecef;
  border-radius: 3px;
  font-family: system-ui, -apple-system, sans-serif;
}

.external-icon {
  font-size: 0.75rem;
  color: #adb5bd;
}

.badge {
  font-size: 0.75rem;
}

.results-section {
  max-height: 400px;
  overflow-y: auto;
}

.deltafile-results-section {
  border-bottom: 1px solid #e9ecef;
}

.search-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1rem;
  color: #6c757d;

  i {
    color: #007bff;
  }
}

.search-empty-inline {
  padding: 0.75rem 1rem;
  color: #6c757d;
  font-size: 0.875rem;
  font-style: italic;
}

.validation-error {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  color: #856404;
  background: #fff3cd;
  font-size: 0.875rem;

  i {
    color: #856404;
  }
}

.result-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.5rem 1rem;
  cursor: pointer;
  transition: background-color 0.1s;

  &:hover,
  &.selected {
    background: #f8f9fa;
  }

  &.selected {
    background: #e3f2fd;
  }
}

.result-main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.result-icon {
  width: 1.25rem;
  flex-shrink: 0;
  color: #6c757d;
  text-align: center;
}

.result-name {
  color: #212529;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-did {
  margin-left: auto;
  font-size: 0.7rem;
  color: #adb5bd;
  font-family: monospace;
  flex-shrink: 0;
}

.result-meta {
  display: flex;
  gap: 1rem;
  padding-left: 2rem;
  font-size: 0.8rem;
  color: #6c757d;
}

.result-datasource {
  color: #007bff;
}

.result-date {
  color: #adb5bd;
}

.result-annotations {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding-left: 2rem;
  margin-top: 0.25rem;
}

.result-annotation {
  font-size: 0.75rem;
  background: #e9ecef;
  padding: 0.125rem 0.375rem;
  border-radius: 3px;
}

.anno-key {
  color: #6c757d;
}

.anno-sep {
  color: #adb5bd;
}

.anno-value {
  color: #212529;
}

.search-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem 1rem;
  color: #6c757d;
  font-size: 0.9rem;
}

.command-footer {
  display: flex;
  justify-content: center;
  gap: 1.5rem;
  padding: 0.75rem 1rem;
  background: #f8f9fa;
  border-top: 1px solid #e9ecef;
}

.footer-hint {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  color: #6c757d;

  kbd {
    padding: 0.125rem 0.25rem;
    background: #e9ecef;
    border-radius: 2px;
    font-family: system-ui, -apple-system, sans-serif;
  }
}

.confirmation-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.97);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  border-radius: 8px;
}

.confirmation-content {
  text-align: center;
  padding: 2rem;
  max-width: 400px;
}

.confirmation-icon {
  font-size: 2.5rem;
  color: #f0ad4e;
  margin-bottom: 1rem;
}

.confirmation-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #212529;
  margin: 0 0 0.75rem 0;
}

.confirmation-message {
  color: #6c757d;
  margin: 0 0 0.5rem 0;
  line-height: 1.5;
}

.confirmation-question {
  color: #495057;
  font-weight: 500;
  margin: 0 0 1.5rem 0;
}

.confirmation-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}

.confirm-btn {
  padding: 0.5rem 1.5rem;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;

  &.cancel {
    background: white;
    border: 1px solid #dee2e6;
    color: #495057;

    &:hover {
      background: #f8f9fa;
      border-color: #adb5bd;
    }

    &:focus {
      outline: none;
      box-shadow: 0 0 0 3px rgba(108, 117, 125, 0.25);
    }
  }

  &.confirm {
    background: #dc3545;
    border: 1px solid #dc3545;
    color: white;

    &:hover {
      background: #c82333;
      border-color: #bd2130;
    }

    &:focus {
      outline: none;
      box-shadow: 0 0 0 3px rgba(220, 53, 69, 0.25);
    }
  }
}
</style>
