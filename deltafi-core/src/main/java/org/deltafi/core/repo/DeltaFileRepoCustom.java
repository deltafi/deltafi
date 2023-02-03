/**
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
import org.deltafi.core.types.DeltaFiles;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

public interface DeltaFileRepoCustom {
    /**
     * Reads the ids for all DeltaFiles in the repository.
     * @return the list of ids for all DeltaFiles in the repository that still have content
     */
    List<String> readDidsWithContent();

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
     * Set or verify the expiration TTL value on the collection.
     *
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
     * @return the list of the DeltaFiles to be requeued
     */
    List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, int requeueSeconds);

    /**
     * Find DeltaFiles that match the given criteria.
     * Any actions in a non-terminal state will be marked as errors stating the given policy
     * marked the DeltaFile for deletion.
     *
     * @param createdBefore - if non-null find DeltaFiles created before this date
     * @param completedBefore - if non-null find DeltaFiles in the completed stage that were last modified before this date
     * @param minBytes - only delete deltaFiles greater than or equal to this size
     * @param flow - if non-null the DeltaFiles must have this flow set in the source info
     * @param policy - policy name to use in any metadata
     * @param deleteMetadata - whether we are finding files to be finally deleted.  if this is false, DeltaFiles that have already had their content deleted will not be selected
     * @param batchSize - maximum number to delete
     * @return the list of DeltaFiles marked for deletion
     */
    List<DeltaFile> findForDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, long minBytes, String flow, String policy, boolean deleteMetadata, int batchSize);

    /**
     * Find the oldest DeltaFiles up to bytesToDelete size that match the flow (if given).
     * Any actions in a non-terminal state will be marked as errors stating the given policy
     * marked the DeltaFile for deletion.
     *
     * @param bytesToDelete - the number of bytes that must be deleted
     * @param flow - if non-null the DeltaFiles must have this flow set in the source info
     * @param policy - policy name to use in any metadata
     * @param batchSize - maximum number to delete
     * @return the list of DeltaFiles marked for deletion
     */
    List<DeltaFile> findForDelete(long bytesToDelete, String flow, String policy, int batchSize);

    DeltaFiles deltaFiles(Integer offset, int limit, DeltaFilesFilter filter, DeltaFileOrder orderBy);

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
     * @return the ErrorsByFlow
     */
    ErrorsByFlow getErrorSummaryByFlow(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy);

    /**
     * Count the number of errors per errorMessage + flow using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each errorMessage + flow grouping are included.
     *
     * @param offset  Offset to use for pagination (defaults to 0)
     * @param limit   Maximum number of flows to return
     * @param filter  Filters are used to constrain which DeltaFiles are used in computation
     * @param orderBy Determines what fields the returned records will be sorted by
     * @return the ErrorsByMessage
     */
    ErrorsByMessage getErrorSummaryByMessage(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileOrder orderBy);

    /**
     * Get a list of all domains that are currently assigned deltaFiles
     *
     * @return the Domains
     */
    List<String> domains();

    /**
     * Get a list of all indexed metadata keys currently assigned by deltaFiles
     *
     * @param domain An optional domain to filter by
     * @return the indexed metadata
     */
    List<String> indexedMetadataKeys(String domain);

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
}
