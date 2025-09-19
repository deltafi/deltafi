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
package org.deltafi.core.action.egress;

import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;

public class StandardEgressHeaders {
    public static Map<String, String> buildMap(@NotNull ActionContext context, @NotNull EgressInput input) {
        Map<String, String> headersMap = new TreeMap<>();
        if (input.getMetadata() != null) {
            headersMap.putAll(input.getMetadata());
        }
        headersMap.put("did", context.getDid().toString());
        headersMap.put("dataSource", context.getDataSource());
        headersMap.put("flow", context.getFlowName());
        headersMap.put("originalFilename", context.getDeltaFileName());
        
        // Handle null content case
        if (input.getContent() != null) {
            headersMap.put("filename", input.getContent().getName());
        } else {
            headersMap.put("filename", "");
        }
        
        return headersMap;
    }
}
