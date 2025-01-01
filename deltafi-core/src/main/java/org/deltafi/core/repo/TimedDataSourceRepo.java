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

import org.deltafi.core.types.TimedDataSource;
import org.springframework.transaction.annotation.Transactional;
import org.deltafi.common.types.IngressStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface TimedDataSourceRepo extends FlowRepo, TimedDataSourceRepoCustom {
    @Transactional
    @Query(value = "UPDATE flows " +
            "SET cron_schedule = :cronSchedule, next_run = :nextRun " +
            "WHERE name = :flowName AND type = 'TIMED_DATA_SOURCE' " +
            "RETURNING *", nativeQuery = true)
    TimedDataSource updateCronSchedule(String flowName, String cronSchedule, OffsetDateTime nextRun);

    @Transactional
    @Query(value = "UPDATE flows " +
            "SET last_run = :lastRun, current_did = :currentDid, execute_immediate = false " +
            "WHERE name = :flowName AND type = 'TIMED_DATA_SOURCE' " +
            "RETURNING *", nativeQuery = true)
    TimedDataSource updateLastRun(String flowName, OffsetDateTime lastRun, UUID currentDid);

    @Transactional
    @Query(value = "UPDATE flows " +
            "SET memo = :memo " +
            "WHERE name = :flowName AND type = 'TIMED_DATA_SOURCE' " +
            "RETURNING *", nativeQuery = true)
    TimedDataSource updateMemo(String flowName, String memo);

    @Transactional
    @Query(value = "UPDATE flows " +
            "SET current_did = null, " +
            "    memo = :memo, " +
            "    execute_immediate = :executeImmediate, " +
            "    ingress_status = :status, " +
            "    ingress_status_message = :statusMessage, " +
            "    next_run = :nextRun " +
            "WHERE name = :flowName AND current_did = :currentDid AND type = 'TIMED_DATA_SOURCE' " +
            "RETURNING *", nativeQuery = true)
    TimedDataSource completeExecution(String flowName, UUID currentDid, String memo, boolean executeImmediate, String status, String statusMessage, OffsetDateTime nextRun);

    @Transactional
    @Query(value = "UPDATE flows " +
            "SET max_errors = :maxErrors " +
            "WHERE name = :flowName AND type = 'TIMED_DATA_SOURCE' " +
            "RETURNING *", nativeQuery = true)
    TimedDataSource updateMaxErrors(String flowName, int maxErrors);
}
