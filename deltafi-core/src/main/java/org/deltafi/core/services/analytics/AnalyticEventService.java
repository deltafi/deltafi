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
package org.deltafi.core.services.analytics;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.DeltaFile;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Generate analytic events.
 */
@Slf4j
@Service
public class AnalyticEventService {
    private DataSource dataSource = null;

    private final boolean enabled;
    private boolean initialized = false;
    private static final int BATCH_SIZE = 5000;
    private static final String DATABASE = "deltafi";

    private final EventPipeline<CompletedDeltaFileEvent> completed;
    private final EventPipeline<ErrorEvent> errors;

    private final List<EventPipeline<? extends AnalyticEvent>> pipelines;

    public AnalyticEventService(@Value("${CLICKHOUSE_ENABLED:true}") boolean enabled,
                                @Value("${CLICKHOUSE_HOST:deltafi-clickhouse}") String hostname,
                                @Value("${CLICKHOUSE_PORT:8123}") int port,
                                @Value("${CLICKHOUSE_USER:default}") String username,
                                @Value("${CLICKHOUSE_PASSWORD:deltafi}") String password,
                                DeltaFiPropertiesService deltaFiPropertiesService) {
        this.enabled = enabled && deltaFiPropertiesService.getDeltaFiProperties().isMetricsErrorAnalyticsEnabled();
        completed = new EventPipeline<>(CompletedDeltaFileEvent.TABLE_NAME,
                CompletedDeltaFileEvent.INSERT,
                CompletedDeltaFileEvent::addSchema);
        errors = new EventPipeline<>(ErrorEvent.TABLE_NAME,
                ErrorEvent.INSERT,
                ErrorEvent::addSchema);
        pipelines = List.of(completed, errors);

        if (!this.enabled) return;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format("jdbc:clickhouse://%s:%d", hostname, port));
        config.setUsername(username);
        config.setPassword(password);
        this.dataSource = new HikariDataSource(config);

