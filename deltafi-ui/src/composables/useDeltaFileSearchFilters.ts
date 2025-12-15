/*
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
*/

// ABOUTME: Shared composable for DeltaFile search filter state management.
// ABOUTME: Handles URL persistence, filter encoding/decoding, and filter building.

import { ref, computed, inject, watch } from "vue";
import { useUrlSearchParams, useStorage, StorageSerializers } from "@vueuse/core";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import _ from "lodash";

dayjs.extend(utc);

export interface SearchFilterModel {
  // Paginator options (only for DeltaFileSearchPage)
  offset?: number;
  perPage?: number;
  page?: number;
  sortDirection?: string;
  sortField?: string;
  // Search filter options
  startTimeDate: Date;
  endTimeDate: Date;
  fileName: string | null;
  pendingAnnotations: boolean | null;
  validatedAnnotations: Array<{ key: string; value: string; valid: boolean }>;
  annotations: Array<{ key: string; value: string }>;
  dataSources: string[];
  dataSinks: string[];
  transforms: string[];
  topics: string[];
  filteredCause: string | null;
  requeueMin: number | null;
  stage: string | null;
  egressed: boolean | null;
  filtered: boolean | null;
  testMode: boolean | null;
  terminalStage: boolean | null;
  replayable: boolean | null;
  paused: boolean | null;
  pinned: boolean | null;
  warnings: boolean | null;
  sizeMin: number | null;
  sizeMax: number | null;
  sizeType: string;
  sizeUnit: string;
  queryDateTypeOptions: string;
  ingressBytesMin: number | null;
  ingressBytesMax: number | null;
  totalBytesMin: number | null;
  totalBytesMax: number | null;
}

export interface DeltaFilesFilter {
  modifiedAfter?: string;
  modifiedBefore?: string;
  createdAfter?: string;
  createdBefore?: string;
  nameFilter?: { name: string };
  stage?: string;
  dataSources?: string[];
  dataSinks?: string[];
  transforms?: string[];
  topics?: string[];
  annotations?: Array<{ key: string; value: string }>;
  egressed?: boolean;
  filtered?: boolean;
  testMode?: boolean;
  replayable?: boolean;
  terminalStage?: boolean;
  pendingAnnotations?: boolean;
  paused?: boolean;
  pinned?: boolean;
  warnings?: boolean;
  ingressBytesMin?: number;
  ingressBytesMax?: number;
  totalBytesMin?: number;
  totalBytesMax?: number;
  requeueCountMin?: number;
  filteredCause?: string;
  errorCause?: string;
  errorAcknowledged?: boolean;
}

const TIMESTAMP_FORMAT = "YYYY-MM-DD HH:mm:ss";

const SIZE_UNITS_MAP = new Map([
  ["B", { multiplier: 1 }],
  ["kB", { multiplier: 1000 }],
  ["MB", { multiplier: 1000000 }],
  ["GB", { multiplier: 1000000000 }],
]);

const SIZE_TYPES = ["Ingress", "Total"];
const QUERY_DATE_TYPES = ["Modified", "Created"];

// Fields that should trigger URL persistence when changed
const PERSISTED_FIELDS = [
  "fileName",
  "filteredCause",
  "requeueMin",
  "stage",
  "dataSources",
  "dataSinks",
  "transforms",
  "topics",
  "egressed",
  "filtered",
  "testMode",
  "replayable",
  "terminalStage",
  "sizeMin",
  "sizeMax",
  "validatedAnnotations",
  "pendingAnnotations",
  "annotations",
  "sizeUnit",
  "sizeType",
  "paused",
  "warnings",
  "pinned",
  "queryDateTypeOptions",
  "startTimeDate",
  "endTimeDate",
];

export interface UseDeltaFileSearchFiltersOptions {
  storageKey?: string;
  includePagination?: boolean;
}

