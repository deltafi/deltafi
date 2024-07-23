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
import org.deltafi.core.types.TimedDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
                               ingress_status_message, discriminator, id)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        jdbcTemplate.batchUpdate(sql, timedDataSources, 1000, (ps, timedDataSource) -> {
            ps.setString(1, timedDataSource.getName());
            ps.setString(2, timedDataSource.getType().name());
            ps.setString(3, timedDataSource.getDescription());
            ps.setString(4, toJson(timedDataSource.getSourcePlugin()));
            ps.setString(5, toJson(timedDataSource.getFlowStatus()));
            ps.setString(6, toJson(timedDataSource.getVariables()));
            ps.setString(7, timedDataSource.getTopic());
            ps.setString(8, toJson(timedDataSource.getTimedIngressAction()));
            ps.setString(9, timedDataSource.getCronSchedule());
            ps.setObject(10, timedDataSource.getLastRun());
            ps.setObject(11, timedDataSource.getNextRun());
            ps.setString(12, timedDataSource.getMemo());
            ps.setObject(13, timedDataSource.getCurrentDid());
            ps.setBoolean(14, timedDataSource.isExecuteImmediate());
            ps.setString(15, timedDataSource.getIngressStatus().name());
            ps.setString(16, timedDataSource.getIngressStatusMessage());
            ps.setString(17, "TIMED_DATA_SOURCE");
            ps.setObject(18, timedDataSource.getId());
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
