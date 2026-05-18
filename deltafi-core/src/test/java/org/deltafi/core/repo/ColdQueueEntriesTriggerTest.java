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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class ColdQueueEntriesTriggerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("deltafi/timescaledb:2.19.0-pg16").asCompatibleSubstituteFor("postgres"))
            .withCommand("postgres", "-c", "timescaledb.enable_job_execution=off");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;

    private int flowId;

    @BeforeEach
    void resetState() {
        jdbc.execute("DELETE FROM delta_file_flows");
        jdbc.execute("DELETE FROM delta_files");
        jdbc.execute("DELETE FROM flow_definitions WHERE name = 'cold-flow'");
        flowId = jdbc.queryForObject(
                "INSERT INTO flow_definitions (name, type) VALUES ('cold-flow', 'DATA_SINK') RETURNING id",
                Integer.class);
    }

    @Test
    void insertColdQueuedFlowCreatesEntry() {
        UUID flowUuid = insertColdQueuedFlow();

        assertEquals(1L, coldQueueEntryCount());
        assertEquals(flowUuid, jdbc.queryForObject(
                "SELECT delta_file_flow_id FROM cold_queue_entries",
                UUID.class));
        assertEquals("cold-flow", jdbc.queryForObject(
                "SELECT flow_name FROM cold_queue_entries",
                String.class));
        assertEquals("action-1", jdbc.queryForObject(
                "SELECT action_name FROM cold_queue_entries",
                String.class));
        assertEquals("com.example.Action", jdbc.queryForObject(
                "SELECT action_class FROM cold_queue_entries",
                String.class));
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT rate_limited FROM cold_queue_entries",
                Boolean.class)));
        assertNotNull(jdbc.queryForObject(
                "SELECT queued_at FROM cold_queue_entries",
                OffsetDateTime.class));
    }

    @Test
    void updateToNonTrackingColumnDoesNotChangeColdQueueEntries() {
        UUID flowUuid = insertColdQueuedFlow();
        OffsetDateTime queuedAt = jdbc.queryForObject(
                "SELECT queued_at FROM cold_queue_entries WHERE delta_file_flow_id = ?",
                OffsetDateTime.class,
                flowUuid);
        assertNotNull(queuedAt);

        jdbc.update("UPDATE delta_file_flows SET modified = modified + INTERVAL '1 second' WHERE id = ?", flowUuid);

        assertEquals(1L, coldQueueEntryCount());
        assertEquals(queuedAt, jdbc.queryForObject(
                "SELECT queued_at FROM cold_queue_entries WHERE delta_file_flow_id = ?",
                OffsetDateTime.class,
                flowUuid));
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT rate_limited FROM cold_queue_entries WHERE delta_file_flow_id = ?",
                Boolean.class,
                flowUuid)));
    }

    @Test
    void clearingColdQueuedActionRemovesEntry() {
        UUID flowUuid = insertColdQueuedFlow();

        jdbc.update("UPDATE delta_file_flows SET cold_queued_action = NULL WHERE id = ?", flowUuid);

        assertEquals(0L, coldQueueEntryCount());
    }

    @Test
    void deletingNonColdQueuedFlowDoesNotCreateEntries() {
        UUID deltaFileId = insertDeltaFile();
        UUID flowUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO delta_file_flows (id, delta_file_id, depth, number, version, test_mode,
                        flow_definition_id, state, cold_queued_action, actions, created, modified)
                VALUES (?, ?, 0, 0, 0, false, ?, CAST('IN_FLIGHT' AS dff_state_enum), NULL,
                        '[{"n":"action-1"}]'::jsonb, NOW(), NOW())
                """, flowUuid, deltaFileId, flowId);

        jdbc.update("DELETE FROM delta_file_flows WHERE id = ?", flowUuid);

        assertEquals(0L, coldQueueEntryCount());
        assertFalse(existsInColdQueue(flowUuid));
    }

    private UUID insertColdQueuedFlow() {
        UUID deltaFileId = insertDeltaFile();
        UUID flowUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO delta_file_flows (id, delta_file_id, depth, number, version, test_mode,
                        flow_definition_id, state, cold_queued_action, actions, created, modified)
                VALUES (?, ?, 0, 0, 0, false, ?, CAST('IN_FLIGHT' AS dff_state_enum), ?, ?::jsonb, NOW(), NOW())
                """, flowUuid, deltaFileId, flowId, "com.example.Action",
                "[{\"n\":\"action-1\",\"q\":\"2026-04-23T10:15:30Z\",\"rl\":true}]");
        return flowUuid;
    }

    private UUID insertDeltaFile() {
        UUID did = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO delta_files (did, requeue_count, ingress_bytes, referenced_bytes, total_bytes, version,
                        content_deletable, terminal, stage, data_source_id, created, modified)
                VALUES (?, 0, 0, 0, 0, 0, false, false, CAST('ERROR' AS df_stage_enum), ?, NOW(), NOW())
                """, did, flowId);
        return did;
    }

    private long coldQueueEntryCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM cold_queue_entries", Long.class);
        return count == null ? 0L : count;
    }

    private boolean existsInColdQueue(UUID flowUuid) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM cold_queue_entries WHERE delta_file_flow_id = ?)",
                Boolean.class,
                flowUuid);
        return Boolean.TRUE.equals(exists);
    }
}
