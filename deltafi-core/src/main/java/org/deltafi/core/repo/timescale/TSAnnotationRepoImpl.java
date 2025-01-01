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
package org.deltafi.core.repo.timescale;

import lombok.RequiredArgsConstructor;
import org.deltafi.core.types.timescale.TSAnnotation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TSAnnotationRepoImpl implements TSAnnotationRepoCustom {
    private static final String UPSERT_ANNOTATION = """
        INSERT INTO ts_annotations (entity_timestamp, entity_id, data_source, key, value)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (entity_timestamp, entity_id, key) DO UPDATE SET
        value = EXCLUDED.value""";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void batchUpsert(List<TSAnnotation> annotations) {
        if (annotations.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(UPSERT_ANNOTATION, annotations, annotations.size(),
                (PreparedStatement ps, TSAnnotation annotation) -> {
                    ps.setObject(1, annotation.getEntityTimestamp());
                    ps.setObject(2, annotation.getId().getEntityId());
                    ps.setString(3, annotation.getDataSource());
                    ps.setString(4, annotation.getId().getKey());
                    ps.setString(5, annotation.getValue());
                });
    }
}
