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
package org.deltafi.core.services;

import lombok.Builder;
import lombok.Data;
import org.deltafi.common.types.Action;
import org.deltafi.common.types.ActionConfiguration;
import org.deltafi.common.types.DeltaFile;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
class ActionInvocation {
    private ActionConfiguration actionConfiguration;
    private String flow;
    private DeltaFile deltaFile;
    private List<DeltaFile> deltaFiles;
    private String egressFlow;
    private String returnAddress;
    private OffsetDateTime actionCreated;
    private Action action;
}
