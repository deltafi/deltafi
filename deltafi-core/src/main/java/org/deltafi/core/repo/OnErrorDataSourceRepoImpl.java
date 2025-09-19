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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.deltafi.core.types.OnErrorDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class OnErrorDataSourceRepoImpl implements OnErrorDataSourceRepoCustom {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OnErrorDataSourceRepoImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void batchInsert(List<OnErrorDataSource> onErrorDataSources) {
        String sql = """
            INSERT INTO flows (name, type, description, source_plugin, flow_status, variables,
                               topic, discriminator, id, max_errors,
                               metadata, annotation_config, error_message_regex, source_filters,
                               metadata_filters, annotation_filters, include_source_metadata_regex,
                               source_metadata_prefix, include_source_annotations_regex)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?,
                    ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb)
        """;

        jdbcTemplate.batchUpdate(sql, onErrorDataSources, 1000, (ps, onErrorDataSource) -> {
            ps.setString(1, onErrorDataSource.getName());
            ps.setString(2, onErrorDataSource.getType().name());
            ps.setString(3, onErrorDataSource.getDescription());
            ps.setString(4, toJson(onErrorDataSource.getSourcePlugin()));
            ps.setString(5, toJson(onErrorDataSource.getFlowStatus()));
            ps.setString(6, toJson(onErrorDataSource.getVariables()));
            ps.setString(7, onErrorDataSource.getTopic());
            ps.setString(8, "ON_ERROR_DATA_SOURCE");
            ps.setObject(9, onErrorDataSource.getId());
            ps.setInt(10, onErrorDataSource.getMaxErrors());
            ps.setString(11, toJson(onErrorDataSource.getMetadata()));
            ps.setString(12, toJson(onErrorDataSource.getAnnotationConfig()));
            ps.setString(13, onErrorDataSource.getErrorMessageRegex());
            ps.setString(14, toJson(onErrorDataSource.getSourceFilters()));
            ps.setString(15, toJson(onErrorDataSource.getMetadataFilters()));
            ps.setString(16, toJson(onErrorDataSource.getAnnotationFilters()));
            ps.setString(17, toJson(onErrorDataSource.getIncludeSourceMetadataRegex()));
            ps.setString(18, onErrorDataSource.getSourceMetadataPrefix());
            ps.setString(19, toJson(onErrorDataSource.getIncludeSourceAnnotationsRegex()));
        });
    }

    private String toJson(Object object) {
        try {
            return object == null ? null : objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting object to JSON", e);
        }
    }
}
