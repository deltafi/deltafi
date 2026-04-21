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

// ABOUTME: Composable for executing federated error operations across fleet members.
// ABOUTME: Provides search, acknowledge, resume, replay, and replay+acknowledge APIs.

import { ref, Ref } from "vue";

export interface FederatedErrorFilter {
  dataSources?: string[];
  transforms?: string[];
  dataSinks?: string[];
  errorCause?: string;
  errorAcknowledged?: boolean;
  memberNames?: string[];
  memberTags?: string[];
}

export interface FederatedErrorResult {
  memberName: string;
  memberUrl: string;
  tags: string[];
  count: number | null;
  status: "CONNECTED" | "STALE" | "UNREACHABLE";
  error: string | null;
}

export interface FederatedErrorResponse {
  results: FederatedErrorResult[];
  totalCount: number;
  membersSearched: number;
  membersFailed: number;
  searchedAt: string;
}

export interface FederatedActionResult {
  memberName: string;
  successCount: number;
  failureCount: number;
  status: "CONNECTED" | "STALE" | "UNREACHABLE";
  error: string | null;
}

export interface FederatedActionResponse {
  results: FederatedActionResult[];
  totalSuccessCount: number;
  totalFailureCount: number;
  membersFailed: number;
}

export interface FlowSelection {
  name: string;
  flowType: string;
}

export interface FederatedActionRequest {
  filter: FederatedErrorFilter;
  reason?: string;
  memberNames?: string[];
  limit?: number;
  flows?: FlowSelection[];
}

export interface FederatedErrorSummary {
  key: string;
  flowType?: string;
  count: number;
  nodeCount: number;
}

export interface FederatedErrorSummaryResponse {
  summaries: FederatedErrorSummary[];
  totalCount: number;
  membersSearched: number;
  membersFailed: number;
  searchedAt: string;
}

