// ABOUTME: State and search logic for the command palette (Cmd+K navigation).
// ABOUTME: Manages visibility, search filtering, filter pills, and command execution.

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

import { ref, computed, watch, inject, type Ref, type ComputedRef } from "vue";
import { useRouter } from "vue-router";
import { EnumType } from "json-to-graphql-query";
import useMenuItems, { type FlattenedPage } from "./useMenuItems";
import useGraphQL from "./useGraphQL";
import useSystemProperties from "./useSystemProperties";

export type SearchFilterType = "did" | "filename" | "annotations" | null;

export interface CommandItem {
  name: string;
  icon?: string;
  path?: string;
  url?: string;
  group?: string;
  hotkey?: string;
  badge?: () => { visible: boolean; class: string; value: number };
  action?: () => Promise<void>;
  keywords?: string[];
}

export interface DeltaFileResult {
  did: string;
  name: string;
  dataSource: string;
  modified: string;
  annotations: Record<string, string>;
}

export interface UseCommandPaletteReturn {
  visible: Ref<boolean>;
  searchQuery: Ref<string>;
  activeFilter: Ref<SearchFilterType>;
  filteredCommands: ComputedRef<CommandItem[]>;
  deltaFileResults: Ref<DeltaFileResult[]>;
  searchLoading: Ref<boolean>;
  selectedIndex: Ref<number>;
  didValidationError: Ref<string | null>;
  pendingAction: Ref<{ name: string; message: string; action: () => Promise<void> } | null>;
  open: () => void;
  close: () => void;
  toggle: () => void;
  setFilter: (filter: SearchFilterType) => void;
  clearFilter: () => void;
  executeCommand: (command: CommandItem) => void;
  executeDeltaFile: (result: DeltaFileResult) => void;
  executeSearch: (commands: CommandItem[]) => void;
  moveSelection: (direction: "up" | "down", totalCommands: number) => void;
  confirmPendingAction: () => Promise<void>;
  cancelPendingAction: () => void;
}

const hotkeysByPath: Record<string, string> = {
  "/": "D",
  "/deltafile/search": "S",
  "/errors": "E",
  "/deltafile/upload/": "U",
  "/system-map": "M",
  "/config/plugins": "P",
  "/config/transforms": "T",
};

const visible = ref(false);
const searchQuery = ref("");
const activeFilter = ref<SearchFilterType>(null);
const selectedIndex = ref(0);
const deltaFileResults = ref<DeltaFileResult[]>([]);
const searchLoading = ref(false);
const didValidationError = ref<string | null>(null);
const ingressEnabled = ref<boolean | null>(null);
const egressEnabled = ref<boolean | null>(null);
const pendingAction = ref<{ name: string; message: string; action: () => Promise<void> } | null>(null);
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null;

// UUID validation regex
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const isValidUUID = (str: string): boolean => {
  return UUID_REGEX.test(str.trim());
};

