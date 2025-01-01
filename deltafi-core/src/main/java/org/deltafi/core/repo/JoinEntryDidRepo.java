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

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.deltafi.core.types.JoinEntryDid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JoinEntryDidRepo extends JpaRepository<JoinEntryDid, UUID> {
    List<JoinEntryDid> findByJoinEntryId(UUID joinEntryId);

    @Modifying
    @Transactional
    void deleteByJoinEntryId(UUID joinEntryId);

    @Modifying
    @Transactional
    void deleteAllByJoinEntryIdAndDidIn(UUID joinEntryId, List<UUID> dids);

    @Modifying
    @Query(value = "UPDATE JoinEntryDid j SET j.orphan = true, j.errorReason = :errorReason, j.actionName = :actionName WHERE j.id = :joinEntryId")
    void setOrphanState(UUID joinEntryId, String errorReason, String actionName);

    List<JoinEntryDid> findByOrphanIsTrue(Limit limit);
}
