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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.deltafi.core.types.RestDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class RestDataSourceRepoImpl implements RestDataSourceRepoCustom {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RestDataSourceRepoImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void batchInsert(List<RestDataSource> restDataSources) {
        String sql = """
            INSERT INTO flows (name, type, description, source_plugin, flow_status, variables,
                               topic, discriminator, id, max_errors,
                               metadata, annotation_config, tags, rate_limit)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?,
                    ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
        """;

        jdbcTemplate.batchUpdate(sql, restDataSources, 1000, this::setupPreparedStatement);
    }

    @Override
    @Transactional
    public void upsertFlowFromSnapshot(RestDataSource flow) {
        String sql = """
            INSERT INTO flows (name, type, description, source_plugin, flow_status, variables,
                               topic, discriminator, id, max_errors,
                               metadata, annotation_config, tags, rate_limit)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?,
                    ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
            ON CONFLICT (name, type) DO UPDATE SET
                max_errors = EXCLUDED.max_errors,
                rate_limit = EXCLUDED.rate_limit,
               \s""" + FlowRepo.FLOW_STATE_UPSERT_FROM_SNAPSHOT;

        jdbcTemplate.update(sql, ps -> setupPreparedStatement(ps, flow));
    }

    @Override
    @Transactional
    public void upsertFlowFromPlugin(RestDataSource flow) {
        String sql = """
            INSERT INTO flows (name, type, description, source_plugin, flow_status, variables,
                               topic, discriminator, id, max_errors,
                               metadata, annotation_config, tags, rate_limit)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?,
                    ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
            ON CONFLICT (name, type) DO UPDATE SET
                description = EXCLUDED.description,
                source_plugin = EXCLUDED.source_plugin,
                variables = EXCLUDED.variables,
                topic = EXCLUDED.topic,
                max_errors = flows.max_errors,
                metadata = EXCLUDED.metadata,
                annotation_config = EXCLUDED.annotation_config,
                tags = EXCLUDED.tags,
                rate_limit = flows.rate_limit,
               \s""" + FlowRepo.FLOW_STATE_UPSERT_FROM_PLUGIN;

        jdbcTemplate.update(sql, ps -> setupPreparedStatement(ps, flow));
    }

    private void setupPreparedStatement(PreparedStatement ps, RestDataSource restDataSource) throws SQLException {
        ps.setString(1, restDataSource.getName());
        ps.setString(2, restDataSource.getType().name());
        ps.setString(3, restDataSource.getDescription());
        ps.setString(4, toJson(restDataSource.getSourcePlugin()));
        ps.setString(5, toJson(restDataSource.getFlowStatus()));
        ps.setString(6, toJson(restDataSource.getVariables()));
        ps.setString(7, restDataSource.getTopic());
        ps.setString(8, "REST_DATA_SOURCE");
        ps.setObject(9, restDataSource.getId());
        ps.setInt(10, restDataSource.getMaxErrors());
        ps.setString(11, toJson(restDataSource.getMetadata()));
        ps.setString(12, toJson(restDataSource.getAnnotationConfig()));
        ps.setString(13, toJson(restDataSource.getTags()));
        ps.setString(14, toJson(restDataSource.getRateLimit()));
    }

    private String toJson(Object object) {
        try {
            return object == null ? null : objectMapper.writeValueAsString(object);
        } catch (JacksonException e) {
            throw new RuntimeException("Error converting object to JSON", e);
        }
    }
}
