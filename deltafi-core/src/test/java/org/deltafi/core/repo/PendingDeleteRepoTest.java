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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class PendingDeleteRepoTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("deltafi/timescaledb:2.19.0-pg16").asCompatibleSubstituteFor("postgres"))
            .withCommand("postgres",
                    "-c", "timescaledb.enable_job_execution=off",
                    "-c", "timescaledb.max_background_workers=0");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;

    private PendingDeleteRepo repo;

    @BeforeEach
    void resetState() {
        jdbc.execute("DELETE FROM pending_deletes");
        repo = new PendingDeleteRepo(jdbc);
    }

    @Test
    void rejectsDuplicateNodeAndDid() {
        UUID did = UUID.randomUUID();
        repo.insertPendingDeletes(List.of("node-a"), List.of(did), "bucket");
        repo.insertPendingDeletes(List.of("node-a"), List.of(did), "bucket");

        assertEquals(1, rowCountFor("node-a", did));
        assertEquals(1, repo.count());
    }

    @Test
    void allowsSameDidAcrossDifferentNodes() {
        UUID did = UUID.randomUUID();
        repo.insertPendingDeletes(List.of("node-a", "node-b"), List.of(did), "bucket");

        assertEquals(1, rowCountFor("node-a", did));
        assertEquals(1, rowCountFor("node-b", did));
        assertEquals(2, repo.count());
    }

    @Test
    void preservesOriginalAddedAtWhenReinserted() {
        UUID did = UUID.randomUUID();
        repo.insertPendingDeletes(List.of("node-a"), List.of(did), "bucket");
        Timestamp original = addedAtFor("node-a", did);
        assertNotNull(original);

        // Backdate so a refreshed added_at would be observably different.
        jdbc.update("UPDATE pending_deletes SET added_at = added_at - INTERVAL '1 hour' WHERE node = ? AND did = ?",
                "node-a", did);
        Timestamp backdated = addedAtFor("node-a", did);

        repo.insertPendingDeletes(List.of("node-a"), List.of(did), "bucket");

        assertEquals(backdated, addedAtFor("node-a", did));
    }

    @Test
    void collapsesDuplicatesWithinBatch() {
        UUID did = UUID.randomUUID();
        repo.insertPendingDeletes(List.of("node-a", "node-a"), List.of(did, did), "bucket");

        assertEquals(1, rowCountFor("node-a", did));
        assertEquals(1, repo.count());
    }

    private int rowCountFor(String node, UUID did) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pending_deletes WHERE node = ? AND did = ?",
                Integer.class, node, did);
        return count == null ? 0 : count;
    }

    private Timestamp addedAtFor(String node, UUID did) {
        return jdbc.queryForObject(
                "SELECT added_at FROM pending_deletes WHERE node = ? AND did = ?",
                Timestamp.class, node, did);
    }
}
