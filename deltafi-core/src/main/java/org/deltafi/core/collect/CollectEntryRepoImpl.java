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
package org.deltafi.core.collect;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.deltafi.core.repo.IndexUtils;
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

@RequiredArgsConstructor
public class CollectEntryRepoImpl implements CollectEntryRepoCustom {
    private static final String ID_FIELD = "id";
    private static final String COLLECT_DEFINITION_FIELD = "collectDefinition";
    private static final String LOCKED_FIELD = "locked";
    private static final String LOCKED_TIME_FIELD = "lockedTime";
    private static final String COLLECT_DATE_FIELD = "collectDate";
    private static final String MIN_NUM_FIELD = "minNum";
    private static final String MAX_NUM_FIELD = "maxNum";
    private static final String COUNT_FIELD = "count";

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Override
    public void ensureCollectDefinitionIndex() {
        IndexOperations indexOperations = mongoTemplate.indexOps(CollectEntry.class);
        IndexUtils.updateIndices(indexOperations, "unique_collect_definition",
                new Index(COLLECT_DEFINITION_FIELD, Sort.Direction.ASC).named("unique_collect_definition").unique(),
                indexOperations.getIndexInfo());
    }

    @Override
    public CollectEntry upsertAndLock(CollectDefinition collectDefinition, OffsetDateTime collectDate, Integer minNum,
            Integer maxNum) {
        Query query = new Query().addCriteria(Criteria.where(COLLECT_DEFINITION_FIELD).is(collectDefinition)
                .and(LOCKED_FIELD).is(false));
        UpdateDefinition updateDefinition = new Update()
                .setOnInsert(COLLECT_DATE_FIELD, collectDate)
                .setOnInsert(MIN_NUM_FIELD, minNum)
                .setOnInsert(MAX_NUM_FIELD, maxNum)
                .set(LOCKED_FIELD, true)
                .set(LOCKED_TIME_FIELD, OffsetDateTime.now(clock))
                .inc(COUNT_FIELD, 1);
        FindAndModifyOptions findAndModifyOptions = FindAndModifyOptions.options().upsert(true).returnNew(true);
        return mongoTemplate.findAndModify(query, updateDefinition, findAndModifyOptions, CollectEntry.class);
    }

    @Override
    public CollectEntry lockOneBefore(OffsetDateTime collectDate) {
        Query query = new Query().addCriteria(Criteria.where(COLLECT_DATE_FIELD).lte(collectDate)
                .and(LOCKED_FIELD).is(false));
        UpdateDefinition updateDefinition = new Update()
                .set(LOCKED_FIELD, true)
                .set(LOCKED_TIME_FIELD, OffsetDateTime.now(clock));
        return mongoTemplate.findAndModify(query, updateDefinition, CollectEntry.class);
    }

    @Override
    public void unlock(String id) {
        mongoTemplate.updateFirst(new Query().addCriteria(Criteria.where(ID_FIELD).is(new ObjectId(id))),
                new Update().set(LOCKED_FIELD, false).unset(LOCKED_TIME_FIELD), CollectEntry.class);
    }

    @Override
    public long unlockBefore(OffsetDateTime lockDate) {
        return mongoTemplate.updateMulti(new Query().addCriteria(Criteria.where(LOCKED_TIME_FIELD).lt(lockDate)),
                new Update().set(LOCKED_FIELD, false).unset(LOCKED_TIME_FIELD), CollectEntry.class).getModifiedCount();
    }
}
