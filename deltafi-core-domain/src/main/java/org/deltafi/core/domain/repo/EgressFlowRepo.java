package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.types.EgressFlow;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EgressFlowRepo extends MongoRepository<EgressFlow, String>, FlowRepoCustom<EgressFlow> {
    int deleteBySourcePlugin(PluginCoordinates sourcePlugin);
}