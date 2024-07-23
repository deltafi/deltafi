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

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.types.Property;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

@Slf4j
@SuppressWarnings("unused")
@RequiredArgsConstructor
public class DeltaFiPropertiesRepoImpl implements DeltaFiPropertiesRepoCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public void upsertProperties(List<Property> properties) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Property.class);

        for (Property property : properties) {
            Query query = new Query(Criteria.where("_id").is(property.getKey()));
            Update update = new Update()
                    .set("defaultValue", property.getDefaultValue())
                    .set("description", property.getDescription())
                    .set("refreshable", property.isRefreshable());

            bulkOps.upsert(query, update);
        }

        bulkOps.execute();
    }

    @Override
    public boolean updateProperties(List<KeyValue> updates) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkMode.UNORDERED, Property.class);

        for (KeyValue updateKeyValue : updates) {
            Query query = new Query(Criteria.where("_id").is(updateKeyValue.getKey()));
            Update update = new Update().set("customValue", updateKeyValue.getValue());

            bulkOps.updateOne(query, update);
        }

        BulkWriteResult results = bulkOps.execute();
        return results.getModifiedCount() > 0;
    }

    @Override
    public boolean unsetProperties(List<String> propertyNames) {
        Update update = new Update();
        update.unset("customValue");
        UpdateResult result = mongoTemplate.updateMulti(Query.query(Criteria.where("_id").in(propertyNames)), update, Property.class);
        return result.getModifiedCount() > 0;
    }
}
