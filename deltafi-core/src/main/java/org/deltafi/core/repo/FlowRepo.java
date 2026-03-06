/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

import static org.deltafi.core.services.PluginService.SYSTEM_PLUGIN_ARTIFACT_ID;
import static org.deltafi.core.services.PluginService.SYSTEM_PLUGIN_GROUP_ID;

@Repository
public interface FlowRepo<R extends Flow> extends JpaRepository<Flow, UUID> {
    // ON CONFLICT fragment: keep existing flow_status, apply state/testMode from snapshot
    String FLOW_STATE_UPSERT_FROM_SNAPSHOT =
            "flow_status = jsonb_set(jsonb_set(flows.flow_status, " +
            "'{state}', EXCLUDED.flow_status->'state'), " +
            "'{testMode}', EXCLUDED.flow_status->'testMode')";

    // ON CONFLICT fragment: use plugin's flow_status, preserve state + testMode from existing, include plugin errors
    String FLOW_STATE_UPSERT_FROM_PLUGIN =
            "flow_status = jsonb_set(jsonb_set(jsonb_set(EXCLUDED.flow_status, " +
            "'{state}', flows.flow_status->'state'), " +
            "'{testMode}', flows.flow_status->'testMode'), " +
            "'{errors}', COALESCE(EXCLUDED.flow_status->'errors', '[]'::jsonb))";

    @Query("SELECT f FROM Flow f WHERE f.name = ?1 AND f.type = ?2")
    <T extends Flow> Optional<T> findByNameAndType(String name, FlowType type, Class<T> clazz);

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
            "AND type = :#{#type.name}",
            nativeQuery = true)
    void updateSystemPluginFlowVersions(String version, FlowType type);

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
            "AND f.type = :#{#type.name}",
            nativeQuery = true)
    List<String> findRunningBySourcePlugin(String groupId, String artifactId, String version, FlowType type);

    /**
     * Update the maximum errors for a flow and return the updated flow.
     * This is a default method that individual repository interfaces can override
     * with more specific return types.
     */
    default Flow updateMaxErrors(String flowName, int maxErrors) {
        throw new UnsupportedOperationException("updateMaxErrors must be implemented by specific repository");
    }

    /**
     * Upsert a flow from snapshot
     * Uses INSERT ... ON CONFLICT to atomically insert or update in a single operation.
     * - If flow doesn't exist: inserts the full placeholder
     * - If flow exists (created by plugin): updates only snapshot-related fields (state, testMode, maxErrors, rateLimit)
     * This ensures that real flows from plugin registration are preserved while applying snapshot state.
     * Each specific repository must override this method to handle its own flow type.
     *
     * @param flow the flow to upsert
     */
    default void upsertFlowFromSnapshot(R flow) {
        throw new UnsupportedOperationException("upsertFlowFromSnapshot must be implemented by specific repository");
    }

    /**
     * Upsert a flow from plugin registration
     * Uses INSERT ... ON CONFLICT to atomically insert or update in a single operation.
     * - If flow doesn't exist: inserts the full flow
     * - If flow exists (placeholder from snapshot): updates definition columns from the real flow
     *   while preserving state columns (state, testMode, maxErrors, rateLimit) from the existing placeholder
     * Each specific repository must override this method to handle its own flow type.
     *
     * @param flow the flow to upsert
     */
    default void upsertFlowFromPlugin(R flow) {
        throw new UnsupportedOperationException("upsertFlowFromPlugin must be implemented by specific repository");
    }
}
