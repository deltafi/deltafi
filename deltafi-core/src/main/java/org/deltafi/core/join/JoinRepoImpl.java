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
package org.deltafi.core.join;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.Clock;
import java.time.OffsetDateTime;

@RequiredArgsConstructor
public class JoinRepoImpl implements JoinRepoCustom {
    private static final String ID_FIELD = "id";
    private static final String LOCKED_FIELD = "locked";
    private static final String LOCKED_TIME_FIELD = "lockedTime";
    private static final String JOIN_DATE_FIELD = "joinDate";
    private static final String MAX_DELTA_FILE_ENTRIES_FIELD = "maxDeltaFileEntries";
    private static final String DELTA_FILE_ENTRIES_FIELD = "deltaFileEntries";

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Override
    public JoinEntry upsertAndLock(JoinEntryId id, OffsetDateTime joinDate, Integer maxDeltaFileEntries,
            String did, String index) {
        Query query = new Query().addCriteria(Criteria.where(ID_FIELD).is(id).and(LOCKED_FIELD).is(false));
        UpdateDefinition updateDefinition = new Update()
                .setOnInsert(JOIN_DATE_FIELD, joinDate)
                .setOnInsert(MAX_DELTA_FILE_ENTRIES_FIELD, maxDeltaFileEntries)
                .set(LOCKED_FIELD, true)
                .set(LOCKED_TIME_FIELD, OffsetDateTime.now(clock))
                .push(DELTA_FILE_ENTRIES_FIELD, new IndexedDeltaFileEntry(did, index));
        FindAndModifyOptions findAndModifyOptions = FindAndModifyOptions.options().upsert(true).returnNew(true);
        return mongoTemplate.findAndModify(query, updateDefinition, findAndModifyOptions, JoinEntry.class);
    }

    @Override
    public JoinEntry lockFirstBefore(OffsetDateTime joinDate) {
        Query query = new Query().addCriteria(Criteria.where(JOIN_DATE_FIELD).lte(joinDate)
                .and(LOCKED_FIELD).is(false));
        UpdateDefinition updateDefinition = new Update()
                .set(LOCKED_FIELD, true)
                .set(LOCKED_TIME_FIELD, OffsetDateTime.now(clock));
        return mongoTemplate.findAndModify(query, updateDefinition, JoinEntry.class);
    }

    @Override
    public void unlock(JoinEntryId id) {
        mongoTemplate.updateFirst(new Query().addCriteria(Criteria.where(ID_FIELD).is(id)),
                new Update().set(LOCKED_FIELD, false).unset(LOCKED_TIME_FIELD), JoinEntry.class);
    }

    @Override
    public long unlockBefore(OffsetDateTime lockDate) {
        return mongoTemplate.updateMulti(new Query().addCriteria(Criteria.where(LOCKED_TIME_FIELD).lt(lockDate)),
                new Update().set(LOCKED_FIELD, false).unset(LOCKED_TIME_FIELD), JoinEntry.class).getModifiedCount();
    }
}
