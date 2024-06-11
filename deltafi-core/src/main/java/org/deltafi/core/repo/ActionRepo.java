package org.deltafi.core.repo;

import org.deltafi.common.types.ActionState;
import org.deltafi.core.types.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ActionRepo extends JpaRepository<Action, UUID>, ActionRepoCustom {
    long countByStateAndErrorAcknowledgedIsNull(ActionState stage);
}
