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

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.result.DeleteResult;
import org.deltafi.common.types.PropertySet;
import org.deltafi.core.domain.types.PropertyId;
import org.deltafi.core.domain.types.PropertyUpdate;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Property set repository backed by mongo
 */
@Repository
public class PropertyRepositoryImpl implements PropertyRepository {

    public static final String ID = "_id";
    private final MongoTemplate mongoTemplate;

    public PropertyRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public PropertySet save(PropertySet propertySet) {
        return mongoTemplate.save(propertySet);
    }

    @Override
    public List<PropertySet> saveAll(Collection<PropertySet> propertySets) {
        return propertySets.stream().map(this::save).collect(Collectors.toList());
    }

    @Override
    public Optional<PropertySet> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, PropertySet.class));
    }

    @Override
    public List<PropertySet> findAll() {
        return mongoTemplate.findAll(PropertySet.class);
    }

    @Override
    public boolean removeById(String id) {
        DeleteResult result = mongoTemplate.remove(Query.query(Criteria.where(ID).is(id)), PropertySet.class);
        return result.getDeletedCount() > 0;
    }

    @Override
    public void removeAll() {
        mongoTemplate.remove(new Query(), PropertySet.class);
    }

    @Override
    public Set<String> getIds() {
        return new HashSet<>(mongoTemplate.findDistinct(ID, PropertySet.class, String.class));
    }

    @Override
    public int updateProperties(List<PropertyUpdate> updates) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, PropertySet.class);
        bulkOps.updateOne(updates.stream().map(this::toMongoUpdate).collect(Collectors.toList()));
        BulkWriteResult writeResult = bulkOps.execute();
        return writeResult.getModifiedCount();
    }

    @Override
    public int unsetProperties(List<PropertyId> propertyIds) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, PropertySet.class);
        bulkOps.updateOne(propertyIds.stream().map(this::toMongoUnset).collect(Collectors.toList()));
        BulkWriteResult writeResult = bulkOps.execute();
        return writeResult.getModifiedCount();
    }

    private Pair<Query, Update> toMongoUnset(PropertyId propertyId) {
        Query query = propertyQuery(propertyId.getPropertySetId(), propertyId.getKey());
        Update mongoUpdate = new Update();
        mongoUpdate.unset("properties.$.value");
        return Pair.of(query, mongoUpdate);
    }

    private Pair<Query, Update> toMongoUpdate(PropertyUpdate update) {
        Query query = propertyQuery(update.getPropertySetId(), update.getKey());
        Update mongoUpdate = Update.update("properties.$.value", update.getValue());
        return Pair.of(query, mongoUpdate);
    }

    private Query propertyQuery(String propertySetId, String propertyKey) {
        return Query.query(Criteria.where(ID).is(propertySetId)
                .and("properties")
                .elemMatch(Criteria.where("key").is(propertyKey).and("editable").is(true)));
    }
}
