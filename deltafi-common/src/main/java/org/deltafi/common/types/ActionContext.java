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
package org.deltafi.common.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.content.ActionContentStorageService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionContext {
    private UUID did;
    private String deltaFileName;
    private String dataSource;
    private String flowName;
    private UUID flowId;
    private String actionName;
    private String actionVersion;
    private String hostname;
    private OffsetDateTime startTime;
    private String systemName;
    private ActionContentStorageService contentStorageService;

    private JoinConfiguration join;
    private List<UUID> joinedDids;

    private String memo;

    /**
     * Create a copy of this ActionContext with a new random did
     * @return the copied context
     */
    public ActionContext childContext() {
        return new ActionContext(UUID.randomUUID(), deltaFileName, dataSource, flowName, flowId, actionName,
                actionVersion, hostname, startTime, systemName, contentStorageService, join, joinedDids, memo);
    }
}
