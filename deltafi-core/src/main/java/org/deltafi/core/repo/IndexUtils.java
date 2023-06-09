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

import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.util.MongoDbErrorCodes;
import org.springframework.util.ObjectUtils;

import java.util.List;

@SuppressWarnings("unused")
@Slf4j
public class IndexUtils {

    public static void updateIndices(IndexOperations idxOps, String indexName, Index index, List<IndexInfo> existingIndexes) {
        try {
            log.debug("Ensure index {}", indexName);
            idxOps.ensureIndex(index);
        } catch (UncategorizedMongoDbException ex) {
            if (ex.getCause() instanceof MongoException && MongoDbErrorCodes.isDataIntegrityViolationCode(((MongoException) ex.getCause()).getCode()) && indexExists(indexName, existingIndexes)) {
                log.info("An old version of index {} exists, attempting to recreate it", indexName);
                recreateIndex(idxOps, indexName, index);
            } else {
                log.error("Failed to ensure index: {}", index, ex);
            }
        }
    }

    private static void recreateIndex(IndexOperations idxOps, String indexName, Index index) {
        try {
            idxOps.dropIndex(indexName);
            idxOps.ensureIndex(index);
        } catch (UncategorizedMongoDbException ex) {
            log.error("Failed to recreate index: {}", index, ex);
        }
    }

    private static boolean indexExists(String name, List<IndexInfo> existingIndexes) {
        return existingIndexes.stream()
                .anyMatch(indexInfo -> ObjectUtils.nullSafeEquals(name, indexInfo.getName()));
    }
}
