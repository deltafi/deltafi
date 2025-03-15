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

import org.deltafi.core.types.AnalyticsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface AnalyticsRepo extends JpaRepository<AnalyticsEntity, Long>, AnalyticsRepoCustom {
    @Modifying
    @Transactional
    @Query("UPDATE AnalyticsEntity a SET a.eventGroupId = :groupId, a.updated = :updated WHERE a.id.did = :did")
    int updateEventGroupIdAndUpdatedByDid(UUID did, int groupId, OffsetDateTime updated);

    @Modifying
    @Transactional
    @Query("UPDATE AnalyticsEntity a SET a.updated = :updated WHERE a.id.did = :did")
    int updateUpdatedByDid(UUID did, OffsetDateTime updated);
}
