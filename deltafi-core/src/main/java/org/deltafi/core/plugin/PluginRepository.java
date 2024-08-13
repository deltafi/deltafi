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
package org.deltafi.core.plugin;

import org.deltafi.common.types.PluginCoordinates;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface PluginRepository extends JpaRepository<PluginEntity, PluginCoordinates> {
    Optional<PluginEntity> findByPluginCoordinatesGroupIdAndPluginCoordinatesArtifactId(String groupId, String artifactId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM plugins p " +
            "WHERE p.group_id = :groupId " +
            "AND p.artifact_id = :artifactId", nativeQuery = true)
    void deleteByGroupIdAndArtifactId(String groupId, String artifactId);

    @Query(value = "SELECT * FROM plugins WHERE EXISTS (" +
            "SELECT 1 FROM jsonb_array_elements(dependencies) AS dep " +
            "WHERE dep->>'groupId' = :#{#coords.groupId} " +
            "AND dep->>'artifactId' = :#{#coords.artifactId} " +
            "AND dep->>'version' = :#{#coords.version}" +
            ")", nativeQuery = true)
    List<PluginEntity> findPluginsWithDependency(PluginCoordinates coords);
}
