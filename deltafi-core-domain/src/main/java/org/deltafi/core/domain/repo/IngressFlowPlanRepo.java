package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.types.IngressFlowPlan;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface IngressFlowPlanRepo extends MongoRepository<IngressFlowPlan, String> {
    /**
     * Delete any flow plans where the source plugin matches the plugin coordinates
     * @param sourcePlugin the plugin coordinates to match
     * @return - the number of flow plans deleted
     */
    int deleteBySourcePlugin(PluginCoordinates sourcePlugin);

    /**
     * Find the IngressFlowPlans with the given sourcePlugin
     * @param sourcePlugin PluginCoordinates to search by
     * @return the IngressFlowPlans with the given sourcePlugin
     */
    List<IngressFlowPlan> findBySourcePlugin(PluginCoordinates sourcePlugin);
}
