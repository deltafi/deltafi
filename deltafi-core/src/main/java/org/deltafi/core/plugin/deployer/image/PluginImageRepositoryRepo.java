/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.plugin.deployer.image;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PluginImageRepositoryRepo extends JpaRepository<PluginImageRepository, String> {

    @Query(value = "SELECT * FROM plugin_image_repository WHERE :groupId IN (SELECT jsonb_array_elements_text(plugin_group_ids))", nativeQuery = true)
    Optional<PluginImageRepository> findByPluginGroupId(String groupId);

    @Query(value = """
           SELECT EXISTS(
               SELECT 1 
               FROM plugin_image_repository, jsonb_array_elements_text(plugin_group_ids) AS group_id
               WHERE image_repository_base != :base 
               AND group_id = ANY(string_to_array(:groupIds, ','))
           )
           """, nativeQuery = true)
    boolean otherExistsByAnyGroupId(String base, String groupIds);
}
