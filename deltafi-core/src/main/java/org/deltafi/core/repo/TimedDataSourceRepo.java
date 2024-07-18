package org.deltafi.core.repo;

import jakarta.transaction.Transactional;
import org.deltafi.common.types.IngressStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface TimedDataSourceRepo extends FlowRepo {
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
}
