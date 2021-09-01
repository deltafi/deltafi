package org.deltafi.core.domain.api.repo;

import org.deltafi.core.domain.api.types.DeltaFile;

import java.time.OffsetDateTime;
import java.util.List;

public interface DeltaFileRepoCustom {

    /**
     * Find stale deltaFiles that may need to be requeued, and update the last modified time of the QUEUED action.
     *
     * @return - list of the DeltaFiles to be requeued
     */
    List<DeltaFile> updateForRequeue(OffsetDateTime requeueTime, int requeueSeconds);

    /**
     * Find DeltaFiles that match the given criteria and move them to the Delete stage.
     * Any actions in a non-terminal state will be marked as errors stating the given policy
     * marked the DeltaFile for deletion.
     *
     * @param createdBefore - if non-null find DeltaFiles created before this date
     * @param completedBefore - if non-null find DeltaFiles in the completed stage that were last modified before this date
     * @param flow - if non-null the DeltaFiles must have this flow set in the source info
     * @param policy - policy name to use in any metadata
     * @return the list of DeltaFiles marked for deletion
     */
    List<DeltaFile> markForDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, String flow, String policy);

}