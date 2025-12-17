/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.repo;

import org.deltafi.core.generated.types.DeltaFileDirection;
import org.deltafi.core.generated.types.SummaryByFlowSort;
import org.deltafi.core.generated.types.SummaryByMessageSort;
import org.deltafi.core.types.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DeltaFileFlowRepoCustom {
    /**
     * Retrieves the error counts for the specified set of dataSources.  Only unacknowledged errors are considered.
     *
     * @param dataSources A set of {@code String} values representing the flow names for which to retrieve error counts.
     * @return A {@code Map<String, Integer>} where the key is the flow name and the value is the error count for that flow.
     */
    Map<String, Integer> errorCountsByDataSource(Set<String> dataSources);

    /**
     * Count the number of errors per dataSource using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each dataSource are included.
     *
     * @param offset    Offset to use for pagination (defaults to 0)
     * @param limit     Maximum number of flows to return
     * @param filter    Filters are used to constrain which DeltaFiles are used in computation
     * @param direction Determines what order the returned records will be sorted by
     * @param sortField Field to order by, optional
     * @return the SummaryByFlow
     */
    SummaryByFlow getErrorSummaryByFlow(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileDirection direction, SummaryByFlowSort sortField);

    /**
     * Count the number of filtered DeltaFiles per dataSource using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each dataSource are included.
     *
     * @param offset    Offset to use for pagination (defaults to 0)
     * @param limit     Maximum number of flows to return
     * @param filter    Filters are used to constrain which DeltaFiles are used in computation
     * @param direction Determines what order the returned records will be sorted by
     * @param sortField Field to order by, optional
     * @return the SummaryByFlow
     */
    SummaryByFlow getFilteredSummaryByFlow(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileDirection direction, SummaryByFlowSort sortField);

    /**
     * Count the number of errors per errorMessage + dataSource using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each errorMessage + dataSource grouping are included.
     *
     * @param offset    Offset to use for pagination (defaults to 0)
     * @param limit     Maximum number of errorMessage/flows to return
     * @param filter    Filters are used to constrain which DeltaFiles are used in computation
     * @param direction Determines what order the returned records will be sorted by
     * @param sortField Field to order by, optional
     * @return the SummaryByFlowAndMessage
     */
    SummaryByFlowAndMessage getErrorSummaryByMessage(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileDirection direction, SummaryByMessageSort sortField);

    /**
     * Count the number of filtered DeltaFiles per dataSource and filterCause using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each filterCause + dataSource grouping are included.
     *
     * @param offset    Offset to use for pagination (defaults to 0)
     * @param limit     Maximum number of errorMessage/flows to return
     * @param filter    Filters are used to constrain which DeltaFiles are used in computation
     * @param direction Determines what order the returned records will be sorted by
     * @param sortField Field to order by, optional
     * @return the SummaryByFlowAndMessage
     */
    SummaryByFlowAndMessage getFilteredSummaryByMessage(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileDirection direction, SummaryByMessageSort sortField);

    /**
     * Get the distinct list of cold queued action classes (queue names)
     *
     * @return distinct list of action classes that have a cold queued DeltaFile
     */
    List<String> distinctColdQueuedActions();

    /**
     * Retrieves each action with a cold queue, and a count of items in each queue
     *
     * @return A {@code Map<String, Integer>} where the key is the action class and the value is the count of items in that actions's cold queue.
     */
    Map<String, Integer> coldQueuedActionsCount();

    /**
     * Compute the total number of cold queued items
     *
     * @param limit max rows to count
     * @return A {@code Long} for the total number of cold queued items
     */
    Long coldQueuedCount(int limit);

    /**
     * Get cold queue counts per (flowName, actionName, actionClass) from the cold_queue_entries table.
     * This uses the trigger-maintained entries for accurate counts and oldest timestamps.
     *
     * @return list of cold queue metrics
     */
    List<ColdQueueMetrics> getColdQueueCounts();

    /**
     * Cold queue metrics per flow/action.
     */
    record ColdQueueMetrics(String flowName, String flowType, String actionName, String actionClass,
                            int count, java.time.OffsetDateTime oldestQueuedAt) {}

    /**
     * Get the oldest cold queue entry's info (DID and timestamp).
     *
     * @return optional containing the oldest entry info, or empty if no cold queue entries exist
     */
    Optional<OldestColdQueueEntry> getOldestColdQueueEntry();

    /**
     * Info about the oldest cold queue entry.
     */
    record OldestColdQueueEntry(UUID did, java.time.OffsetDateTime queuedAt) {}

}
