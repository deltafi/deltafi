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

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PendingDeleteRepo {
    private final JdbcTemplate jdbcTemplate;

    public void insertPendingDeletes(List<String> nodes, List<UUID> dids, String bucket) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO pending_deletes (node, did, bucket) VALUES (?, ?, ?)",
                nodes.stream()
                        .flatMap(node -> dids.stream().map(did -> new Object[]{node, did, bucket}))
                        .toList(),
                100,
                (ps, args) -> {
                    ps.setString(1, (String) args[0]);
                    ps.setObject(2, args[1]);
                    ps.setString(3, (String) args[2]);
                }
        );
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pending_deletes", Integer.class);
        return count != null ? count : 0;
    }

    public void deleteNodesNotIn(List<String> activeNodes) {
        String inClause = String.join(",", activeNodes.stream().map(n -> "'" + n + "'").toList());
        String sql = "DELETE FROM pending_deletes WHERE node NOT IN (" + inClause + ")";
        jdbcTemplate.update(sql);
    }

    public Map<String, Integer> countOldEntriesPerNode(Duration ageThreshold) {
        return jdbcTemplate.query(
                "SELECT node, COUNT(*) FROM pending_deletes WHERE added_at < now() - (? || ' seconds')::interval GROUP BY node",
                ps -> ps.setLong(1, ageThreshold.getSeconds()),
                rs -> {
                    Map<String, Integer> result = new HashMap<>();
                    while (rs.next()) {
                        result.put(rs.getString("node"), rs.getInt("count"));
                    }
                    return result;
                }
        );
    }
}
