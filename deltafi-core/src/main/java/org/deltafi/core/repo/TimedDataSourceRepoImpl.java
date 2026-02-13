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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.deltafi.core.types.TimedDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public class TimedDataSourceRepoImpl implements TimedDataSourceRepoCustom {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TimedDataSourceRepoImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void batchInsert(List<TimedDataSource> timedDataSources) {
        String sql = """
            INSERT INTO flows (name, type, description, source_plugin, flow_status, variables,
                               topic, timed_ingress_action, cron_schedule, last_run, next_run,
                               memo, current_did, execute_immediate, ingress_status,
                               ingress_status_message, discriminator, id, max_errors,
                               metadata, annotation_config, tags)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
        """;

        jdbcTemplate.batchUpdate(sql, timedDataSources, 1000, (ps, flow) -> setupPreparedStatement(flow, ps));
    }

    @Override
    @Transactional
    public void upsertFlowFromSnapshot(TimedDataSource flow) {
        String sql = """
            INSERT INTO flows (name, type, description, source_plugin, flow_status, variables,
                               topic, timed_ingress_action, cron_schedule, last_run, next_run,
                               memo, current_did, execute_immediate, ingress_status,
                               ingress_status_message, discriminator, id, max_errors,
                               metadata, annotation_config, tags)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
            ON CONFLICT (name, type) DO UPDATE SET
                max_errors = EXCLUDED.max_errors,
               \s""" + FlowRepo.FLOW_STATE_UPSERT_FROM_SNAPSHOT;

        jdbcTemplate.update(sql, ps -> setupPreparedStatement(flow, ps));
    }

    @Override
    @Transactional
    public void upsertFlowFromPlugin(TimedDataSource flow) {
        String sql = """
            INSERT INTO flows (name, type, description, source_plugin, flow_status, variables,
                               topic, timed_ingress_action, cron_schedule, last_run, next_run,
                               memo, current_did, execute_immediate, ingress_status,
                               ingress_status_message, discriminator, id, max_errors,
                               metadata, annotation_config, tags)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
            ON CONFLICT (name, type) DO UPDATE SET
                description = EXCLUDED.description,
                source_plugin = EXCLUDED.source_plugin,
                variables = EXCLUDED.variables,
                topic = EXCLUDED.topic,
                timed_ingress_action = EXCLUDED.timed_ingress_action,
                cron_schedule = EXCLUDED.cron_schedule,
                max_errors = flows.max_errors,
                metadata = EXCLUDED.metadata,
                annotation_config = EXCLUDED.annotation_config,
                tags = EXCLUDED.tags,
               \s""" + FlowRepo.FLOW_STATE_UPSERT_FROM_PLUGIN;

        jdbcTemplate.update(sql, ps -> setupPreparedStatement(flow, ps));
    }

    private void setupPreparedStatement(TimedDataSource flow, PreparedStatement ps) throws SQLException {
        ps.setString(1, flow.getName());
        ps.setString(2, flow.getType().name());
        ps.setString(3, flow.getDescription());
        ps.setString(4, toJson(flow.getSourcePlugin()));
        ps.setString(5, toJson(flow.getFlowStatus()));
        ps.setString(6, toJson(flow.getVariables()));
        ps.setString(7, flow.getTopic());
        ps.setString(8, toJson(flow.getTimedIngressAction()));
        ps.setString(9, flow.getCronSchedule());
        ps.setObject(10, flow.getLastRun());
        ps.setObject(11, flow.getNextRun());
        ps.setString(12, flow.getMemo());
        ps.setObject(13, flow.getCurrentDid());
        ps.setBoolean(14, flow.isExecuteImmediate());
        ps.setString(15, flow.getIngressStatus() != null ? flow.getIngressStatus().name() : null);
        ps.setString(16, flow.getIngressStatusMessage());
        ps.setString(17, "TIMED_DATA_SOURCE");
        ps.setObject(18, flow.getId());
        ps.setInt(19, flow.getMaxErrors());
        ps.setString(20, toJson(flow.getMetadata()));
        ps.setString(21, toJson(flow.getAnnotationConfig()));
        ps.setString(22, toJson(flow.getTags()));
    }

    private String toJson(Object object) {
        try {
            return object == null ? null : objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting object to JSON", e);
        }
    }
}
