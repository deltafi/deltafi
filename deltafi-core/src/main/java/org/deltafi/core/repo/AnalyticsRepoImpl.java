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

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.types.AnalyticsEntity;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AnalyticsRepoImpl implements AnalyticsRepoCustom {
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void batchInsert(List<AnalyticsEntity> entities) {
        if (entities.isEmpty()) return;

        jdbcTemplate.batchUpdate(
                "INSERT INTO analytics (event_time, did, flow_id, data_source_id, event_group_id, action_id, cause_id, event_type, bytes_count, file_count, survey, updated) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?::event_type_enum, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {
                        AnalyticsEntity entity = entities.get(i);
                        ps.setObject(1, entity.getId().getEventTime());
                        ps.setObject(2, entity.getId().getDid());
                        ps.setObject(3, entity.getFlowId());
                        ps.setInt(4, entity.getDataSourceId());
                        ps.setObject(5, entity.getEventGroupId());
                        ps.setObject(6, entity.getActionId());
                        ps.setObject(7, entity.getCauseId());
                        ps.setString(8, entity.getEventType().name());
                        ps.setLong(9, entity.getBytesCount());
                        ps.setInt(10, entity.getFileCount());
                        ps.setBoolean(11, entity.isSurvey());
                        ps.setObject(12, entity.getUpdated());
                    }

                    @Override
                    public int getBatchSize() {
                        return entities.size();
                    }
                }
        );
    }

    @Override
    public void batchUpdateEventGroupIdAndUpdated(List<Object[]> updatesWithGroup) {
        if (updatesWithGroup.isEmpty()) return;
        jdbcTemplate.batchUpdate("UPDATE analytics SET event_group_id = ?, updated = ? WHERE did = ?", updatesWithGroup);
    }
    @Override
    public void batchUpdateUpdated(List<Object[]> updatesWithoutGroup) {
        if (updatesWithoutGroup.isEmpty()) return;
        jdbcTemplate.batchUpdate("UPDATE analytics SET updated = ? WHERE did = ?", updatesWithoutGroup);
    }
}
