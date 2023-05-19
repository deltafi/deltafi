/**
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

import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.TransformFlow;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@SuppressWarnings("unused")
@Slf4j
public class TransformFlowRepoImpl extends BaseFlowRepoImpl<TransformFlow> implements TransformFlowRepoCustom {

    private static final String MAX_ERRORS = "maxErrors";
    private static final String EXPECTED_ANNOTATIONS = "expectedAnnotations";

    public TransformFlowRepoImpl(MongoTemplate mongoTemplate) {
        super(mongoTemplate, TransformFlow.class);
    }

    @Override
    public boolean updateMaxErrors(String flowName, int maxErrors) {
        Query idMatches = Query.query(Criteria.where(ID).is(flowName).and(MAX_ERRORS).ne(maxErrors));
        Update maxErrorsUpdate = Update.update(MAX_ERRORS, maxErrors);
        return 1 == mongoTemplate.updateFirst(idMatches, maxErrorsUpdate, TransformFlow.class).getModifiedCount();
    }

    @Override
    public boolean updateExpectedAnnotations(String flowName, Set<String> expectedAnnotations) {
        // sort before storing so the `ne` can be used in the query
        TreeSet<String> sortedAnnotations = expectedAnnotations != null ? new TreeSet<>(expectedAnnotations) : null;
        Query idMatches = Query.query(Criteria.where(ID).is(flowName).and(EXPECTED_ANNOTATIONS).ne(sortedAnnotations));
        Update expectedAnnotationsUpdate = Update.update(EXPECTED_ANNOTATIONS, sortedAnnotations);
        return 1 == mongoTemplate.updateFirst(idMatches, expectedAnnotationsUpdate, TransformFlow.class).getModifiedCount();
    }
}
