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
package org.deltafi.core.repo;

import com.mongodb.BasicDBObject;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.types.PropertyType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;

@Slf4j
public class DeltaFiPropertiesRepoImpl implements DeltaFiPropertiesRepoCustom {
    private static final Query ID_QUERY = new Query(Criteria.where("_id").is(DeltaFiProperties.PROPERTY_ID));
    private static final DeltaFiProperties DEFAULT_PROPS = new DeltaFiProperties();
    public static final String SET_PROPERTIES = "setProperties";
    private final MongoTemplate mongoTemplate;

    public DeltaFiPropertiesRepoImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean updateProperties(Map<PropertyType, String> updateMap) {
        Update update = new Update();

        for (Map.Entry<PropertyType, String> entry : updateMap.entrySet()) {
            PropertyType propertyType = entry.getKey();
            update.set(propertyType.getKey(), propertyType.convertValue(entry.getValue()));
            update.addToSet(SET_PROPERTIES, propertyType);
        }

        return executeUpdate(update);
    }

    @Override
    public boolean unsetProperties(List<PropertyType> propertyTypes) {
        Update update = new Update();

        for (PropertyType propertyType : propertyTypes) {
            update.set(propertyType.getKey(), propertyType.getProperty(DEFAULT_PROPS));
            update.pull(SET_PROPERTIES, propertyType);
        }

        return executeUpdate(update);
    }

    @Override
    public boolean removeExternalLink(String linkName) {
        return removeLink("ui.externalLinks", linkName);
    }

    @Override
    public boolean removeDeltaFileLink(String linkName) {
        return removeLink("ui.deltaFileLinks", linkName);
    }

    private boolean removeLink(String key, String linkName) {
        return executeUpdate(new Update().pull(key, new BasicDBObject("name", linkName)));
    }

    private boolean executeUpdate(Update update) {
        return mongoTemplate.updateFirst(ID_QUERY, update, DeltaFiProperties.class).getModifiedCount() > 0;
    }
}
