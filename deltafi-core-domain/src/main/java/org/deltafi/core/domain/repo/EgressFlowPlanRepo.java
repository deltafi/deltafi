package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.types.EgressFlowPlan;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EgressFlowPlanRepo extends MongoRepository<EgressFlowPlan, String> {
    /**
     * Delete any flow plans where the source plugin matches the plugin coordinates
     *
     * @param pluginCoordinates the plugin coordinates to match
     * @return - the number of flow plans deleted
     */
    int deleteBySourcePlugin(PluginCoordinates pluginCoordinates);

    /**
     * Find the EgressFlowPlans with the given sourcePlugin
     * @param sourcePlugin PluginCoordinates to search by
     * @return the EgressFlowPlans with the given sourcePlugin
     */
    List<EgressFlowPlan> findBySourcePlugin(PluginCoordinates sourcePlugin);
}
