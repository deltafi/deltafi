/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.springframework.transaction.annotation.Transactional;
import org.deltafi.common.types.FlowType;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.*;
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
public interface FlowRepo extends JpaRepository<Flow, UUID> {
    @Query("SELECT f FROM Flow f WHERE f.name = :name AND TYPE(f) = :type")
    <T extends Flow> Optional<T> findByNameAndType(String name, Class<T> type);

    @Query(value = "SELECT f.* FROM flows f " +
            "WHERE f.flow_status ->> 'state' = :#{#state.name} " +
            "AND f.type = :#{#type.name}",
            nativeQuery = true)
    List<Flow> findByFlowStatusStateAndType(FlowState state, FlowType type);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM flows WHERE " +
            "source_plugin->>'groupId' = :#{#sourcePlugin.groupId} AND " +
            "source_plugin->>'artifactId' = :#{#sourcePlugin.artifactId} AND " +
            "source_plugin->>'version' = :#{#sourcePlugin.version} AND " +
            "type = :#{#type.name}",
            nativeQuery = true)
    int deleteBySourcePluginAndType(PluginCoordinates sourcePlugin, FlowType type);

    @Transactional
    @Modifying
    void deleteByNameAndType(String name, FlowType type);

    @Query(value = "SELECT * FROM flows WHERE " +
            "source_plugin->>'groupId' = :groupId AND " +
            "source_plugin->>'artifactId' = :artifactId AND " +
            "type = :#{#type.name()}",
            nativeQuery = true)
    List<Flow> findBySourcePluginGroupIdAndSourcePluginArtifactIdAndType(String groupId, String artifactId, FlowType type);

    @Modifying
    @Transactional
    @Query(value = "UPDATE flows SET source_plugin = jsonb_set(source_plugin, '{version}', to_jsonb(:version)) " +
            "WHERE source_plugin ->> 'groupId' = '" + SYSTEM_PLUGIN_GROUP_ID + "' " +
            "AND source_plugin ->> 'artifactId' = '" + SYSTEM_PLUGIN_ARTIFACT_ID + "' " +
            "AND type = :type",
            nativeQuery = true)
    <T extends Flow> void updateSystemPluginFlowVersions(String version, Class<T> type);

    @Modifying
    @Transactional
    @Query(value = "UPDATE flows SET flow_status = jsonb_set(flow_status, '{state}', to_jsonb(:#{#flowState.name})) " +
            "WHERE name = :flowName AND type = :#{#type.name}",
            nativeQuery = true)
    int updateFlowStatusState(String flowName, FlowState flowState, FlowType type);

    @Modifying
    @Transactional
    @Query(value = "UPDATE flows SET flow_status = jsonb_set(flow_status, '{testMode}', to_jsonb(:testMode)) " +
            "WHERE name = :flowName AND type = :#{#type.name}",
            nativeQuery = true)
    int updateFlowStatusTestMode(String flowName, boolean testMode, FlowType type);

    @Query(value = "SELECT f.name FROM flows f " +
            "WHERE f.flow_status ->> 'state' = 'RUNNING' " +
            "AND f.source_plugin ->> 'groupId' = :groupId " +
            "AND f.source_plugin ->> 'artifactId' = :artifactId " +
            "AND f.source_plugin ->> 'version' = :version " +
            "AND f.type = :type",
            nativeQuery = true)
    <T extends Flow> List<String> findRunningBySourcePlugin(String groupId, String artifactId, String version, Class<T> type);
}
