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
package org.deltafi.core.repo.timescale;

import lombok.RequiredArgsConstructor;
import org.deltafi.core.types.timescale.TSIngress;
import org.json.JSONObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TSIngressRepoImpl implements TSIngressRepoCustom {
    private static final String UPSERT_TS_INGRESS = """
        INSERT INTO ts_ingresses (id, timestamp, data_source, annotations, ingress_bytes, count, survey)
        VALUES (?, ?, ?, ?::jsonb, ?, ?, ?)
        ON CONFLICT (id, timestamp, data_source) DO UPDATE SET
        annotations = ts_ingresses.annotations || EXCLUDED.annotations""";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void batchUpsert(List<TSIngress> ingressEvents) {
        if (ingressEvents.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(UPSERT_TS_INGRESS, ingressEvents, ingressEvents.size(),
                (PreparedStatement ps, TSIngress event) -> {
                    ps.setObject(1, event.getKey().getId());
                    ps.setObject(2, event.getKey().getTimestamp());
                    ps.setString(3, event.getKey().getDataSource());
                    ps.setString(4, new JSONObject(event.getAnnotations()).toString());
                    ps.setLong(5, event.getIngressBytes());
                    ps.setInt(6, event.getCount());
                    ps.setBoolean(7, event.isSurvey());
                });
    }
}
