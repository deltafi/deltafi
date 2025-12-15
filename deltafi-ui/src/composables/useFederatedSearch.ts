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

// ABOUTME: Composable for executing federated searches across fleet members.
// ABOUTME: Provides search API, aggregated options, and URL building for member drill-down links.

import { ref, Ref } from "vue";

export interface FederatedSearchResult {
  memberName: string;
  memberUrl: string;
  tags: string[];
  count: number | null;
  status: "CONNECTED" | "STALE" | "UNREACHABLE";
  error: string | null;
}

export interface FederatedSearchResponse {
  results: FederatedSearchResult[];
  totalCount: number;
  membersSearched: number;
  membersFailed: number;
  searchedAt: string;
}

export interface AggregatedSearchOptions {
  restDataSources: string[];
  timedDataSources: string[];
  dataSinks: string[];
  transforms: string[];
  topics: string[];
  annotationKeys: string[];
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

export default function useFederatedSearch() {
  const loading = ref(false);
  const response: Ref<FederatedSearchResponse | null> = ref(null);
  const error: Ref<string | null> = ref(null);
  const options: Ref<AggregatedSearchOptions | null> = ref(null);
  const optionsLoading = ref(false);
  const optionsError: Ref<string | null> = ref(null);

  const search = async (filter: DeltaFilesFilter): Promise<FederatedSearchResponse | null> => {
    loading.value = true;
    error.value = null;
    try {
      const res = await fetch("/api/v2/leader/search/federated", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(filter),
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data: FederatedSearchResponse = await res.json();
      response.value = data;
      return data;
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : "Search failed";
      return null;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Fetch aggregated search options from all fleet members.
   * Returns combined flow names, topics, and annotation keys.
   */
  const fetchOptions = async (): Promise<AggregatedSearchOptions | null> => {
    optionsLoading.value = true;
    optionsError.value = null;
    try {
      const res = await fetch("/api/v2/leader/search/options", {
        method: "GET",
        headers: {
          Accept: "application/json",
        },
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data: AggregatedSearchOptions = await res.json();
      options.value = data;
      return data;
    } catch (e: unknown) {
      optionsError.value = e instanceof Error ? e.message : "Failed to fetch options";
      return null;
    } finally {
      optionsLoading.value = false;
    }
  };

  /**
   * Build a URL to open the member's search page with the same filters.
   * The URL format matches DeltaFileSearchPage's query string format.
   */
  const buildMemberSearchUrl = (memberUrl: string, filter: DeltaFilesFilter, queryDateType: string = "Modified"): string => {
    const params = new URLSearchParams();

    // Date parameters - map back to UI format
    if (queryDateType === "Modified") {
      if (filter.modifiedAfter) params.set("startTimeDate", filter.modifiedAfter);
      if (filter.modifiedBefore) params.set("endTimeDate", filter.modifiedBefore);
    } else {
      if (filter.createdAfter) params.set("startTimeDate", filter.createdAfter);
      if (filter.createdBefore) params.set("endTimeDate", filter.createdBefore);
    }
    params.set("queryDateTypeOptions", queryDateType);

    if (filter.nameFilter?.name) params.set("fileName", filter.nameFilter.name);
    if (filter.stage) params.set("stage", filter.stage);
    if (filter.dataSources?.length) params.set("dataSources", filter.dataSources.join(","));
    if (filter.dataSinks?.length) params.set("dataSinks", filter.dataSinks.join(","));
    if (filter.transforms?.length) params.set("transforms", filter.transforms.join(","));
    if (filter.topics?.length) params.set("topics", filter.topics.join(","));

    if (filter.annotations?.length) {
      const annotationsStr = filter.annotations.map((a) => `${a.key}:${a.value}`).join(",");
      params.set("annotations", annotationsStr);
    }

    if (filter.egressed !== undefined) params.set("egressed", String(filter.egressed));
    if (filter.filtered !== undefined) params.set("filtered", String(filter.filtered));
    if (filter.testMode !== undefined) params.set("testMode", String(filter.testMode));
    if (filter.replayable !== undefined) params.set("replayable", String(filter.replayable));
    if (filter.terminalStage !== undefined) params.set("terminalStage", String(filter.terminalStage));
    if (filter.pendingAnnotations !== undefined) params.set("pendingAnnotations", String(filter.pendingAnnotations));
    if (filter.paused !== undefined) params.set("paused", String(filter.paused));
    if (filter.pinned !== undefined) params.set("pinned", String(filter.pinned));
    if (filter.warnings !== undefined) params.set("warnings", String(filter.warnings));

    if (filter.requeueCountMin !== undefined) params.set("requeueMin", String(filter.requeueCountMin));
    if (filter.filteredCause) params.set("filteredCause", filter.filteredCause);

    // Size parameters - need to reverse-engineer unit from bytes
    // For simplicity, we'll use bytes (B) as the unit
    if (filter.ingressBytesMin !== undefined) {
      params.set("sizeType", "Ingress");
      params.set("sizeMin", String(filter.ingressBytesMin));
      params.set("sizeUnit", "B");
    }
    if (filter.ingressBytesMax !== undefined) {
      params.set("sizeType", "Ingress");
      params.set("sizeMax", String(filter.ingressBytesMax));
      params.set("sizeUnit", "B");
    }
    if (filter.totalBytesMin !== undefined) {
      params.set("sizeType", "Total");
      params.set("sizeMin", String(filter.totalBytesMin));
      params.set("sizeUnit", "B");
    }
    if (filter.totalBytesMax !== undefined) {
      params.set("sizeType", "Total");
      params.set("sizeMax", String(filter.totalBytesMax));
      params.set("sizeUnit", "B");
    }

    const queryString = params.toString();
    const baseUrl = memberUrl === "local" ? "" : memberUrl;
    return `${baseUrl}/deltafile/search${queryString ? "?" + queryString : ""}`;
  };

  return {
    loading,
    response,
    error,
    options,
    optionsLoading,
    optionsError,
    search,
    fetchOptions,
    buildMemberSearchUrl,
  };
}
