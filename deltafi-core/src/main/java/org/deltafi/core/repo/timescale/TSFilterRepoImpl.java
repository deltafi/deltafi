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
package org.deltafi.core.repo.timescale;

import lombok.RequiredArgsConstructor;
import org.deltafi.core.types.timescale.TSFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class TSFilterRepoImpl implements TSFilterRepoCustom {
    private static final String INSERT_TS_FILTER = """
        INSERT INTO ts_filters (id, timestamp, data_source, message, flow, action)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (timestamp, id) DO NOTHING""";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void batchInsert(List<TSFilter> filterEvents) {
        if (filterEvents.isEmpty()) {
            return;
        }

        List<Object[]> batchArgs = filterEvents.stream()
                .map(event -> new Object[]{
                        event.getKey().getId(),
                        event.getKey().getTimestamp(),
                        event.getKey().getDataSource(),
                        event.getMessage(),
                        event.getFlow(),
                        event.getAction()
                })
                .toList();

        jdbcTemplate.batchUpdate(INSERT_TS_FILTER, batchArgs);
    }
}
