package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.types.IngressFlow;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngressFlowRepo extends MongoRepository<IngressFlow, String>, FlowRepoCustom<IngressFlow> {
    int deleteBySourcePlugin(PluginCoordinates sourcePlugin);
}
