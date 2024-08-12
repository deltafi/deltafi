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
import org.deltafi.core.types.RestDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
                               topic, discriminator, id, max_errors)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
        """;

        jdbcTemplate.batchUpdate(sql, restDataSources, 1000, (ps, restDataSource) -> {
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
