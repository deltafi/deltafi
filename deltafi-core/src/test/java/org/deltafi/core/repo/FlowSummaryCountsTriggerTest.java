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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Repo-level tests for the flow_summary_counts triggers. Uses {@link JdbcTest} so Flyway runs against
 * a Testcontainers postgres but no application beans are loaded — lightweight compared to the full
 * {@code @SpringBootTest} harness used for service/datafetcher tests.
 *
 * <p>Each test seeds minimal rows via raw SQL so the trigger is the sole unit under test (no JPA or
 * repo-layer intermediation). The {@code @BeforeEach} clears the relevant tables so state doesn't
 * leak between tests.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class FlowSummaryCountsTriggerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("deltafi/timescaledb:2.19.0-pg16").asCompatibleSubstituteFor("postgres"))
            .withCommand("postgres", "-c", "timescaledb.enable_job_execution=off");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // test/resources/application.yaml disables Flyway globally for the full-context tests;
        // this slice needs migrations to set up the triggers under test.
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;

    private int flowId;

    @BeforeEach
    void resetState() {
        // Order matters for FKs; delta_file_flows first, then delta_files, then re-create one flow def.
        jdbc.execute("DELETE FROM delta_file_flows");
        jdbc.execute("DELETE FROM delta_files");
        jdbc.execute("DELETE FROM flow_summary_deltas");
        jdbc.execute("DELETE FROM flow_summary_counts");
        jdbc.execute("DELETE FROM flow_definitions WHERE name = 'flow1'");
        flowId = jdbc.queryForObject(
                "INSERT INTO flow_definitions (name, type) VALUES ('flow1', 'TRANSFORM') RETURNING id",
                Integer.class);
    }

    private void drainDeltas() {
        jdbc.execute("SELECT consume_flow_summary_deltas()");
    }

    @Test
    void insertErrorFlowCreatesSummaryRow() {
        UUID did = insertDeltaFile();
        insertFlow(did, "ERROR", null, "causeA");

        assertCount("ERROR", false, "causeA", 1);
    }

    @Test
    void insertNonTrackedStateCreatesNoSummary() {
        UUID did = insertDeltaFile();
        insertFlow(did, "COMPLETE", null, null);

        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM flow_summary_counts", Long.class);
        assertEquals(0L, count);
    }

    @Test
    void acknowledgingErrorMovesCountFromUnackToAck() {
        UUID did = insertDeltaFile();
        UUID flowUuid = insertFlow(did, "ERROR", null, "causeA");

        jdbc.update("UPDATE delta_file_flows SET error_acknowledged = NOW() WHERE id = ?", flowUuid);

        assertCount("ERROR", false, "causeA", 0);
        assertCount("ERROR", true, "causeA", 1);
    }

    @Test
    void unacknowledgingMovesCountBack() {
        UUID did = insertDeltaFile();
        UUID flowUuid = insertFlow(did, "ERROR", null, "causeA");
        jdbc.update("UPDATE delta_file_flows SET error_acknowledged = NOW() WHERE id = ?", flowUuid);

        jdbc.update("UPDATE delta_file_flows SET error_acknowledged = NULL WHERE id = ?", flowUuid);

        assertCount("ERROR", false, "causeA", 1);
        assertCount("ERROR", true, "causeA", 0);
    }

    @Test
    void causeChangeMovesCountToNewGroup() {
        UUID did = insertDeltaFile();
        UUID flowUuid = insertFlow(did, "ERROR", null, "causeA");

        jdbc.update("UPDATE delta_file_flows SET error_or_filter_cause = 'causeB' WHERE id = ?", flowUuid);

        assertCount("ERROR", false, "causeA", 0);
        assertCount("ERROR", false, "causeB", 1);
    }

    @Test
    void stateTransitionOutOfErrorDecrements() {
        UUID did = insertDeltaFile();
        UUID flowUuid = insertFlow(did, "ERROR", null, "causeA");

        jdbc.update("UPDATE delta_file_flows SET state = 'COMPLETE' WHERE id = ?", flowUuid);

        assertCount("ERROR", false, "causeA", 0);
    }

    @Test
    void updateToNonTrackingColumnDoesNotChangeSummary() {
        UUID did = insertDeltaFile();
        UUID flowUuid = insertFlow(did, "ERROR", null, "causeA");

        // WHEN clause should skip the trigger entirely for modified-only updates.
        jdbc.update("UPDATE delta_file_flows SET modified = modified + INTERVAL '1 second' WHERE id = ?", flowUuid);

        assertCount("ERROR", false, "causeA", 1);
    }

    @Test
    void deleteTrackedFlowDecrements() {
        UUID did = insertDeltaFile();
        UUID flowUuid = insertFlow(did, "ERROR", null, "causeA");

        jdbc.update("DELETE FROM delta_file_flows WHERE id = ?", flowUuid);

        assertCount("ERROR", false, "causeA", 0);
    }

    @Test
    void nullCauseIsTrackedAsDistinctGroup() {
        UUID did1 = insertDeltaFile();
        UUID did2 = insertDeltaFile();
        insertFlow(did1, "ERROR", null, null);
        insertFlow(did2, "ERROR", null, "causeA");

        assertCount("ERROR", false, null, 1);
        assertCount("ERROR", false, "causeA", 1);
    }

    // Cross-drain tests: exercise the case where a summary row already exists in
    // flow_summary_counts (because a prior drain materialized it) and a subsequent delta is
    // negative. The consumer must decrement the existing row; a naive GREATEST(net, 0) at the
    // INSERT site would discard the negative and the summary would stay stale.

    @Test
    void acknowledgeDecrementsAcrossDrain() {
        UUID did = insertDeltaFile();
        UUID flowUuid = insertFlow(did, "ERROR", null, "causeA");
        drainDeltas();
        assertSummaryRow("ERROR", false, "causeA", 1);

        jdbc.update("UPDATE delta_file_flows SET error_acknowledged = NOW() WHERE id = ?", flowUuid);

        assertCount("ERROR", false, "causeA", 0);
        assertCount("ERROR", true, "causeA", 1);
    }

    @Test
    void stateExitFromErrorDecrementsAcrossDrain() {
        UUID did = insertDeltaFile();
        UUID flowUuid = insertFlow(did, "ERROR", null, "causeA");
        drainDeltas();
        assertSummaryRow("ERROR", false, "causeA", 1);

        jdbc.update("UPDATE delta_file_flows SET state = 'COMPLETE' WHERE id = ?", flowUuid);

        assertCount("ERROR", false, "causeA", 0);
    }

    @Test
    void deleteDecrementsAcrossDrain() {
        UUID did = insertDeltaFile();
        UUID flowUuid = insertFlow(did, "ERROR", null, "causeA");
        drainDeltas();
        assertSummaryRow("ERROR", false, "causeA", 1);

        jdbc.update("DELETE FROM delta_file_flows WHERE id = ?", flowUuid);

        assertCount("ERROR", false, "causeA", 0);
    }

    @Test
    void causeChangeDecrementsAcrossDrain() {
        UUID did = insertDeltaFile();
        UUID flowUuid = insertFlow(did, "ERROR", null, "causeA");
        drainDeltas();
        assertSummaryRow("ERROR", false, "causeA", 1);

        jdbc.update("UPDATE delta_file_flows SET error_or_filter_cause = 'causeB' WHERE id = ?", flowUuid);

        assertCount("ERROR", false, "causeA", 0);
        assertCount("ERROR", false, "causeB", 1);
    }

    @Test
    void rebuildRecoversFromDrift() {
        UUID did = insertDeltaFile();
        insertFlow(did, "ERROR", null, "causeA");

        // TRUNCATE bypasses row-level triggers. Clear both tables to simulate fully-drifted summary state.
        jdbc.execute("TRUNCATE flow_summary_counts");
        jdbc.execute("TRUNCATE flow_summary_deltas");

        Long empty = jdbc.queryForObject(
                "SELECT COALESCE(SUM(error_count), 0) FROM flow_summary_counts", Long.class);
        assertEquals(0L, empty);

        jdbc.execute("SELECT rebuild_flow_summary_counts()");
        assertCount("ERROR", false, "causeA", 1);
    }

    // --- helpers ---

    private UUID insertDeltaFile() {
        UUID did = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO delta_files (did, requeue_count, ingress_bytes, referenced_bytes, total_bytes, version,
                        content_deletable, terminal, stage, data_source_id, created, modified)
                VALUES (?, 0, 0, 0, 0, 0, false, false, CAST('ERROR' AS df_stage_enum), ?, NOW(), NOW())
                """, did, flowId);
        return did;
    }

    private UUID insertFlow(UUID deltaFileId, String state, String ackTimestamp, String cause) {
        UUID flowUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO delta_file_flows (id, delta_file_id, depth, number, version, test_mode,
                        flow_definition_id, state, error_acknowledged, error_or_filter_cause, created, modified)
                VALUES (?, ?, 0, 0, 0, false, ?, CAST(? AS dff_state_enum),
                        CAST(? AS timestamptz), ?, NOW(), NOW())
                """, flowUuid, deltaFileId, flowId, state, ackTimestamp, cause);
        return flowUuid;
    }

    private void assertCount(String state, boolean acknowledged, String cause, int expected) {
        // Triggers append to flow_summary_deltas; drain them into flow_summary_counts before asserting.
        drainDeltas();
        assertSummaryRow(state, acknowledged, cause, expected);
    }

    /**
     * Reads the current state of flow_summary_counts without draining first. Use this when
     * exercising the middle of a cross-drain scenario — i.e., to verify that a prior drain
     * materialized the row we expect before triggering a new mutation.
     */
    private void assertSummaryRow(String state, boolean acknowledged, String cause, int expected) {
        String causeClause = cause == null ? "error_or_filter_cause IS NULL" : "error_or_filter_cause = ?";
        String sql = """
                SELECT COALESCE(SUM(error_count), 0)
                FROM flow_summary_counts
                WHERE state = CAST(? AS dff_state_enum)
                AND acknowledged = ?
                AND flow_definition_id = ?
                AND %s""".formatted(causeClause);

        Object[] params = cause == null
                ? new Object[]{state, acknowledged, flowId}
                : new Object[]{state, acknowledged, flowId, cause};

        Long actual = jdbc.queryForObject(sql, Long.class, params);
        assertEquals((long) expected, actual,
                "(" + state + ", ack=" + acknowledged + ", cause=" + cause + ")");
    }
}
