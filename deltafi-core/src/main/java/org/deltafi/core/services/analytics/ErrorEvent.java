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

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Map;

@Builder
@Data
@Slf4j
class ErrorEvent implements AnalyticEvent {
    static final String TABLE_NAME = "errors";
    static final String INSERT = "INSERT INTO deltafi." + TABLE_NAME + " (timestamp , dataSource, cause, flow, action, ingressBytes, annotations) VALUES (?, ?, ?, ?, ?, ?, ?)";
    static final String CREATE = String.format("""
                CREATE TABLE IF NOT EXISTS deltafi.%s
                (
                    `timestamp` DateTime,
                    `dataSource` LowCardinality(String),
                    `cause` String,
                    `flow` LowCardinality(String),
                    `action` LowCardinality(String),
                    `ingressBytes` UInt64,
                    `annotations` Map(String, String)
                )
                ENGINE = MergeTree
                PARTITION BY (toYYYYMMDD(timestamp), dataSource)
                ORDER BY (dataSource, flow, timestamp)
                TTL timestamp + INTERVAL 30 DAY""", TABLE_NAME);

    OffsetDateTime timestamp;
    String dataSource;
    String cause;
    String flow;
    String action;
    long ingressBytes;
    Map<String, String> annotations;

    @Override
    public void addTo(@NotNull PreparedStatement statement) {
        try {
            statement.setObject(1, timestamp.toLocalDateTime());
            statement.setString(2, dataSource);
            statement.setString(3, cause);
            statement.setString(4, flow);
            statement.setString(5, action);
            statement.setLong(6, ingressBytes);
            statement.setObject(7, annotations);
            statement.addBatch();
        } catch (SQLException e) {
            log.error("Unable to add error to Clickhouse: ", e);
        }
    }

    public static void addSchema(Statement statement) throws SQLException {
        statement.addBatch(CREATE);
    }
}
