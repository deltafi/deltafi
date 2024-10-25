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
package org.deltafi.core.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.deltafi.core.types.DataSink;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
                               egress_action, expected_annotations, subscribe, discriminator, id)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
        """;

        jdbcTemplate.batchUpdate(sql, dataSinks, 1000, (ps, dataSink) -> {
            ps.setString(1, dataSink.getName());
            ps.setString(2, dataSink.getType().name());
            ps.setString(3, dataSink.getDescription());
            ps.setString(4, toJson(dataSink.getSourcePlugin()));
            ps.setString(5, toJson(dataSink.getFlowStatus()));
            ps.setString(6, toJson(dataSink.getVariables()));
            ps.setString(7, toJson(dataSink.getEgressAction()));
            ps.setString(8, toJson(dataSink.getExpectedAnnotations()));
            ps.setString(9, toJson(dataSink.getSubscribe()));
            ps.setString(10, "DATA_SINK");
            ps.setObject(11, dataSink.getId());
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
