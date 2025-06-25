/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import org.deltafi.core.types.RestDataSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;

public interface RestDataSourceRepo extends FlowRepo, RestDataSourceRepoCustom {
    @Transactional
    @Query(value = "UPDATE flows " +
            "SET max_errors = :maxErrors " +
            "WHERE name = :flowName AND type = 'REST_DATA_SOURCE' " +
            "RETURNING *", nativeQuery = true)
    RestDataSource updateMaxErrors(String flowName, int maxErrors);

    @Transactional
    @Query(value = "UPDATE flows SET rate_limit = CAST(:rateLimitJson AS jsonb) WHERE name = :flowName AND type = 'REST_DATA_SOURCE' RETURNING *", nativeQuery = true)
    RestDataSource updateRateLimit(String flowName, String rateLimitJson);

    @Transactional
    @Query(value = "UPDATE flows SET rate_limit = NULL WHERE name = :flowName AND type = 'REST_DATA_SOURCE' RETURNING *", nativeQuery = true)
    RestDataSource removeRateLimit(String flowName);
}