export default function useCommandPalette(): UseCommandPaletteReturn {
  const router = useRouter();
  const { flattenedPages, isVisible } = useMenuItems();
  const { response, queryGraphQL } = useGraphQL();
  const systemProperties = useSystemProperties();
  const hasPermission = inject<(permission: string) => boolean>("hasPermission");

  const fetchSystemState = async () => {
    try {
      ingressEnabled.value = await systemProperties.get("ingressEnabled");
      egressEnabled.value = await systemProperties.get("egressEnabled");
    } catch {
      // Ignore errors, commands will be hidden if state unknown
    }
  };

  const toggleIngress = async () => {
    const newValue = ingressEnabled.value ? "false" : "true";
    await systemProperties.set("ingressEnabled", newValue);
    ingressEnabled.value = newValue === "true";
  };

  const toggleEgress = async () => {
    const newValue = egressEnabled.value ? "false" : "true";
    await systemProperties.set("egressEnabled", newValue);
    egressEnabled.value = newValue === "true";
  };

  const requestToggleIngress = async () => {
    const willDisable = ingressEnabled.value === true;
    pendingAction.value = {
      name: willDisable ? "Disable Ingress" : "Enable Ingress",
      message: willDisable
        ? "This will disable all ingress. No new data will be accepted into the system."
        : "This will enable ingress. The system will begin accepting new data.",
      action: toggleIngress,
    };
  };

  const requestToggleEgress = async () => {
    const willDisable = egressEnabled.value === true;
    pendingAction.value = {
      name: willDisable ? "Disable Egress" : "Enable Egress",
      message: willDisable
        ? "This will disable all egress. No data will be sent to external systems."
        : "This will enable egress. The system will begin sending data to external systems.",
      action: toggleEgress,
    };
  };

  const confirmPendingAction = async () => {
    if (pendingAction.value) {
      await pendingAction.value.action();
      pendingAction.value = null;
    }
  };

  const cancelPendingAction = () => {
    pendingAction.value = null;
  };

  const searchDeltaFiles = async (query: string, filterType: SearchFilterType) => {
    didValidationError.value = null;

    if (!query.trim() || !filterType) {
      deltaFileResults.value = [];
      return;
    }

    // Validate DID format before searching
    if (filterType === "did" && !isValidUUID(query)) {
      didValidationError.value = "Enter a valid UUID (e.g., 550e8400-e29b-41d4-a716-446655440000)";
      deltaFileResults.value = [];
      return;
    }

    searchLoading.value = true;

    try {
      let filter: Record<string, unknown> = {};

      if (filterType === "did") {
        filter = { dids: [query.trim()] };
      } else if (filterType === "filename") {
        filter = { nameFilter: { name: query.trim() } };
      } else if (filterType === "annotations") {
        const parts = query.trim().split("=");
        if (parts.length === 2) {
          // Wrap value with wildcards for fuzzy matching (backend supports * as wildcard)
          const key = parts[0].trim();
          const value = parts[1].trim();
          const fuzzyValue = value.includes("*") ? value : `*${value}*`;
          filter = { annotations: [{ key, value: fuzzyValue }] };
        } else {
          // Key-only search: use * to match any value
          filter = { annotations: [{ key: query.trim(), value: "*" }] };
        }
      }

      const graphqlQuery = {
        deltaFiles: {
          __args: {
            offset: 0,
            limit: 5,
            filter,
            orderBy: {
              direction: new EnumType("DESC"),
              field: "modified",
            },
          },
          deltaFiles: {
            did: true,
            name: true,
            dataSource: true,
            modified: true,
            annotations: true,
          },
        },
      };

      await queryGraphQL(graphqlQuery, "commandPaletteSearch");
      deltaFileResults.value = response.value?.data?.deltaFiles?.deltaFiles || [];
      selectedIndex.value = 0;
    } catch {
      deltaFileResults.value = [];
    } finally {
      searchLoading.value = false;
    }
  };

  // Debounced search when query or filter changes
  watch([searchQuery, activeFilter], ([query, filter]) => {
    if (searchDebounceTimer) {
      clearTimeout(searchDebounceTimer);
    }

    if (!filter || !query) {
      deltaFileResults.value = [];
      return;
    }

    searchDebounceTimer = setTimeout(() => {
      searchDeltaFiles(query, filter);
    }, 300);
  });

  const navigationCommands = computed<CommandItem[]>(() => {
    return flattenedPages.value
      .filter((page) => isVisible(page))
      .map((page) => ({
        ...page,
        hotkey: page.path ? hotkeysByPath[page.path] : undefined,
      }));
  });

  const actionCommands = computed<CommandItem[]>(() => {
    const commands: CommandItem[] = [];

    // Only show system property commands if user has permission
    if (hasPermission?.("SystemPropertiesUpdate")) {
      if (ingressEnabled.value !== null) {
        commands.push({
          name: ingressEnabled.value ? "Disable Ingress" : "Enable Ingress",
          icon: ingressEnabled.value ? "fas fa-toggle-on fa-fw" : "fas fa-toggle-off fa-fw",
          group: "Commands",
          action: requestToggleIngress,
        });
      }

      if (egressEnabled.value !== null) {
        commands.push({
          name: egressEnabled.value ? "Disable Egress" : "Enable Egress",
          icon: egressEnabled.value ? "fas fa-toggle-on fa-fw" : "fas fa-toggle-off fa-fw",
          group: "Commands",
          action: requestToggleEgress,
        });
      }
    }

    return commands;
  });

  const commandItems = computed<CommandItem[]>(() => {
    return [...navigationCommands.value, ...actionCommands.value];
  });

  const filteredCommands = computed<CommandItem[]>(() => {
    const query = searchQuery.value.toLowerCase().trim();
    if (!query) {
      return commandItems.value;
    }

    // Fuzzy match: check if all characters appear in order
    const fuzzyMatch = (text: string, pattern: string): boolean => {
      let patternIdx = 0;
      const textLower = text.toLowerCase();
      for (let i = 0; i < textLower.length && patternIdx < pattern.length; i++) {
        if (textLower[i] === pattern[patternIdx]) {
          patternIdx++;
        }
      }
      return patternIdx === pattern.length;
    };

    return commandItems.value.filter((cmd) => {
      const nameMatch = fuzzyMatch(cmd.name, query);
      const groupMatch = cmd.group ? fuzzyMatch(cmd.group, query) : false;
      const keywordMatch = cmd.keywords?.some((kw) => fuzzyMatch(kw, query)) ?? false;
      return nameMatch || groupMatch || keywordMatch;
    });
  });

  const open = () => {
    visible.value = true;
    searchQuery.value = "";
    activeFilter.value = null;
    selectedIndex.value = 0;
    fetchSystemState();
  };

  const close = () => {
    visible.value = false;
    searchQuery.value = "";
    activeFilter.value = null;
    selectedIndex.value = 0;
    deltaFileResults.value = [];
    didValidationError.value = null;
    pendingAction.value = null;
  };

  const toggle = () => {
    if (visible.value) {
      close();
    } else {
      open();
    }
  };

  const setFilter = (filter: SearchFilterType) => {
    activeFilter.value = filter;
    selectedIndex.value = 0;
    didValidationError.value = null;
  };

  const clearFilter = () => {
    activeFilter.value = null;
    selectedIndex.value = 0;
    deltaFileResults.value = [];
  };

  const executeCommand = async (command: CommandItem) => {
    if (command.action) {
      await command.action();
      // Don't close - let user see updated state or execute more commands
      return;
    }
    if (command.path) {
      router.push(command.path);
    } else if (command.url) {
      window.open(command.url, "_blank");
    }
    close();
  };

  const executeDeltaFile = (result: DeltaFileResult) => {
    router.push(`/deltafile/viewer/${result.did}`);
    close();
  };

  const executeSearch = (commands: CommandItem[]) => {
    const query = searchQuery.value.trim();

    // If no filter is active, execute the selected command
    if (!activeFilter.value) {
      if (commands.length === 0) return;
      const selected = commands[selectedIndex.value];
      if (selected) {
        executeCommand(selected);
      }
      return;
    }

    // For filter modes, require a query
    if (!query) return;

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

  const moveSelection = (direction: "up" | "down", totalCommands: number) => {
    if (totalCommands === 0) return;

    if (direction === "up") {
      selectedIndex.value = selectedIndex.value <= 0 ? totalCommands - 1 : selectedIndex.value - 1;
    } else {
      selectedIndex.value = selectedIndex.value >= totalCommands - 1 ? 0 : selectedIndex.value + 1;
    }
  };

  return {
    visible,
    searchQuery,
    activeFilter,
    filteredCommands,
    deltaFileResults,
    searchLoading,
    selectedIndex,
    didValidationError,
    pendingAction,
    open,
    close,
    toggle,
    setFilter,
    clearFilter,
    executeCommand,
    executeDeltaFile,
    executeSearch,
    moveSelection,
    confirmPendingAction,
    cancelPendingAction,
  };
}
