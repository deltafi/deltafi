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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.Event;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
public class EventRepoImpl implements EventRepoCustom {

    private static final String ID = "_id";
    private static final String TIMESTAMP = "timestamp";
    private static final String ACKNOWLEDGED = "acknowledged";
    private static final String NOTIFICATION = "notification";
    private static final String SEVERITY = "severity";
    private static final String SUMMARY = "summary";
    private static final String CONTENT = "content";
    private static final String SOURCE = "source";

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<Event> updateAcknowledged(String id, boolean acknowledged) {
        return Optional.ofNullable(mongoTemplate.findAndModify(Query.query(Criteria.where(ID).is(id)),
                Update.update(ACKNOWLEDGED, acknowledged), FindAndModifyOptions.options().returnNew(true), Event.class));
    }

    @Override
    public List<Event> findEvents(Map<String, String> filters) {
        OffsetDateTime end = null;
        OffsetDateTime start = null;

        Criteria criteria = new Criteria();
        for (Entry<String, String> entry : filters.entrySet()) {
            switch (entry.getKey()) {
                case ACKNOWLEDGED, NOTIFICATION -> criteria.and(entry.getKey()).is(Boolean.parseBoolean(entry.getValue()));
                case ID, SEVERITY, SUMMARY, CONTENT, SOURCE -> criteria.and(entry.getKey()).is(entry.getValue());
                case "start" -> start = OffsetDateTime.parse(entry.getValue());
                case "end" -> end = OffsetDateTime.parse(entry.getValue());
                default -> log.warn("Unexpected filter {}: {}", entry.getKey(), entry.getValue());
            }
        }

        final OffsetDateTime endTime = Objects.requireNonNullElseGet(end, OffsetDateTime::now);
        start = Objects.requireNonNullElseGet(start, () -> endTime.minusDays(1));
        criteria.and(TIMESTAMP).gt(start).lt(endTime);

        return mongoTemplate.find(Query.query(criteria).with(Sort.by(Direction.DESC, TIMESTAMP)), Event.class);
    }
}
