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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.deltafi.common.types.PluginCoordinates;
import org.deltafi.common.types.Variable;
import org.deltafi.core.types.PluginVariables;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PluginVariableRepo extends JpaRepository<PluginVariables, UUID> {

    @Query("SELECT pv FROM PluginVariables pv WHERE pv.groupId = :groupId AND pv.artifactId = :artifactId AND pv.version = :version")
    Optional<PluginVariables> findBySourcePlugin(String groupId, String artifactId, String version);

    default Optional<PluginVariables> findBySourcePlugin(PluginCoordinates sourcePlugin) {
        return findBySourcePlugin(sourcePlugin.getGroupId(), sourcePlugin.getArtifactId(), sourcePlugin.getVersion());
    }

    @Query("SELECT pv FROM PluginVariables pv WHERE pv.groupId = :groupId AND pv.artifactId = :artifactId")
    List<PluginVariables> findIgnoringVersion(String groupId, String artifactId);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO plugin_variables (id, group_id, artifact_id, version, variables) " +
            "VALUES (:id, :groupId, :artifactId, :version, CAST(:variables AS jsonb)) " +
            "ON CONFLICT (group_id, artifact_id, version) DO UPDATE " +
            "SET variables = CAST(:variables AS jsonb)",
            nativeQuery = true)
    void upsertVariables(UUID id,
                         String groupId,
                         String artifactId,
                         String version,
                         String variables);

    @Transactional
    default void upsertVariables(PluginCoordinates sourcePlugin, List<Variable> variables) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonVariables = objectMapper.writeValueAsString(variables);
            upsertVariables(UUID.randomUUID(), sourcePlugin.getGroupId(), sourcePlugin.getArtifactId(), sourcePlugin.getVersion(), jsonVariables);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting variables to JSON", e);
        }
    }

    @Modifying
    @Transactional
    @Query(value = "UPDATE plugin_variables SET variables = (" +
            "SELECT COALESCE(jsonb_agg(" +
            "  CASE " +
            "    WHEN (elem->>'masked' IS NULL OR elem->>'masked' = 'false') " +
            "    THEN jsonb_set(elem, '{value}', CAST('null' AS jsonb)) " +
            "    ELSE elem " +
            "  END" +
            "), CAST('[]' AS jsonb)) " +
            "FROM jsonb_array_elements(COALESCE(variables, CAST('[]' AS jsonb))) elem" +
            ") " +
            "WHERE variables IS NULL OR " +
            "EXISTS (SELECT 1 FROM jsonb_array_elements(variables) elem " +
            "WHERE elem->>'masked' IS NULL OR elem->>'masked' = 'false')",
            nativeQuery = true)
    void resetAllUnmaskedVariableValues();

    @Modifying
    @Transactional
    @Query("DELETE FROM PluginVariables pv WHERE pv.groupId = :groupId AND pv.artifactId = :artifactId AND pv.version = :version")
    void deleteBySourcePlugin(String groupId, String artifactId, String version);

    @Transactional
    default void deleteBySourcePlugin(PluginCoordinates sourcePlugin) {
        deleteBySourcePlugin(sourcePlugin.getGroupId(), sourcePlugin.getArtifactId(), sourcePlugin.getVersion());
    }
}
