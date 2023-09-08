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
import org.deltafi.core.types.NormalizeFlow;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@SuppressWarnings("unused")
@Slf4j
public class NormalizeFlowRepoImpl extends BaseFlowRepoImpl<NormalizeFlow> implements NormalizeFlowRepoCustom {

    private static final String MAX_ERRORS = "maxErrors";
    public static final String INGRESS_FLOW = "ingressFlow";
    public static final String NORMALIZE_FLOW = "normalizeFlow";

    public NormalizeFlowRepoImpl(MongoTemplate mongoTemplate) {
        super(mongoTemplate, NormalizeFlow.class);
    }

    @PostConstruct
    public void migrateIngressFlows() {
        if (mongoTemplate.collectionExists(INGRESS_FLOW)) {
            if (!mongoTemplate.collectionExists(NORMALIZE_FLOW)) {
                log.info("Migrating {} collection to {}", INGRESS_FLOW, NORMALIZE_FLOW);
                mongoTemplate.getCollection(INGRESS_FLOW)
                        .renameCollection(new MongoNamespace(mongoTemplate.getDb().getName(), NORMALIZE_FLOW));

                Update update = new Update().rename("includeIngressFlows", "includeNormalizeFlows").rename("excludeIngressFlows", "excludeNormalizeFlows");
                mongoTemplate.updateMulti(new Query(), update, NORMALIZE_FLOW);
                log.info("Completed migrating {} collection to {}", INGRESS_FLOW, NORMALIZE_FLOW);
            } else {
                mongoTemplate.dropCollection(INGRESS_FLOW);
                log.info("Dropped the {} collection that was recreated after the migration occurred", INGRESS_FLOW);
            }
        }
    }

    @Override
    public boolean updateMaxErrors(String flowName, int maxErrors) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName).and(MAX_ERRORS).ne(maxErrors));
        Update maxErrorsUpdate = Update.update(MAX_ERRORS, maxErrors);
        return 1 == mongoTemplate.updateFirst(idMatches, maxErrorsUpdate, NormalizeFlow.class).getModifiedCount();
    }
}