        log.info("AnalyticEventService enabled");
    }

    /**
     * Generate an analytic event for DeltaFile error.
     * @param deltafile - errored DeltaFile
     * @param flow - name of the flow
     * @param action - name of the action that errored
     * @param cause - cause of the error
     */
    public void recordError(DeltaFile deltafile, String flow, String action, String cause) {
        if (!enabled) return;

        errors.add(ErrorEvent.builder()
                .timestamp(deltafile.getModified())
                .dataSource(deltafile.getDataSource())
                .cause(cause)
                .flow(flow)
                .action(action)
                .ingressBytes(deltafile.getIngressBytes())
                .annotations(deltafile.getAnnotations())
                .build());
    }

    /**
     * Generate an analytic event for DeltaFile completion or cancellation.
     *
     * @param  deltafile  the DeltaFile to be recorded
     */
    public void recordCompleted(DeltaFile deltafile) {
        if (!enabled) return;
        completed.add(new CompletedDeltaFileEvent(deltafile));
    }

    /**
     * Generate analytic events for Surveys
     *
     * @param surveyEvents the list of surveys to record
     * @return list of errors if there was invalid survey data
     */
    public List<SurveyError> recordSurveys(List<SurveyEvent> surveyEvents) {
        if (!enabled) {
            log.error("Attempted to add survey metrics with analytics disabled");
            throw new DisabledAnalyticsException();
        }

        if (surveyEvents == null || surveyEvents.isEmpty()) {
            return List.of();
        }

        Instant instant = OffsetDateTime.now(ZoneOffset.UTC).toInstant();
        String formattedNow = "" + instant.getEpochSecond() + instant.getNano();
        List<SurveyError> surveyErrors = new ArrayList<>();
        int index = 0;
        List<CompletedDeltaFileEvent> completedEvents = new ArrayList<>();
        for (SurveyEvent surveyEvent : surveyEvents) {
            if (surveyEvent.isValid()) {
                String did = "survey-" + index + "-" + formattedNow;
                completedEvents.add(new CompletedDeltaFileEvent(surveyEvent, did));
            } else {
                surveyErrors.add(new SurveyError(index, surveyEvent));
            }
            index++;
        }

        // if there were errors do not process any of the survey entries
        if (!surveyErrors.isEmpty()) {
            return surveyErrors;
        }

        completed.addAll(completedEvents);
        processPipelines(); // fire immediately instead of waiting for the next scheduled execution
        return List.of();
    }

    /**
     * Process all pending analytic events and then process the queued events
     * in each pipeline
     */
    @Scheduled(initialDelay = 30, fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    @Synchronized
    private void processPipelines() {
        if (!enabled) return;
        if (!initialized) initializeDatabase();
        if (!initialized) {
            log.warn("Unable to initialize Clickhouse");
            return;
        }

        if (pipelines.stream().anyMatch(EventPipeline::pending)) {

            boolean success = pipelines.stream().allMatch(this::writePending);
            if (!success) {
                log.warn("Unable to record to Clickhouse");
                return;
            }

            log.info("Resumed recording to Clickhouse");
        }
        pipelines.forEach(this::drainQueue);
    }


    private void initializeDatabase() {
        if (!enabled || initialized) return;

        log.info ("Initializing Clickhouse Database");

        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.addBatch(String.format("CREATE DATABASE IF NOT EXISTS %s", DATABASE));
                for (EventPipeline<? extends AnalyticEvent> pipeline : pipelines) {
                    pipeline.addSchema(statement);
                }
                statement.executeBatch();
            } catch (SQLException e) {
                log.error("Unable to initialize Clickhouse database: ", e);
                return;
            }
        } catch (SQLException e) {
            log.error("Unable to connect to Clickhouse: {}", e.getMessage());
            return;
        }

        initialized = true;
        log.info("Initialized AnalyticEventService");
    }

    /**
     * Write all pending events to the database.
     *
     * @param  pipeline  the event pipeline containing pending events
     * @return           true if writing is successful, false otherwise
     */
    private <T extends AnalyticEvent> boolean writePending(EventPipeline<T> pipeline) {
        if (!pipeline.pending()) return true;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(pipeline.insertSql)) {
                pipeline.pending.forEach(item -> item.addTo(statement));
                statement.executeBatch();
                pipeline.clearPending();
                return true;
            } catch (SQLException e) {
                log.error("Unable to insert into Clickhouse {} table: ", pipeline.tableName, e);
                initialized = false;
                return false;
            }
        } catch (SQLException e) {
            log.warn("Unable to connect to Clickhouse: {}", e.getMessage());
            initialized = false;
            return false;
        }
    }

    /**
     * Drains the EventPipeline queue of analytic events in batches of BATCH_SIZE.
     *
     * @param  pipeline  the event pipeline to drain
     */
    private <T extends AnalyticEvent> void drainQueue(EventPipeline<T> pipeline) {
        T entry;
        int count = 0;
        while ((entry = pipeline.queue.poll()) != null) {
            pipeline.pending.add(entry);
            count++;
            if (count % BATCH_SIZE == 0) {
                if (!writePending(pipeline)) {
                    log.warn("Interrupted batch flushing {} to Clickhouse", pipeline.tableName);
                    break;
                }
            }
        }

        if (count % BATCH_SIZE > 0 && pipeline.pending() ) {
            if (!writePending(pipeline)) {
                log.warn("Interrupted flushing {} to Clickhouse", pipeline.tableName);
                return;
            }
        }

        if (count > 0) {
            log.info("Flushed {} {} to Clickhouse", count, pipeline.tableName);
        }
    }

    public record SurveyError(String error, SurveyEvent surveyEvent) {
        public SurveyError(int index, SurveyEvent surveyEvent) {
            this("Invalid survey data at " + index, surveyEvent);
        }
    }

    public static class DisabledAnalyticsException extends RuntimeException { }
}