export default function useDeltaFileSearchFilters(options: UseDeltaFileSearchFiltersOptions = {}) {
  const { storageKey = "search-page-persisted-params", includePagination = false } = options;

  const uiConfig = inject("uiConfig") as { useUTC: boolean };
  const params = useUrlSearchParams("history");

  // Default time range
  const defaultStartTimeDate = computed(() => {
    const date = dayjs().utc();
    return (uiConfig?.useUTC ? date : date.local()).startOf("day");
  });

  const defaultEndTimeDate = computed(() => {
    const date = dayjs().utc();
    return (uiConfig?.useUTC ? date : date.local()).endOf("day");
  });

  const resetDefaultTimeDate = computed(() => {
    return [new Date(defaultStartTimeDate.value.format(TIMESTAMP_FORMAT)), new Date(defaultEndTimeDate.value.format(TIMESTAMP_FORMAT))];
  });

  // Default filter template
  const createDefaultTemplate = (): SearchFilterModel => ({
    offset: 0,
    perPage: 20,
    sortDirection: "DESC",
    sortField: "modified",
    startTimeDate: new Date(defaultStartTimeDate.value.format(TIMESTAMP_FORMAT)),
    endTimeDate: new Date(defaultEndTimeDate.value.format(TIMESTAMP_FORMAT)),
    fileName: null,
    pendingAnnotations: null,
    validatedAnnotations: [],
    annotations: [],
    dataSources: [],
    dataSinks: [],
    transforms: [],
    topics: [],
    filteredCause: null,
    requeueMin: null,
    stage: null,
    egressed: null,
    filtered: null,
    testMode: null,
    terminalStage: null,
    replayable: null,
    paused: null,
    pinned: null,
    warnings: null,
    sizeMin: null,
    sizeMax: null,
    sizeType: SIZE_TYPES[0],
    sizeUnit: [...SIZE_UNITS_MAP.keys()][0],
    queryDateTypeOptions: QUERY_DATE_TYPES[0],
    ingressBytesMin: null,
    ingressBytesMax: null,
    totalBytesMin: null,
    totalBytesMax: null,
  });

  const defaultTemplate = createDefaultTemplate();
  const queryParamsModel = ref<SearchFilterModel>(_.cloneDeep(defaultTemplate));
  const initialized = ref(false);

  // Proxy model for automatic default value handling
  const model = computed({
    get() {
      return new Proxy(queryParamsModel.value, {
        set(obj, key, value) {
          (queryParamsModel.value as unknown as Record<string, unknown>)[key as string] = value ?? (defaultTemplate as unknown as Record<string, unknown>)[key as string];
          return true;
        },
      });
    },
    set(newValue) {
      queryParamsModel.value = newValue;
    },
  });

  // Date conversion utilities
  const dateToISOString = (dateData: Date): string => {
    return dayjs(dateData).utc(uiConfig?.useUTC).toISOString();
  };

  const isoStringToDate = (isoStringData: string): Date => {
    return uiConfig?.useUTC ? dayjs(isoStringData).add(new Date().getTimezoneOffset(), "minute").toDate() : dayjs(isoStringData).toDate();
  };

  // Annotation parsing
  const getAnnotationsArray = (annotationsString: string) => {
    if (!annotationsString) return [];
    return annotationsString.split(",").map((annotation) => {
      const [key, value] = annotation.split(":");
      return { key, value, valid: true };
    });
  };

  const getAnnotationsString = (annotations: Array<{ key: string; value: string }>) => {
    if (!annotations?.length) return "";
    return annotations.map((a) => `${a.key}:${a.value}`).join(",");
  };

  // Decode params from URL/storage
  const decodePersistedParams = (obj: Record<string, unknown>) =>
    _.transform(obj, (r: Record<string, unknown>, v, k) => {
      if (["startTimeDate", "endTimeDate"].includes(k)) {
        r[k] = isoStringToDate(v as string);
      } else if (["egressed", "filtered", "testMode", "replayable", "terminalStage", "pendingAnnotations", "paused", "warnings", "pinned"].includes(k)) {
        r[k] = JSON.parse(v as string);
      } else if (["requeueMin", "sizeMin", "sizeMax", "perPage", "page"].includes(k)) {
        r[k] = Number(v);
      } else if (["dataSources", "dataSinks", "transforms", "topics"].includes(k)) {
        r[k] = (v as string).split(",");
      } else if (k === "annotations") {
        r["validatedAnnotations"] = getAnnotationsArray(v as string);
      } else {
        r[k] = v;
      }
    });

  // Encode params for URL/storage
  const encodePersistedParams = (obj: Record<string, unknown>) =>
    _.transform(obj, (r: Record<string, unknown>, v, k) => {
      if (["startTimeDate", "endTimeDate"].includes(k)) {
        r[k] = dateToISOString(v as Date);
      } else if (["egressed", "filtered", "testMode", "replayable", "terminalStage", "pendingAnnotations", "paused", "warnings", "pinned"].includes(k)) {
        r[k] = Boolean(v);
      } else if (["requeueMin", "sizeMin", "sizeMax", "perPage", "page"].includes(k)) {
        r[k] = Number(v);
      } else if (["dataSources", "dataSinks", "transforms", "topics"].includes(k)) {
        r[k] = String(v);
      } else if (k === "annotations") {
        r[k] = getAnnotationsString(v as Array<{ key: string; value: string }>);
      } else {
        r[k] = v;
      }
    });

  // Session storage for persistence
  const queryState = useStorage(storageKey, {}, sessionStorage, { serializer: StorageSerializers.object });

  // Check if URL has search params
  const hasUrlParams = computed(() => Object.keys(params).some((k) => params[k] !== null && params[k] !== undefined && params[k] !== ""));

  // Load persisted params from URL or storage
  const loadPersistedParams = () => {
    let persistedState: Record<string, unknown> = {};
    if (hasUrlParams.value) {
      persistedState = _.cloneDeepWith(params, decodePersistedParams);
    } else if (Object.keys(queryState.value).length > 0) {
      persistedState = _.cloneDeepWith(queryState.value, decodePersistedParams);
    }

    if (Object.keys(persistedState).length > 0) {
      queryParamsModel.value = _.merge(_.cloneDeep(defaultTemplate), persistedState);
    }
    initialized.value = true;
  };

  // Save params to URL and storage
  const savePersistedParams = () => {
    let persistedQueryState = _.cloneDeep(queryParamsModel.value) as Record<string, unknown>;

    // Remove unchanged values
    persistedQueryState = _.omitBy(persistedQueryState, (v, k) => {
      return JSON.stringify((defaultTemplate as unknown as Record<string, unknown>)[k]) === JSON.stringify(v);
    });

    // Remove pagination values if not needed
    if (!includePagination) {
      persistedQueryState = _.omit(persistedQueryState, ["offset", "perPage", "page", "sortDirection", "sortField"]);
    }

    // Remove computed size values
    persistedQueryState = _.omit(persistedQueryState, ["ingressBytesMin", "ingressBytesMax", "totalBytesMin", "totalBytesMax", "validatedAnnotations"]);

    persistedQueryState = _.cloneDeepWith(persistedQueryState, encodePersistedParams);
    queryState.value = persistedQueryState;

    // Update URL params
    Object.keys(params).forEach((k) => ((params as Record<string, string | null>)[k] = null));
    for (const key in persistedQueryState) {
      (params as Record<string, string | null>)[key] = persistedQueryState[key] as string;
    }
  };

  // Compute size in bytes
  const computedSizeParams = computed(() => {
    const multiplier = SIZE_UNITS_MAP.get(model.value.sizeUnit)?.multiplier || 1;
    return {
      ingressBytesMin: model.value.sizeType === "Ingress" && model.value.sizeMin !== null ? model.value.sizeMin * multiplier : null,
      ingressBytesMax: model.value.sizeType === "Ingress" && model.value.sizeMax !== null ? model.value.sizeMax * multiplier : null,
      totalBytesMin: model.value.sizeType === "Total" && model.value.sizeMin !== null ? model.value.sizeMin * multiplier : null,
      totalBytesMax: model.value.sizeType === "Total" && model.value.sizeMax !== null ? model.value.sizeMax * multiplier : null,
    };
  });

  // Build filter object for API calls
  const buildFilter = (): DeltaFilesFilter => {
    const filter: DeltaFilesFilter = {};
    const sizeParams = computedSizeParams.value;

    // Date filters
    if (model.value.queryDateTypeOptions === "Modified") {
      filter.modifiedAfter = dateToISOString(model.value.startTimeDate);
      filter.modifiedBefore = dateToISOString(model.value.endTimeDate);
    } else {
      filter.createdAfter = dateToISOString(model.value.startTimeDate);
      filter.createdBefore = dateToISOString(model.value.endTimeDate);
    }

    // Text filters
    if (model.value.fileName) filter.nameFilter = { name: model.value.fileName };
    if (model.value.stage) filter.stage = model.value.stage;
    if (model.value.filteredCause) filter.filteredCause = model.value.filteredCause;

    // Array filters
    if (model.value.dataSources?.length) filter.dataSources = model.value.dataSources;
    if (model.value.dataSinks?.length) filter.dataSinks = model.value.dataSinks;
    if (model.value.transforms?.length) filter.transforms = model.value.transforms;
    if (model.value.topics?.length) filter.topics = model.value.topics;

    // Annotations
    const reformatAnnotations = model.value.validatedAnnotations?.map((a) => ({ key: a.key, value: a.value }));
    if (reformatAnnotations?.length) filter.annotations = reformatAnnotations;

    // Boolean filters
    if (model.value.egressed !== null) filter.egressed = model.value.egressed;
    if (model.value.filtered !== null) filter.filtered = model.value.filtered;
    if (model.value.testMode !== null) filter.testMode = model.value.testMode;
    if (model.value.replayable !== null) filter.replayable = model.value.replayable;
    if (model.value.terminalStage !== null) filter.terminalStage = model.value.terminalStage;
    if (model.value.pendingAnnotations !== null) filter.pendingAnnotations = model.value.pendingAnnotations;
    if (model.value.paused !== null) filter.paused = model.value.paused;
    if (model.value.pinned !== null) filter.pinned = model.value.pinned;
    if (model.value.warnings !== null) filter.warnings = model.value.warnings;

    // Size filters
    if (sizeParams.ingressBytesMin !== null) filter.ingressBytesMin = sizeParams.ingressBytesMin;
    if (sizeParams.ingressBytesMax !== null) filter.ingressBytesMax = sizeParams.ingressBytesMax;
    if (sizeParams.totalBytesMin !== null) filter.totalBytesMin = sizeParams.totalBytesMin;
    if (sizeParams.totalBytesMax !== null) filter.totalBytesMax = sizeParams.totalBytesMax;

    // Requeue filter
    if (model.value.requeueMin !== null) filter.requeueCountMin = model.value.requeueMin;

    return filter;
  };

  // Check if advanced options are active
  const activeAdvancedOptions = computed(() => {
    const advancedOptionsState = _.omitBy(_.pick(queryParamsModel.value, PERSISTED_FIELDS), (v, k) => {
      return JSON.stringify((defaultTemplate as unknown as Record<string, unknown>)[k]) === JSON.stringify(v);
    });
    return !_.isEmpty(advancedOptionsState);
  });

  // Clear all options to defaults
  const clearOptions = () => {
    queryParamsModel.value = _.cloneDeep(defaultTemplate);
    savePersistedParams();
  };

  // Update date range
  const updateDateRange = (startDate: Date, endDate: Date) => {
    model.value.startTimeDate = startDate;
    model.value.endTimeDate = endDate;
  };

  // Watch for changes and persist (only after initialization)
  watch(
    queryParamsModel,
    () => {
      if (initialized.value) {
        savePersistedParams();
      }
    },
    { deep: true }
  );

  // Setup watchers that trigger a callback when filters change
  const setupFilterWatchers = (onFilterChange: () => void) => {
    // Date range changes
    watch(
      () => [model.value.startTimeDate, model.value.endTimeDate],
      () => {
        onFilterChange();
      }
    );

    // Annotation changes
    watch(
      () => model.value.validatedAnnotations,
      () => {
        onFilterChange();
      },
      { deep: true }
    );

    // Boolean and simple filters
    watch(
      () => [
        model.value.sizeMin,
        model.value.sizeMax,
        model.value.stage,
        model.value.egressed,
        model.value.filtered,
        model.value.testMode,
        model.value.requeueMin,
        model.value.replayable,
        model.value.terminalStage,
        model.value.pendingAnnotations,
        model.value.paused,
        model.value.warnings,
        model.value.pinned,
      ],
      () => {
        onFilterChange();
      }
    );

    // Query date type changes
    watch(
      () => [model.value.queryDateTypeOptions],
      () => {
        onFilterChange();
      }
    );

    // Array filters (debounced)
    watch(
      () => [model.value.dataSinks, model.value.dataSources, model.value.transforms, model.value.topics],
      _.debounce(
        () => {
          onFilterChange();
        },
        500,
        { leading: false, trailing: true }
      )
    );

    // Filename filter (debounced)
    watch(
      () => model.value.fileName,
      _.debounce(
        () => {
          onFilterChange();
        },
        500,
        { leading: false, trailing: true }
      )
    );

    // Filtered cause (debounced)
    watch(
      () => model.value.filteredCause,
      _.debounce(
        () => {
          if (model.value.filteredCause == "") {
            model.value.filteredCause = null;
          } else {
            onFilterChange();
          }
        },
        500,
        { leading: false, trailing: true }
      ),
      { deep: true }
    );

    // Size type/unit changes
    watch(
      () => [model.value.sizeType, model.value.sizeUnit],
      () => {
        if (model.value.sizeMin || model.value.sizeMax) {
          onFilterChange();
        }
      }
    );
  };

  return {
    model,
    defaultTemplate,
    queryParamsModel,
    defaultStartTimeDate,
    defaultEndTimeDate,
    resetDefaultTimeDate,
    activeAdvancedOptions,
    hasUrlParams,
    sizeUnitsMap: SIZE_UNITS_MAP,
    sizeTypes: SIZE_TYPES,
    queryDateTypes: QUERY_DATE_TYPES,
    persistedFields: PERSISTED_FIELDS,
    loadPersistedParams,
    savePersistedParams,
    buildFilter,
    clearOptions,
    updateDateRange,
    dateToISOString,
    isoStringToDate,
    setupFilterWatchers,
  };
}
