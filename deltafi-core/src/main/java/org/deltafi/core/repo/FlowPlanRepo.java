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

import jakarta.transaction.Transactional;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.types.FlowPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.deltafi.core.plugin.SystemPluginService.SYSTEM_PLUGIN_ARTIFACT_ID;
import static org.deltafi.core.plugin.SystemPluginService.SYSTEM_PLUGIN_GROUP_ID;

@Repository
public interface FlowPlanRepo extends JpaRepository<FlowPlanEntity, UUID> {

    List<FlowPlanEntity> findByType(final FlowType type);
    Optional<FlowPlanEntity> findByNameAndType(final String name, final FlowType type);

    /**
     * Delete any flow plans where the source plugin matches the plugin coordinates
     * @param pluginCoordinates the plugin coordinates to match
     * @return - the number of flow plans deleted
     */
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM flow_plan WHERE " +
            "source_plugin->>'groupId' = :#{#pluginCoordinates.groupId} AND " +
            "source_plugin->>'artifactId' = :#{#pluginCoordinates.artifactId} AND " +
            "source_plugin->>'version' = :#{#pluginCoordinates.version}",
            nativeQuery = true)
    int deleteBySourcePlugin(PluginCoordinates pluginCoordinates);

    /**
     * Find the flow plans with the given sourcePlugin
     * @param sourcePlugin PluginCoordinates to search by
     * @return the flow plans with the given sourcePlugin
     */
    @Query(value = "SELECT * FROM flow_plan WHERE " +
            "source_plugin->>'groupId' = :#{#sourcePlugin.groupId} AND " +
            "source_plugin->>'artifactId' = :#{#sourcePlugin.artifactId} AND " +
            "source_plugin->>'version' = :#{#sourcePlugin.version} AND " +
            "type = :#{#type.name()}",
            nativeQuery = true)
    List<FlowPlanEntity> findBySourcePluginAndType(PluginCoordinates sourcePlugin, FlowType type);

    /**
     * Find the flow plans with the given groupId and artifactId
     * @param groupId plugin groupId to search by
     * @param artifactId plugin artifactId to search by
     * @return the flow plans with the given groupId and artifactId
     */
    @Query(value = "SELECT * FROM flow_plan WHERE source_plugin->>'groupId' = :groupId AND source_plugin->>'artifactId' = :artifactId", nativeQuery = true)
    List<FlowPlanEntity> findByGroupIdAndArtifactId(String groupId, String artifactId);

    /**
     * Update the system-plugin flow plans sourcePlugin version to the current running version
     * @param version current running version
     */
    @Transactional
    @Modifying
    @Query(value = "UPDATE flow_plan " +
            "SET source_plugin = jsonb_set(source_plugin, '{version}', to_jsonb(:version)) " +
            "WHERE source_plugin->>'groupId' = '" + SYSTEM_PLUGIN_GROUP_ID + "' " +
            "AND source_plugin->>'artifactId' = '" + SYSTEM_PLUGIN_ARTIFACT_ID + "'",
            nativeQuery = true)
    void updateSystemPluginFlowPlanVersions(String version);
}
