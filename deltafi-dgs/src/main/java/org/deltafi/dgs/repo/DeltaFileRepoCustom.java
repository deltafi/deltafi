package org.deltafi.dgs.repo;

import org.deltafi.dgs.api.types.DeltaFile;

import java.time.OffsetDateTime;
import java.util.List;

public interface DeltaFileRepoCustom {

    /**
     * Find the deltaFiles ready to be dispatched, update the action state and
     * action history.
     *
     * @param actionName - action to search for
     * @return - list of the DeltaFiles with the updated values
     */
    List<DeltaFile> findAndDispatchForAction(String actionName, Integer limit, Boolean dryRun);

    /**
     * Find DeltaFiles that match the given criteria and move them to the Delete stage.
     * Any actions in a non-terminal state will be marked as errors stating the given policy
     * marked the DeltaFile for deletion.
     *
     * @param createdBefore - if non-null find DeltaFiles created before this date
     * @param completedBefore - if non-null find DeltaFiles in the completed stage that were last modified before this date
     * @param flow - if non-null the DeltaFiles must have this flow set in the source info
     * @param policy - policy name to use in any metadata
     */
    void markForDelete(OffsetDateTime createdBefore, OffsetDateTime completedBefore, String flow, String policy);

}