export default function useFederatedErrors() {
  const loading = ref(false);
  const response: Ref<FederatedErrorResponse | null> = ref(null);
  const error: Ref<string | null> = ref(null);
  const actionLoading = ref(false);
  const actionResponse: Ref<FederatedActionResponse | null> = ref(null);
  const actionError: Ref<string | null> = ref(null);
  const summaryLoading = ref(false);
  const summaryResponse: Ref<FederatedErrorSummaryResponse | null> = ref(null);
  const summaryError: Ref<string | null> = ref(null);

  /**
   * Search for errors across all fleet members.
   * Returns error counts per member for the given filter criteria.
   */
  const searchErrors = async (filter: FederatedErrorFilter): Promise<FederatedErrorResponse | null> => {
    loading.value = true;
    error.value = null;
    try {
      const res = await fetch("/api/v2/leader/errors/search", {
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
      const data: FederatedErrorResponse = await res.json();
      response.value = data;
      return data;
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : "Error search failed";
      return null;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Acknowledge errors matching the filter across specified fleet members.
   */
  const acknowledgeErrors = async (request: FederatedActionRequest): Promise<FederatedActionResponse | null> => {
    return executeAction("/api/v2/leader/errors/acknowledge", request);
  };

  /**
   * Resume errors matching the filter across specified fleet members.
   */
  const resumeErrors = async (request: FederatedActionRequest): Promise<FederatedActionResponse | null> => {
    return executeAction("/api/v2/leader/errors/resume", request);
  };

  /**
   * Replay errors matching the filter across specified fleet members.
   */
  const replayErrors = async (request: FederatedActionRequest): Promise<FederatedActionResponse | null> => {
    return executeAction("/api/v2/leader/errors/replay", request);
  };

  /**
   * Replay and acknowledge errors matching the filter across specified fleet members.
   */
  const replayAndAcknowledgeErrors = async (request: FederatedActionRequest): Promise<FederatedActionResponse | null> => {
    return executeAction("/api/v2/leader/errors/replay-and-acknowledge", request);
  };

  /**
   * Get error summary by data source across all fleet members.
   */
  const errorSummaryByDataSource = async (filter: FederatedErrorFilter): Promise<FederatedErrorSummaryResponse | null> => {
    return fetchSummary("/api/v2/leader/errors/summary/by-data-source", filter);
  };

  /**
   * Get error summary by error message across all fleet members.
   */
  const errorSummaryByMessage = async (filter: FederatedErrorFilter): Promise<FederatedErrorSummaryResponse | null> => {
    return fetchSummary("/api/v2/leader/errors/summary/by-message", filter);
  };

  /**
   * Fetch error summary from fleet.
   */
  const fetchSummary = async (url: string, filter: FederatedErrorFilter): Promise<FederatedErrorSummaryResponse | null> => {
    summaryLoading.value = true;
    summaryError.value = null;
    try {
      const res = await fetch(url, {
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
      const data: FederatedErrorSummaryResponse = await res.json();
      summaryResponse.value = data;
      return data;
    } catch (e: unknown) {
      summaryError.value = e instanceof Error ? e.message : "Summary fetch failed";
      return null;
    } finally {
      summaryLoading.value = false;
    }
  };

  /**
   * Execute an action on fleet members.
   */
  const executeAction = async (url: string, request: FederatedActionRequest): Promise<FederatedActionResponse | null> => {
    actionLoading.value = true;
    actionError.value = null;
    try {
      const res = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(request),
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data: FederatedActionResponse = await res.json();
      actionResponse.value = data;
      return data;
    } catch (e: unknown) {
      actionError.value = e instanceof Error ? e.message : "Action failed";
      return null;
    } finally {
      actionLoading.value = false;
    }
  };

  /**
   * Execute an action on a single fleet member.
   * Returns the result for that member only.
   * If limit is provided, only processes that many items per call (for batching).
   * If flows is provided, uses by-flow mutations instead of matching mutations.
   */
  const executeActionOnMember = async (
    action: "acknowledge" | "resume" | "replay" | "replayAndAcknowledge",
    memberName: string,
    filter: FederatedErrorFilter,
    reason?: string,
    limit?: number,
    flows?: FlowSelection[]
  ): Promise<FederatedActionResult | null> => {
    const urlMap: Record<string, string> = {
      acknowledge: "/api/v2/leader/errors/acknowledge",
      resume: "/api/v2/leader/errors/resume",
      replay: "/api/v2/leader/errors/replay",
      replayAndAcknowledge: "/api/v2/leader/errors/replay-and-acknowledge",
    };

    const request: FederatedActionRequest = {
      filter,
      reason,
      memberNames: [memberName],
      limit,
      flows,
    };

    try {
      const res = await fetch(urlMap[action], {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(request),
      });
      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`);
      }
      const data: FederatedActionResponse = await res.json();
      // Return the result for the specific member
      return data.results.find((r) => r.memberName === memberName) || null;
    } catch (e: unknown) {
      return {
        memberName,
        successCount: 0,
        failureCount: 0,
        status: "UNREACHABLE",
        error: e instanceof Error ? e.message : "Action failed",
      };
    }
  };

  /**
   * Build a URL to open the member's errors page with the same filters.
   * The URL format matches the errors page query string format.
   */
  const buildMemberErrorsUrl = (memberUrl: string, filter: FederatedErrorFilter): string => {
    const params = new URLSearchParams();

    if (filter.dataSources?.length) params.set("dataSources", filter.dataSources.join(","));
    if (filter.errorCause) params.set("errorCause", filter.errorCause);
    if (filter.errorAcknowledged !== undefined) params.set("acknowledged", String(filter.errorAcknowledged));

    const queryString = params.toString();
    const baseUrl = memberUrl === "local" ? "" : memberUrl;
    return `${baseUrl}/errors${queryString ? "?" + queryString : ""}`;
  };

  return {
    loading,
    response,
    error,
    actionLoading,
    actionResponse,
    actionError,
    summaryLoading,
    summaryResponse,
    summaryError,
    searchErrors,
    acknowledgeErrors,
    resumeErrors,
    replayErrors,
    replayAndAcknowledgeErrors,
    errorSummaryByDataSource,
    errorSummaryByMessage,
    executeActionOnMember,
    buildMemberErrorsUrl,
  };
}
