/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.common.types.DeltaFile;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.ColdQueuedActionSummary;
import org.deltafi.core.types.DeltaFiles;
import org.deltafi.core.types.ErrorSummaryFilter;
import org.deltafi.core.types.SummaryByFlow;
import org.deltafi.core.types.FilteredSummaryFilter;
import org.deltafi.core.types.SummaryByFlowAndMessage;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DeltaFileRepoCustom {
    /**
     * Ensure the latest versions of the indices defined in the Repository
     * are created. Ensure the TTL index has the latest expiration value.
     * Remove indices that are not defined in the Repository.
     *
     * @param newTtl duration that a DeltaFile should be persisted
     *
     */
    void ensureAllIndices(Duration newTtl);

    /**
     * Create or update the expiration TTL value on the collection
     * if it doesn't exist, or it has changed.
     * @param expirationDuration duration until expiration
     */
    void setExpirationIndex(Duration expirationDuration);

    /**
     * Get a list of all indexes on the collection.
     *
     * @return list of indexes.
     */
    List<IndexInfo> getIndexes();

    /**
     * Get the current expiration TTL on the collection.
     *
     * @return Duration, current TTL value, or null if not set.
     */
    Duration getTtlExpiration();

    /**
     * Find stale deltaFiles that may need to be requeued, and update the last modified time of the QUEUED action.
     *
     * @param requeueTime age for searching for expired actions to requeue
     * @param requeueDuration duration to wait for an action to finish before requeuing
     * @param skipActions Set of actions to not requeue
     * @param skipDids Set of dids to not requeue
     * @return the list of the DeltaFiles to be requeued
     */
    List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, Duration requeueDuration, Set<String> skipActions, Set<String> skipDids);

    /**
     * Requeue up to maxFiles COLD_QUEUED DeltaFiles with the given action names
     *
     * @param actionNames requeue actions with these names
     * @param maxFiles limit the query to this many files
     * @param modified time to mark the files QUEUED
     * @return the list of the DeltaFiles to be requeued
     */
    List<DeltaFile> updateColdQueuedForRequeue(List<String> actionNames, int maxFiles, OffsetDateTime modified);

    /**
     * Find DeltaFiles that are ready for an automatic resume after encountering an error.
     *
     * @param maxReadyTime upper limit for finding matching DeltaFiles
     * @return the list of the DeltaFiles to be resumed
     */
    List<DeltaFile> findReadyForAutoResume(OffsetDateTime maxReadyTime);

    /**
     * Search for DeltaFiles in an ERROR stage which may be candidates for applying
     *  a new or recently updated auto resume policy.
     *
     * @param flowName Optionally limit search to those matching flow
     * @return the list of the DeltaFiles to be checked
     */
    List<DeltaFile> findResumePolicyCandidates(String flowName);

    /**
     *  Schedule a list of DeltaFiles for auto resume, by setting
     *  the next resume properties.
     *
     * @param dids A list of dids which should be updated
     * @param policyName The auto resume policy name which prompted the update
     * @param nextAutoResume The time the DeltaFile(s) should be scheduled for auto resume
     */
    void updateForAutoResume(List<String> dids, String policyName, OffsetDateTime nextAutoResume);

    /**
     * Find DeltaFiles that match the given criteria.
     * Any actions in a non-terminal state will be marked as errors stating the given policy
     * marked the DeltaFile for deletion.
     *
     * @param createdBefore - if non-null find DeltaFiles created before this date
     * @param completedBefore - if non-null find DeltaFiles in the completed stage that were last modified before this date
     * @param minBytes - only delete deltaFiles greater than or equal to this size
     * @param flow - if non-null the DeltaFiles must have this flow set in the source info
     * @param deleteMetadata - whether we are finding files to be finally deleted.  if this is false, DeltaFiles that have already had their content deleted will not be selected
     * @param batchSize - maximum number to delete
     * @return the list of DeltaFiles marked for deletion
     */
    List<DeltaFile> findForTimedDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, long minBytes, String flow, boolean deleteMetadata, int batchSize);

    /**
     * Find the oldest DeltaFiles up to bytesToDelete size that match the flow (if given).
     * Any actions in a non-terminal state will be marked as errors stating the given policy
     * marked the DeltaFile for deletion.
     *
     * @param bytesToDelete - the number of bytes that must be deleted
     * @param flow - if non-null the DeltaFiles must have this flow set in the source info
     * @param batchSize - maximum number to delete
     * @return the list of DeltaFiles marked for deletion
     */
    List<DeltaFile> findForDiskSpaceDelete(long bytesToDelete, String flow, int batchSize);

    /** Return a list of DeltaFiles matching the given criteria
     *
     * @param offset Offset to use for pagination (defaults to 0)
     * @param limit Maximum number of DeltaFiles to return (defaults to 50)
     * @param filter Filters are used to constrain DeltaFiles that are returned
     * @param orderBy Determines what fields the returned records will be sorted by
     * @param includeFields List of projection fields to return, or all fields if null
     * @return the list of DeltaFiles
     */
    DeltaFiles deltaFiles(Integer offset, int limit, DeltaFilesFilter filter, DeltaFileOrder orderBy, List<String> includeFields);

    /**
     * Count the number of errors per flow using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each flow are included.
     *
     * @param offset  Offset to use for pagination (defaults to 0)
     * @param limit   Maximum number of flows to return
     * @param filter  Filters are used to constrain which DeltaFiles are used in computation
     * @param orderBy Determines what fields the returned records will be sorted by
     * @return the SummaryByFlow
     */
    SummaryByFlow getErrorSummaryByFlow(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy);

    /**
     * Count the number of filtered DeltaFiles per flow using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each flow are included.
     *
     * @param offset  Offset to use for pagination (defaults to 0)
     * @param limit   Maximum number of flows to return
     * @param filter  Filters are used to constrain which DeltaFiles are used in computation
     * @param orderBy Determines what fields the returned records will be sorted by
     * @return the SummaryByFlow
     */
    SummaryByFlow getFilteredSummaryByFlow(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileOrder orderBy);

    /**
     * Count the number of errors per errorMessage + flow using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each errorMessage + flow grouping are included.
     *
     * @param offset  Offset to use for pagination (defaults to 0)
     * @param limit   Maximum number of flows to return
     * @param filter  Filters are used to constrain which DeltaFiles are used in computation
     * @param orderBy Determines what fields the returned records will be sorted by
     * @return the SummaryByFlowAndMessage
     */
    SummaryByFlowAndMessage getErrorSummaryByMessage(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy);

    /**
     * Count the number of filtered DeltaFiles per flow and filterCause using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each filterCause + flow grouping are included.
     *
     * @param offset  Offset to use for pagination (defaults to 0)
     * @param limit   Maximum number of flows to return
     * @param filter  Filters are used to constrain which DeltaFiles are used in computation
     * @param orderBy Determines what fields the returned records will be sorted by
     * @return the SummaryByFlowAndMessage
     */
    SummaryByFlowAndMessage getFilteredSummaryByMessage(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileOrder orderBy);

    /**
     * Retrieves the error counts for the specified set of flows.  Only unacknowledged errors are considered.
     *
     * @param flows A set of {@code String} values representing the flow names for which to retrieve error counts.
     * @return A {@code Map<String, Integer>} where the key is the flow name and the value is the error count for that flow.
     */
    Map<String, Integer> errorCountsByFlow(Set<String> flows);

    /**
     * Get a list of all domains that are currently assigned deltaFiles
     *
     * @return the Domains
     */
    List<String> domains();

    /**
     * Get a list of all annotation keys currently assigned by deltaFiles
     *
     * @param domain An optional domain to filter by
     * @return the annotation keys
     */
    List<String> annotationKeys(String domain);

    /**
     * For each did in the list, update the corresponding DeltaFile contentDeleted to value
     *
     * @param dids List of dids for DeltaFiles to be updated
     * @param now Timestamp for deletion
     * @param reason Reason for deletion
     */
    void setContentDeletedByDidIn(List<String> dids, OffsetDateTime now, String reason);

    /**
     * Perform an estimated count of documents based on collection stats
     * @return the estimated count
     */
    Long estimatedCount();

    /**
     * Get count and sizes of deltaFiles in the system
     * @return stats
     */
    DeltaFileStats deltaFileStats();

    /**
     * Remove the given flow from pendingAnnotationsForFlows for any DeltaFile
     * that currently has the flow in the set.
     * Unset any pendingAnnotationsForFlows that are empty after removing the flow
     * @param flow that should be removed from pendingAnnotationsForFlows
     */
    void removePendingAnnotationsForFlow(String flow);

    /**
     * Retrieves a summary of all cold queued actions.
     *
     * @return A list of ColdQueuedActionSummary objects representing the summary of cold queued actions.
     */
    List<ColdQueuedActionSummary> coldQueuedActionsSummary();

    /**
     * Updates the specified version of the DeltaFile with the given update
     *
     * @param  did the document ID
     * @param  version the version number of the document to update
     * @param  update the update to apply to the document
     * @return true if the update was successful, false otherwise
     */
    boolean update(String did, long version, Update update);

    void batchedBulkDeleteByDidIn(List<String> dids);
}
