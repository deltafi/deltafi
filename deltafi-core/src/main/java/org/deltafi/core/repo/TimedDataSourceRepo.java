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
package org.deltafi.core.repo;

import jakarta.transaction.Transactional;
import org.deltafi.common.types.IngressStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface TimedDataSourceRepo extends FlowRepo, TimedDataSourceRepoCustom {
    @Modifying
    @Transactional
    @Query("UPDATE TimedDataSource t SET t.cronSchedule = :cronSchedule, t.nextRun = :nextRun WHERE t.name = :flowName")
    int updateCronSchedule(String flowName, String cronSchedule, OffsetDateTime nextRun);

    @Modifying
    @Transactional
    @Query("UPDATE TimedDataSource t SET t.lastRun = :lastRun, t.currentDid = :currentDid, t.executeImmediate = false WHERE t.name = :flowName")
    int updateLastRun(String flowName, OffsetDateTime lastRun, UUID currentDid);

    @Modifying
    @Transactional
    @Query("UPDATE TimedDataSource t SET t.memo = :memo WHERE t.name = :flowName")
    int updateMemo(String flowName, String memo);

    @Modifying
    @Transactional
    @Query("UPDATE TimedDataSource t SET t.currentDid = null, t.memo = :memo, t.executeImmediate = :executeImmediate, t.ingressStatus = :status, t.ingressStatusMessage = :statusMessage, t.nextRun = :nextRun WHERE t.name = :flowName AND t.currentDid = :currentDid")
    int completeExecution(String flowName, UUID currentDid, String memo, boolean executeImmediate, IngressStatus status, String statusMessage, OffsetDateTime nextRun);

    @Modifying
    @Transactional
    @Query("UPDATE TimedDataSource t SET t.maxErrors = :maxErrors WHERE t.name = :flowName")
    int updateMaxErrors(String flowName, int maxErrors);
}
