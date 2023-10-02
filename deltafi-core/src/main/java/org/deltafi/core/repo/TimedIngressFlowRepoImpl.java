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
import org.deltafi.core.types.TimedIngressFlow;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.OffsetDateTime;

@SuppressWarnings("unused")
@Slf4j
public class TimedIngressFlowRepoImpl extends BaseFlowRepoImpl<TimedIngressFlow> implements TimedIngressFlowRepoCustom {

    private static final String INTERVAL = "interval";
    private static final String LAST_RUN = "lastRun";

    public TimedIngressFlowRepoImpl(MongoTemplate mongoTemplate) {
        super(mongoTemplate, TimedIngressFlow.class);
    }

    @Override
    public boolean updateInterval(String flowName, Duration interval) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName).and(INTERVAL).ne(interval));
        Update intervalUpdate = Update.update(INTERVAL, interval);
        return 1 == mongoTemplate.updateFirst(idMatches, intervalUpdate, TimedIngressFlow.class).getModifiedCount();
    }

    @Override
    public boolean updateLastRun(String flowName, OffsetDateTime lastRun) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName));
        Update lastRunUpdate = Update.update(LAST_RUN, lastRun);
        return 1 == mongoTemplate.updateFirst(idMatches, lastRunUpdate, TimedIngressFlow.class).getModifiedCount();
    }
}
