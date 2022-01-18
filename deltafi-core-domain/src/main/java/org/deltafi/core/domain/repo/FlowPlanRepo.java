package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.FlowPlan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FlowPlanRepo extends MongoRepository<FlowPlan, String> {
    int deleteByName(String name);

    FlowPlan findByName(String name);
}
