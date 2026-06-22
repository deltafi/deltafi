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
import org.deltafi.core.types.DataSink;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class DataSinkRepoImpl implements DataSinkRepoCustom {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DataSinkRepoImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void batchInsert(List<DataSink> dataSinks) {
        String sql = """
            INSERT INTO flows (name, type, description, source_plugin, flow_status, variables,
                               egress_action, expected_annotations, subscribe, discriminator, id, tags)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb)
        """;

        jdbcTemplate.batchUpdate(sql, dataSinks, 1000, (ps, dataSink) -> setupPreparedStatement(dataSink, ps));
    }

    @Override
    @Transactional
    public void upsertFlowFromSnapshot(DataSink flow) {
        String sql = """
            INSERT INTO flows (name, type, description, source_plugin, flow_status, variables,
                               egress_action, expected_annotations, subscribe, discriminator, id, tags)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb)
            ON CONFLICT (name, type) DO UPDATE SET
                expected_annotations = EXCLUDED.expected_annotations,
               \s""" + FlowRepo.FLOW_STATE_UPSERT_FROM_SNAPSHOT;

        jdbcTemplate.update(sql, ps -> setupPreparedStatement(flow, ps));
    }

    @Override
    @Transactional
    public void upsertFlowFromPlugin(DataSink flow) {
        String sql = """
            INSERT INTO flows (name, type, description, source_plugin, flow_status, variables,
                               egress_action, expected_annotations, subscribe, discriminator, id, tags)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb)
            ON CONFLICT (name, type) DO UPDATE SET
                description = EXCLUDED.description,
                source_plugin = EXCLUDED.source_plugin,
                variables = EXCLUDED.variables,
                egress_action = EXCLUDED.egress_action,
                expected_annotations = EXCLUDED.expected_annotations,
                subscribe = EXCLUDED.subscribe,
                tags = EXCLUDED.tags,
               \s""" + FlowRepo.FLOW_STATE_UPSERT_FROM_PLUGIN;

        jdbcTemplate.update(sql, ps -> setupPreparedStatement(flow, ps));
    }

    private String toJson(Object object) {
        try {
            return object == null ? null : objectMapper.writeValueAsString(object);
        } catch (JacksonException e) {
            throw new RuntimeException("Error converting object to JSON", e);
        }
    }

    private void setupPreparedStatement(DataSink flow, PreparedStatement ps) throws SQLException {
        ps.setString(1, flow.getName());
        ps.setString(2, flow.getType().name());
        ps.setString(3, flow.getDescription());
        ps.setString(4, toJson(flow.getSourcePlugin()));
        ps.setString(5, toJson(flow.getFlowStatus()));
        ps.setString(6, toJson(flow.getVariables()));
        ps.setString(7, toJson(flow.getEgressAction()));
        ps.setString(8, toJson(flow.getExpectedAnnotations()));
        ps.setString(9, toJson(flow.getSubscribe()));
        ps.setString(10, "DATA_SINK");
        ps.setObject(11, flow.getId());
        ps.setString(12, toJson(flow.getTags()));
    }
}
