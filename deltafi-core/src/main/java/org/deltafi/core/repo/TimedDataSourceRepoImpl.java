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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.IngressStatus;
import org.deltafi.core.types.TimedDataSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.OffsetDateTime;
import java.util.UUID;

@SuppressWarnings("unused")
@Slf4j
public class TimedDataSourceRepoImpl extends BaseFlowRepoImpl<TimedDataSource> implements TimedDataSourceRepoCustom {

    private static final String CURRENT_DID = "currentDid";
    private static final String EXECUTE_IMMEDIATE = "executeImmediate";
    private static final String INGRESS_STATUS = "ingressStatus";
    private static final String INGRESS_STATUS_MESSAGE = "ingressStatusMessage";
    private static final String CRON_SCHEDULE = "cronSchedule";
    private static final String LAST_RUN = "lastRun";
    private static final String NEXT_RUN = "nextRun";
    private static final String MEMO = "memo";

    public TimedDataSourceRepoImpl(MongoTemplate mongoTemplate) {
        super(mongoTemplate, TimedDataSource.class);
    }

    @Override
    public boolean updateCronSchedule(String flowName, String cronSchedule, OffsetDateTime nextRun) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName).and(CRON_SCHEDULE).ne(cronSchedule));
        Update cronScheduleUpdate = Update.update(CRON_SCHEDULE, cronSchedule).set(NEXT_RUN, nextRun);
        return 1 == mongoTemplate.updateFirst(idMatches, cronScheduleUpdate, TimedDataSource.class).getModifiedCount();
    }

    @Override
    public boolean updateLastRun(String flowName, OffsetDateTime lastRun, UUID currentDid) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName));
        Update lastRunUpdate = Update.update(LAST_RUN, lastRun).set(CURRENT_DID, currentDid).set(EXECUTE_IMMEDIATE, false);
        return 1 == mongoTemplate.updateFirst(idMatches, lastRunUpdate, TimedDataSource.class).getModifiedCount();
    }

    @Override
    public boolean updateMemo(String flowName, String memo) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName));
        Update memoUpdate = Update.update(MEMO, memo);
        return 1 == mongoTemplate.updateFirst(idMatches, memoUpdate, TimedDataSource.class).getModifiedCount();
    }

    @Override
    public boolean completeExecution(String flowName, UUID currentDid, String memo, boolean executeImmediate,
            IngressStatus status, String statusMessage, OffsetDateTime nextRun) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName).and(CURRENT_DID).is(currentDid));
        Update update = Update.update(CURRENT_DID, null).set(MEMO, memo).set(EXECUTE_IMMEDIATE, executeImmediate)
                .set(INGRESS_STATUS, status).set(INGRESS_STATUS_MESSAGE, statusMessage).set(NEXT_RUN, nextRun);
        return 1 == mongoTemplate.updateFirst(idMatches, update, TimedDataSource.class).getModifiedCount();
    }
}
