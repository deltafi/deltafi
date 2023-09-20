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

import com.mongodb.DBObject;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

@Slf4j
public class EgressMigrationHelper {

    public static final String INCLUDE_INGRESS_FLOWS = "includeIngressFlows";
    public static final String INCLUDE_NORMALIZE_FLOWS = "includeNormalizeFlows";
    public static final String EXCLUDE_INGRESS_FLOWS = "excludeIngressFlows";
    public static final String EXCLUDE_NORMALIZE_FLOWS = "excludeNormalizeFlows";

    private EgressMigrationHelper() {}

    public static void migrateIngressToNormalize(MongoTemplate mongoTemplate, String collectionName) {
        Criteria includeIngressNotNormalize = Criteria.where(INCLUDE_INGRESS_FLOWS).exists(true).and(INCLUDE_NORMALIZE_FLOWS).exists(false);
        Criteria excludeIngressNotNormalize = Criteria.where(EXCLUDE_INGRESS_FLOWS).exists(true).and(EXCLUDE_NORMALIZE_FLOWS).exists(false);

        // find any documents where the old field is set but the new is not
        Criteria orCriteria = new Criteria().orOperator(includeIngressNotNormalize, excludeIngressNotNormalize);

        List<Document> egressFlows = mongoTemplate.find(Query.query(orCriteria), DBObject.class, collectionName)
                .stream().map(EgressMigrationHelper::fromDbObject).toList();

        int flowCount = egressFlows.size();
        if (flowCount > 0) {
            log.info("Migrating {} entries in the {} collection", flowCount, collectionName);
        }

        egressFlows.forEach(flow -> mongoTemplate.save(flow, collectionName));
    }

    private static Document fromDbObject(DBObject dbObject) {
        copyIfMissing(dbObject, INCLUDE_INGRESS_FLOWS, INCLUDE_NORMALIZE_FLOWS);
        copyIfMissing(dbObject, EXCLUDE_INGRESS_FLOWS, EXCLUDE_NORMALIZE_FLOWS);
        return new Document(dbObject.toMap());
    }

    private static void copyIfMissing(DBObject dbObject, String sourceField, String targetField) {
        if (dbObject.containsField(sourceField) && !dbObject.containsField(targetField)) {
            dbObject.put(targetField, dbObject.get(sourceField));
        }
    }
}
