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

import lombok.RequiredArgsConstructor;
import org.deltafi.common.uuid.UUIDGenerator;
import org.deltafi.core.types.JoinDefinition;
import org.deltafi.core.types.JoinEntry;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@RequiredArgsConstructor
public class JoinEntryRepoImpl implements JoinEntryRepoCustom {
    private static final String ID_FIELD = "id";
    private static final String JOIN_DEFINITION_FIELD = "joinDefinition";
    private static final String LOCKED_FIELD = "locked";
    private static final String LOCKED_TIME_FIELD = "lockedTime";
    private static final String JOIN_DATE_FIELD = "joinDate";
    private static final String MIN_NUM_FIELD = "minNum";
    private static final String MAX_NUM_FIELD = "maxNum";
    private static final String COUNT_FIELD = "count";
    private static final String MAX_FLOW_DEPTH = "maxFlowDepth";

    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final UUIDGenerator uuidGenerator;

    @Override
    public void ensureJoinDefinitionIndex() {
        IndexOperations indexOperations = mongoTemplate.indexOps(JoinEntry.class);
        IndexUtils.updateMongoIndices(indexOperations, "unique_join_definition",
                new Index(JOIN_DEFINITION_FIELD, Sort.Direction.ASC).named("unique_join_definition").unique(),
                indexOperations.getIndexInfo());
    }

    @Override
    public JoinEntry upsertAndLock(JoinDefinition joinDefinition, OffsetDateTime joinDate, Integer minNum,
                                   Integer maxNum, int maxFlowDepth) {
        Query query = new Query().addCriteria(Criteria.where(JOIN_DEFINITION_FIELD).is(joinDefinition)
                .and(LOCKED_FIELD).is(false));
        UpdateDefinition updateDefinition = new Update()
                .setOnInsert(JOIN_DATE_FIELD, joinDate)
                .setOnInsert(MIN_NUM_FIELD, minNum)
                .setOnInsert(MAX_NUM_FIELD, maxNum)
                .setOnInsert("_id", uuidGenerator.generate())
                .set(LOCKED_FIELD, true)
                .set(LOCKED_TIME_FIELD, OffsetDateTime.now(clock))
                .max(MAX_FLOW_DEPTH, maxFlowDepth)
                .inc(COUNT_FIELD, 1);
        FindAndModifyOptions findAndModifyOptions = FindAndModifyOptions.options().upsert(true).returnNew(true);
        return mongoTemplate.findAndModify(query, updateDefinition, findAndModifyOptions, JoinEntry.class);
    }

    @Override
    public JoinEntry lockOneBefore(OffsetDateTime joinDate) {
        Query query = new Query().addCriteria(Criteria.where(JOIN_DATE_FIELD).lte(joinDate)
                .and(LOCKED_FIELD).is(false));
        UpdateDefinition updateDefinition = new Update()
                .set(LOCKED_FIELD, true)
                .set(LOCKED_TIME_FIELD, OffsetDateTime.now(clock));
        return mongoTemplate.findAndModify(query, updateDefinition, JoinEntry.class);
    }

    @Override
    public void unlock(UUID id) {
        mongoTemplate.updateFirst(new Query().addCriteria(Criteria.where(ID_FIELD).is(id)),
                new Update().set(LOCKED_FIELD, false).unset(LOCKED_TIME_FIELD), JoinEntry.class);
    }

    @Override
    public long unlockBefore(OffsetDateTime lockDate) {
        return mongoTemplate.updateMulti(new Query().addCriteria(Criteria.where(LOCKED_TIME_FIELD).lt(lockDate)),
                new Update().set(LOCKED_FIELD, false).unset(LOCKED_TIME_FIELD), JoinEntry.class).getModifiedCount();
    }
}
