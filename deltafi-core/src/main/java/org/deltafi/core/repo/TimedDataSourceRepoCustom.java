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

import org.deltafi.common.types.IngressStatus;
import org.deltafi.core.types.TimedDataSource;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TimedDataSourceRepoCustom extends FlowRepoCustom<TimedDataSource> {
    boolean updateCronSchedule(String flowName, String cronSchedule, OffsetDateTime nextRun);

    boolean updateLastRun(String flowName, OffsetDateTime lastRun, UUID currentDid);

    boolean completeExecution(String flowName, UUID currentDid, String memo, boolean executeImmediate,
                              IngressStatus status, String statusMessage, OffsetDateTime nextRun);

    boolean updateMemo(String flowName, String memo);
}
