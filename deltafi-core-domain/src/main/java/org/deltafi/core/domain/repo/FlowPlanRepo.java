/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.types.FlowPlan;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

@NoRepositoryBean
public interface FlowPlanRepo<T extends FlowPlan> extends MongoRepository<T, String> {
    /**
     * Delete any flow plans where the source plugin matches the plugin coordinates
     * @param pluginCoordinates the plugin coordinates to match
     * @return - the number of flow plans deleted
     */
    int deleteBySourcePlugin(PluginCoordinates pluginCoordinates);

    /**
     * Find the flow plans with the given sourcePlugin
     * @param sourcePlugin PluginCoordinates to search by
     * @return the flow plans with the given sourcePlugin
     */
    List<T> findBySourcePlugin(PluginCoordinates sourcePlugin);
}
