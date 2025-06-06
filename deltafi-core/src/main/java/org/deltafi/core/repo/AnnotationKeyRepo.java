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

import org.deltafi.core.types.AnnotationKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface AnnotationKeyRepo extends JpaRepository<AnnotationKey, Integer> {
    Optional<AnnotationKey> findByKeyName(String keyName);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM annotation_keys ak WHERE NOT EXISTS (" +
            "SELECT 1 annotation_key_id FROM analytics_5m_anno a5 WHERE ak.id = a5.annotation_key_id" +
            ") AND created < NOW() - INTERVAL '30 days'", nativeQuery = true)
    void deleteUnusedAnnotationKeys();
}
