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

import org.deltafi.core.types.IngressFlow;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@SuppressWarnings("unused")
public class IngressFlowRepoImpl extends BaseFlowRepoImpl<IngressFlow> implements IngressFlowRepoCustom {

    private static final String MAX_ERRORS = "maxErrors";

    public IngressFlowRepoImpl(MongoTemplate mongoTemplate) {
        super(mongoTemplate, IngressFlow.class);
    }

    @Override
    public boolean updateMaxErrors(String flowName, int maxErrors) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName).and(MAX_ERRORS).ne(maxErrors));
        Update maxErrorsUpdate = Update.update(MAX_ERRORS, maxErrors);
        return 1 == mongoTemplate.updateFirst(idMatches, maxErrorsUpdate, IngressFlow.class).getModifiedCount();
    }
}
