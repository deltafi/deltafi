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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.ResumePolicy;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
@RequiredArgsConstructor
@Slf4j
public class ResumePolicyRepoImpl implements ResumePolicyRepoCustom {

    private static final String ERROR_SUBSTRING = "errorSubstring";
    private static final String FLOW = "flow";
    private static final String ACTION = "action";
    private static final String ACTION_TYPE = "actionType";

    private static final Map<String, Index> INDICES = Map.of(
            "unique_criteria", new Index().named("unique_criteria")
                    .on(ERROR_SUBSTRING, Sort.Direction.ASC)
                    .on(FLOW, Sort.Direction.ASC)
                    .on(ACTION, Sort.Direction.ASC)
                    .on(ACTION_TYPE, Sort.Direction.ASC)
                    .unique());

    private final MongoTemplate mongoTemplate;

    public void ensureAllIndices() {
        IndexOperations idxOps = mongoTemplate.indexOps(ResumePolicy.class);
        List<IndexInfo> existingIndexes = idxOps.getIndexInfo();

        INDICES.forEach((indexName, indexDef) -> IndexUtils.updateIndices(idxOps, indexName, indexDef, existingIndexes));
    }
}
