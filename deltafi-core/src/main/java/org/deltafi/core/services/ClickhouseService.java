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
package org.deltafi.core.services;

import lombok.Builder;
import lombok.Data;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.DeltaFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ClickhouseService {
    public final boolean enabled;
    public boolean initialized = false;
    private final String database;
    private final String password;
    private final String username;
    private final String connectionURL;
    private final String databaseConnectionURL;

    private final ConcurrentLinkedQueue<ErrorsObject> errorsInsert = new ConcurrentLinkedQueue<>();
    private final ArrayList<ErrorsObject> pendingErrors = new ArrayList<>();

    private static final String ERRORS_TABLE = "errors";
    private static final int BATCH_SIZE = 5000;

    public ClickhouseService(@Value("${CLICKHOUSE_ENABLED:true}") boolean enabled,
                             @Value("${CLICKHOUSE_HOST:deltafi-clickhouse}") String hostname,
                             @Value("${CLICKHOUSE_PORT:8123}") int port,
                             @Value("${CLICKHOUSE_DATABASE:deltafi}") String database,
                             @Value("${CLICKHOUSE_USER:default}") String username,
                             @Value("${CLICKHOUSE_PASSWORD:deltafi}") String password,
                             DeltaFiPropertiesService deltaFiPropertiesService) {
        this.enabled = enabled && deltaFiPropertiesService.getDeltaFiProperties().getMetrics().isErrorAnalyticsEnabled();
        this.database = database;
        this.username = username;
        this.password = password;
        connectionURL = String.format("jdbc:clickhouse://%s:%d", hostname, port);
        databaseConnectionURL = String.format("jdbc:clickhouse://%s:%d/%s", hostname, port, database);

        if (!this.enabled) return;

        log.info("ClickhouseService enabled");
    }

    private void initializeDatabase() {
        if (!enabled || initialized) return;

        log.info ("Initializing Clickhouse Database");

        try (Connection connection = DriverManager.getConnection(connectionURL, username, password)) {

            try (Statement statement = connection.createStatement()) {
                statement.addBatch(String.format("CREATE DATABASE IF NOT EXISTS %s", database));
                statement.addBatch(String.format("""
                        CREATE TABLE IF NOT EXISTS %s.%s
                        (
                            `timestamp` DateTime,
                            `dataSource` String,
                            `cause` String,
                            `flow` String,
                            `action` String,
                            `annotations` Map(String, String)
                        )
                        ENGINE = MergeTree
                        PARTITION BY (toYYYYMMDD(timestamp), dataSource)
                        ORDER BY (dataSource, flow, timestamp)
                        TTL timestamp + INTERVAL 30 DAY""", database, ERRORS_TABLE));
                statement.executeBatch();
            } catch (SQLException e) {
                log.error("Unable to initialize Clickhouse database: ", e);
                return;
            }
        } catch (SQLException e) {
            log.error("Unable to connect to Clickhouse: ", e);
            return;
        }

        initialized = true;
        log.info("Initialized ClickhouseService");
    }

    /**
     * Insert an error into the ClickHouse errors table
     * @param deltafile - errored DeltaFile
     * @param flow - name of the flow
     * @param action - name of the action that errored
     * @param cause - cause of the error
     */
    public void insertError(DeltaFile deltafile, String flow, String action, String cause) {
        if (!enabled) return;

        ErrorsObject error = ErrorsObject.builder()
                .timestamp(deltafile.getModified())
                .dataSource(deltafile.getSourceInfo().getFlow())
                .cause(cause)
                .flow(flow)
                .action(action)
                .annotations(deltafile.getAnnotations())
                .build();

        errorsInsert.add(error);
    }

    private boolean flushPendingErrors() {
        if (pendingErrors.isEmpty()) return true;
        try (Connection connection = DriverManager.getConnection(databaseConnectionURL, username, password)) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + database + "." + ERRORS_TABLE + " VALUES (?, ?, ?, ?, ?, ?)")) {
                pendingErrors.forEach(error -> error.addTo(statement));
                statement.executeBatch();
                pendingErrors.clear();
                return true;
            } catch (SQLException e) {
                log.error("Unable to insert errors into Clickhouse database: ", e);
                return false;
            }
        } catch (SQLException e) {
            log.warn("Unable to connect to Clickhouse: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Flush out all errorsObjects that have been added to the queue
     */
    @Scheduled(initialDelay = 30, fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    @Synchronized
    private void flush() {
        if (!enabled) return;
        if (!initialized) initializeDatabase();
        if (!initialized) {
            log.warn("Unable to initialize Clickhouse");
            return;
        }

        if (!pendingErrors.isEmpty()) {
            if (flushPendingErrors()) {
                log.info("Resumed flushing errors to Clickhouse");
            } else {
                log.warn("Unable to flush errors to Clickhouse");
                return;
            }
        }

        ErrorsObject errorsObject;
        int count = 0;
        while ((errorsObject = errorsInsert.poll()) != null) {
            pendingErrors.add(errorsObject);
            count++;
            if (count % BATCH_SIZE == 0) {
                if (!flushPendingErrors()) {
                    log.warn("Interrupted flushing errors to Clickhouse");
                    break;
                }
            }
        }

        if (count % BATCH_SIZE > 0 && !pendingErrors.isEmpty() ) {
            if (!flushPendingErrors()) {
                log.warn("Interrupted flushing errors to Clickhouse");
                return;
            }
        }

        if (count > 0) {
            log.info("Flushed {} errors to Clickhouse", count);
        }
    }

    @Builder
    @Data
    static class ErrorsObject {
        OffsetDateTime timestamp;
        String dataSource;
        String cause;
        String flow;
        String action;
        Map<String, String> annotations;

        void addTo(PreparedStatement statement) {
            try {
                statement.setObject(1, timestamp.toLocalDateTime());
                statement.setString(2, dataSource);
                statement.setString(3, cause);
                statement.setString(4, flow);
                statement.setString(5, action);
                statement.setObject(6, annotations);
                statement.addBatch();
            } catch (SQLException e) {
                log.error("Unable to add error to Clickhouse: ", e);
            }
        }
    }
}
