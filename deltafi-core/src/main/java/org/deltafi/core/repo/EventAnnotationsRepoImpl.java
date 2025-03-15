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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class EventAnnotationsRepoImpl implements EventAnnotationsRepoCustom {
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void bulkUpsertAnnotations(List<Object[]> batchParams) {
        if (batchParams.isEmpty()) return;

        jdbcTemplate.batchUpdate(
                "INSERT INTO event_annotations (did, annotation_key_id, annotation_value_id) " +
                        "VALUES (?, ?, ?) " +
                        "ON CONFLICT (did, annotation_key_id) DO UPDATE " +
                        "SET annotation_value_id = EXCLUDED.annotation_value_id",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {
                        Object[] params = batchParams.get(i);
                        ps.setObject(1, params[0]); // did
                        ps.setInt(2, (Integer) params[1]); // keyId
                        ps.setInt(3, (Integer) params[2]); // valueId
                    }

                    @Override
                    public int getBatchSize() {
                        return batchParams.size();
                    }
                }
        );
    }
}
