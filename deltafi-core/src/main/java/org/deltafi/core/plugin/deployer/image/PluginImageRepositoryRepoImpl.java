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
package org.deltafi.core.plugin.deployer.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.repo.IndexUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class PluginImageRepositoryRepoImpl {
    private static final String PLUGIN_GROUP_IDS = "pluginGroupIds";

    private static final Map<String, Index> INDICES = Map.of(
            "unique_plugin_group_ids", new Index().named("unique_plugin_group_ids").on(PLUGIN_GROUP_IDS, Sort.Direction.ASC).unique());

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureAllIndices() {
        IndexOperations idxOps = mongoTemplate.indexOps(PluginImageRepository.class);
        List<IndexInfo> existingIndexes = idxOps.getIndexInfo();

        INDICES.forEach((indexName, indexDef) -> IndexUtils.updateIndices(idxOps, indexName, indexDef, existingIndexes));
    }
}
