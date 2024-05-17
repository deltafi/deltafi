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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.DeltaFile;
import org.deltafi.common.types.DeltaFileStage;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
class CompletedDeltaFileEvent implements AnalyticEvent {
    static final String TABLE_NAME = "complete_deltafiles";
    static final String INSERT = "INSERT INTO deltafi." + TABLE_NAME + " (timestamp, did, dataSource, ingressBytes, files, totalBytes, egressedFiles, filteredFiles, cancelledFiles, egresses, annotations) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String CREATE = String.format("""
                CREATE TABLE IF NOT EXISTS deltafi.%s
                (
                    `timestamp` DateTime,
                    `did` String,
                    `dataSource` LowCardinality(String),
                    `ingressBytes` UInt64,
                    `files` UInt64,
                    `totalBytes` UInt64,
                    `egressedFiles` UInt64,
                    `filteredFiles` UInt64,
                    `cancelledFiles` UInt64,
                    `egresses` Array(String),
                    `annotations` Map(String, String)
                )
                ENGINE = MergeTree
                PARTITION BY (toYYYYMMDD(timestamp), dataSource)
                ORDER BY (dataSource, timestamp)
                TTL timestamp + INTERVAL 30 DAY""", TABLE_NAME);
    OffsetDateTime timestamp;
    String did;
    String dataSource;
    long ingressBytes;
    long files;
    long totalBytes;
    long egressedFiles;
    long filteredFiles;
    long cancelledFiles;
    Map<String, String> annotations;
    List<String> egresses;

    CompletedDeltaFileEvent(DeltaFile deltafile) {
        this.timestamp = deltafile.getCreated();
        this.did = deltafile.getDid().toString();
        this.dataSource = deltafile.getDataSource();
        this.ingressBytes = deltafile.getIngressBytes();
        this.files = 1L;
        this.totalBytes = deltafile.getTotalBytes();
        this.egressedFiles = deltafile.getEgressed() ? 1L : 0L;
        this.filteredFiles = deltafile.getFiltered() ? 1L : 0L;
        this.cancelledFiles = deltafile.getStage() == DeltaFileStage.CANCELLED ? 1L : 0L;
        this.annotations = deltafile.getAnnotations();
        this.egresses = deltafile.getEgressFlows();
    }

    public void addTo(@NotNull PreparedStatement statement) {
        try {
            statement.setObject(1, timestamp.toLocalDateTime());
            statement.setString(2, did);
            statement.setString(3, dataSource);
            statement.setLong(4, ingressBytes);
            statement.setLong(5, files);
            statement.setLong(6, totalBytes);
            statement.setLong(7, egressedFiles);
            statement.setLong(8, filteredFiles);
            statement.setLong(9, cancelledFiles);
            statement.setObject(10, egresses);
            statement.setObject(11, annotations);
            statement.addBatch();
        } catch (SQLException e) {
            log.error("Unable to add completed deltafile to Clickhouse: ", e);
        }
    }

    public static void addSchema(Statement statement) throws SQLException {
        statement.addBatch(CREATE);
    }
}
