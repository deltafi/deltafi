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

import com.mongodb.MongoNamespace;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class NormalizeFlowPlanRepoImpl implements NormalizeFlowPlanRepoCustom {

    public static final String INGRESS_FLOW_PLAN = "ingressFlowPlan";
    public static final String NORMALIZE_FLOW_PLAN = "normalizeFlowPlan";
    private final MongoTemplate mongoTemplate;

    public NormalizeFlowPlanRepoImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void migrateIngressFlowPlans() {
        if (mongoTemplate.collectionExists(INGRESS_FLOW_PLAN)) {
            if (!mongoTemplate.collectionExists(NORMALIZE_FLOW_PLAN)) {
                log.info("Migrating {} collection to {}}", INGRESS_FLOW_PLAN, NORMALIZE_FLOW_PLAN);
                mongoTemplate.getCollection(INGRESS_FLOW_PLAN)
                        .renameCollection(new MongoNamespace(mongoTemplate.getDb().getName(), NORMALIZE_FLOW_PLAN));

                Update update = new Update().set("type", "NORMALIZE");

                mongoTemplate.updateMulti(new Query(), update, NORMALIZE_FLOW_PLAN);
                log.info("Completed migrating {} collection to {}", INGRESS_FLOW_PLAN, NORMALIZE_FLOW_PLAN);
            } else {
                mongoTemplate.dropCollection(INGRESS_FLOW_PLAN);
                log.info("Dropped the {} collection that was recreated after the migration occurred", INGRESS_FLOW_PLAN);
            }
        }
    }
}
