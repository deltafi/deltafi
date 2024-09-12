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

import org.deltafi.common.types.ActionState;
import org.deltafi.core.types.Action;
import org.deltafi.core.types.ColdQueuedActionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActionRepo extends JpaRepository<Action, UUID>, ActionRepoCustom {
    long countByStateAndErrorAcknowledgedIsNull(ActionState state);

    @Query("SELECT new org.deltafi.core.types.ColdQueuedActionSummary(a.name, a.type, COUNT(*)) " +
            "FROM Action a " +
            "WHERE a.state = 'COLD_QUEUED' " +
            "GROUP BY a.name, a.type")
    List<ColdQueuedActionSummary> coldQueuedActionsSummary();
}
