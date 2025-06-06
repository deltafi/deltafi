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

import org.deltafi.common.types.FlowType;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.generated.types.*;
import org.deltafi.core.types.DeltaFileDeleteDTO;
import org.deltafi.core.types.DeltaFiles;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DeltaFileRepoCustom {
    /**
     * Find stale deltaFiles that may need to be requeued, and update the last modified time of the QUEUED action.
     *
     * @param requeueTime age for searching for expired actions to requeue
     * @param requeueDuration duration to wait for an action to finish before requeuing
     * @param skipActions Set of actions to not requeue
     * @param skipDids Set of dids to not requeue
     * @param limit maximum number of results to return
     * @return the list of the DeltaFiles to be requeued
     */
    List<DeltaFile> findForRequeue(OffsetDateTime requeueTime, Duration requeueDuration, Set<String> skipActions, Set<UUID> skipDids, int limit);

    /**
     * Requeue up to maxFiles COLD_QUEUED DeltaFiles with the given action names
     *
     * @param actionClass requeue actions with this actionClass
     * @param maxFiles limit the query to this many files
     * @return the list of the DeltaFiles to be requeued
     */
    List<DeltaFile> findColdQueuedForRequeue(String actionClass, int maxFiles);

    /**
     * Requeue up to maxFiles COLD_QUEUED DeltaFiles with the given action names
     *
     * @param skipRestDataSources Set of rest data sources to not requeue
     * @param skipTimedDataSource Set of timed data sources to not requeue
     * @param skipTransforms Set of transforms to not requeue
     * @param skipDataSinks Set of data sinks to not requeue
     * @param maxFiles limit the query to this many files
     * @return the list of the DeltaFiles to be requeued
     */
    List<DeltaFile> findPausedForRequeue(Set<String> skipRestDataSources, Set<String> skipTimedDataSource,
                                         Set<String> skipTransforms, Set<String> skipDataSinks, int maxFiles);

    /**
     * Find DeltaFiles that are ready for an automatic resume after encountering an error.
     *
     * @param maxReadyTime upper limit for finding matching DeltaFiles
     * @param batchSize maximum number of files to return
     * @return the list of the DeltaFiles to be resumed
     */
    List<DeltaFile> findReadyForAutoResume(OffsetDateTime maxReadyTime, int batchSize);

    /**
     * Search for DeltaFiles in an ERROR stage which may be candidates for applying
     *  a new or recently updated auto resume policy.
     *
     * @param flowName Optionally limit search to those matching dataSource
     * @return the list of the DeltaFiles to be checked
     */
    List<DeltaFile> findResumePolicyCandidates(String flowName);

    /**
     * Delete DeltaFiles that match the given criteria if they have no content.
     *
     * @param createdBefore if non-null find DeltaFiles created before this date
     * @param completedBefore if non-null find DeltaFiles in the completed stage that were last modified before this date
     * @param minBytes only delete deltaFiles greater than or equal to this size
     * @param flow if non-null the deltaFiles must have this flow set in the source info
     * @param batchSize the maximum number of deltaFiles to delete
     *                  @param ordered whether the oldest deltaFiles should be deleted first
     * @return the number of rows deleted
     */
    int deleteIfNoContent(OffsetDateTime createdBefore, OffsetDateTime completedBefore, long minBytes, String flow,
                          int batchSize, boolean ordered);

    /**
     * Find DeltaFiles that match the given criteria.
     * Any actions in a non-terminal state will be marked as errors stating the given policy
     * marked the DeltaFile for deletion.
     *
     * @param createdBefore if non-null find DeltaFiles created before this date
     * @param completedBefore if non-null find DeltaFiles in the completed stage that were last modified before this date
     * @param minBytes only delete deltaFiles greater than or equal to this size
     * @param flow if non-null the DeltaFiles must have this dataSource set in the source info
     * @param deleteMetadata whether we are finding files to be finally deleted.  If this is false, DeltaFiles that have already had their content deleted will not be selected
     * @param includePinned whether to include pinned DeltaFiles
     * @param batchSize maximum number to delete
     * @param returnContentObjectIds whether content object ids should be included in the response
     * @param ordered whether the oldest deltaFiles should be deleted first
     * @return the list of DeltaFile information marked for deletion
     */
    List<DeltaFileDeleteDTO> findForTimedDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore,
            long minBytes, String flow, boolean deleteMetadata, boolean includePinned, int batchSize,
            boolean returnContentObjectIds, boolean ordered);

    /**
     * Find the oldest DeltaFiles up to bytesToDelete size that match the dataSource (if given).
     * Any actions in a non-terminal state will be marked as errors stating the given policy
     * marked the DeltaFile for deletion.
     *
     * @param bytesToDelete the number of bytes that must be deleted
     * @param batchSize maximum number to delete
     * @param returnContentObjectIds whether content object ids should be included in the response
     * @return the list of DeltaFile information marked for deletion
     */
    List<DeltaFileDeleteDTO> findForDiskSpaceDelete(long bytesToDelete, int batchSize, boolean returnContentObjectIds);

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

    /** Return a list of DeltaFiles matching the given criteria
     *
     * @param limit Maximum number of DeltaFiles to return (defaults to 50)
     * @param filter Filters are used to constrain DeltaFiles that are returned
     * @return the list of DeltaFiles
     */
    List<DeltaFile> deltaFiles(DeltaFilesFilter filter, int limit);

    /**
     * For each did in the list, update the corresponding DeltaFile contentDeleted to value
     *
     * @param dids List of dids for DeltaFiles to be updated
     * @param now Timestamp for deletion
     * @param reason Reason for deletion
     */
    void setContentDeletedByDidIn(List<UUID> dids, OffsetDateTime now, String reason);

    void insertOne(DeltaFile deltaFile);
    void insertBatch(List<DeltaFile> deltaFiles, int batchSize);
    void batchedBulkDeleteByDidIn(List<UUID> dids);

    /**
     * Find the ids of the DeltaFiles with an error in the flow with the given type and name
     * @param flowType type of flow to search for
     * @param flowName name of flow to search for
     * @param includeAcknowledged include dids of DeltaFiles with acknowledged errors when true
     * @param limit max number of DeltaFile dids to return
     * @return the list of dids who can be resumed
     */
    List<DeltaFile> findForResumeByFlowTypeAndName(FlowType flowType, String flowName, boolean includeAcknowledged, int limit);

    /**
     * Find the ids of the DeltaFiles with the given errorCause that can be resumed
     * @param errorCause error cause to search for using an exact match
     * @param includeAcknowledged include dids of DeltaFiles with acknowledged errors when true
     * @param limit max number of DeltaFile dids to return
     * @return the list of dids who can be resumed
     */
    List<DeltaFile> findForResumeByErrorCause(String errorCause, boolean includeAcknowledged, int limit);

    /**
     * Finds all DeltaFiles marked as waitingForChildren that have no non-terminal children,
     * updates their waitingForChildren flag to false and terminal to true and returns count of updated records.
     * This allows parent DeltaFiles to complete once all their split/child DeltaFiles have completed.
     * @return number of parent DeltaFiles that were updated to no longer wait for children
     */
    int completeParents();
}
