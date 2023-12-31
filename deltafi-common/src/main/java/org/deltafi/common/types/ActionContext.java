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
package org.deltafi.common.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.content.ContentStorageService;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionContext {
    private String did;
    private String flow;
    private String name;
    private String sourceFilename;
    private String ingressFlow;
    private String egressFlow;
    private String hostname;
    private String actionVersion;
    private OffsetDateTime startTime;
    private String systemName;
    private ContentStorageService contentStorageService;

    private CollectConfiguration collect;
    private List<String> collectedDids;

    private String memo;

    /** Create a copy of this ActionContext with a different did
     * @param newDid the new DID
     * */
    public ActionContext copy(String newDid) {
        return new ActionContext(newDid, flow, name, sourceFilename, ingressFlow, egressFlow, hostname, actionVersion,
                startTime, systemName, contentStorageService, collect, collectedDids, memo);
    }
}
