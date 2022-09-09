/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.config.server.repo;

import org.deltafi.core.domain.types.StateHolder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.UUID;

public class StateHolderRepositoryImpl implements StateHolderRepository {

    public static final String ID = "_id";
    private final MongoTemplate mongoTemplate;

    public StateHolderRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public UUID getOrInit() {
        if (0 == mongoTemplate.count(new Query(), StateHolder.class)) {
            StateHolder stateHolder = new StateHolder();
            stateHolder.setStateId(UUID.randomUUID());
            mongoTemplate.save(stateHolder);
            return stateHolder.getStateId();
        }

        return getCurrentState();
    }

    @Override
    public UUID getCurrentState() {
        return mongoTemplate.findById(StateHolder.STATIC_ID, StateHolder.class).getStateId();
    }

    @Override
    public void replaceStateHolderUUID(UUID uuid) {
        mongoTemplate.updateFirst(Query.query(Criteria.where(ID).is(StateHolder.STATIC_ID)), Update.update("stateId", uuid.toString()), StateHolder.class);
    }
}
