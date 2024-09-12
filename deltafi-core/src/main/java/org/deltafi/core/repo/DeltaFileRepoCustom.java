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
    List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, Duration requeueDuration, Set<String> skipActions, Set<UUID> skipDids, int limit);

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
    void updateForAutoResume(List<UUID> dids, String policyName, OffsetDateTime nextAutoResume);

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
     * @return the list of DeltaFile information marked for deletion
     */
    List<DeltaFileDeleteDTO> findForTimedDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, long minBytes, String flow, boolean deleteMetadata, int batchSize);

    /**
     * Find the oldest DeltaFiles up to bytesToDelete size that match the flow (if given).
     * Any actions in a non-terminal state will be marked as errors stating the given policy
     * marked the DeltaFile for deletion.
     *
     * @param bytesToDelete - the number of bytes that must be deleted
     * @param flow - if non-null the DeltaFiles must have this flow set in the source info
     * @param batchSize - maximum number to delete
     * @return the list of DeltaFile information marked for deletion
     */
    List<DeltaFileDeleteDTO> findForDiskSpaceDelete(long bytesToDelete, String flow, int batchSize);

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
     * For each did in the list, update the corresponding DeltaFile contentDeleted to value
     *
     * @param dids List of dids for DeltaFiles to be updated
     * @param now Timestamp for deletion
     * @param reason Reason for deletion
     */
    void setContentDeletedByDidIn(List<UUID> dids, OffsetDateTime now, String reason);

    void batchInsert(List<DeltaFile> deltaFiles);
    void batchedBulkDeleteByDidIn(List<UUID> dids);
}
