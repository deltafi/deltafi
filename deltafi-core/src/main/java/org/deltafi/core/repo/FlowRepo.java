/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.repo;

import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.Flow;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

import static org.deltafi.core.plugin.SystemPluginService.SYSTEM_PLUGIN_ARTIFACT_ID;
import static org.deltafi.core.plugin.SystemPluginService.SYSTEM_PLUGIN_GROUP_ID;

@NoRepositoryBean
public interface FlowRepo<T extends Flow> extends MongoRepository<T, String>, FlowRepoCustom<T> {

    /**
     * Delete any flows where the source plugin matches the plugin coordinates
     *
     * @param sourcePlugin the plugin coordinates to match
     * @return - the number of flows deleted
     */
    int deleteBySourcePlugin(PluginCoordinates sourcePlugin);

    /**
     * Find the flow with the given groupId and artifactId
     * @param groupId plugin groupId to search by
     * @param artifactId plugin artifactId to search by
     * @return the flow with the given groupId and artifactId
     */
    @Query("{ 'sourcePlugin.groupId': ?0, 'sourcePlugin.artifactId': ?1 }")
    List<T> findByGroupIdAndArtifactId(String groupId, String artifactId);

    /**
     * Find a list of flows with the given flow state
     * @param state to search for
     * @return list of flows with the given flow state
     */
    List<T> findByFlowStatusState(FlowState state);

    /**
     * Update the system-plugin flows sourcePlugin version to the current running version
     * @param version current running version
     */
    @Query("{ 'sourcePlugin.groupId': '" + SYSTEM_PLUGIN_GROUP_ID + "', 'sourcePlugin.artifactId': '" + SYSTEM_PLUGIN_ARTIFACT_ID + "'}")
    @Update("{ '$set' : { 'sourcePlugin.version' : ?0 } }")
    void updateSystemPluginFlowVersions(String version);
}
