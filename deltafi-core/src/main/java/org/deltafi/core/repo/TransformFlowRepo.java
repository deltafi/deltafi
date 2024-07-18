package org.deltafi.core.repo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TransformFlowRepo extends FlowRepo {
    @Modifying
    @Query("UPDATE TransformFlow t SET t.maxErrors = :maxErrors WHERE t.name = :flowName")
    int updateMaxErrors(String flowName, int maxErrors);
}
