/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

package org.deltafi.common.metrics;

import org.deltafi.common.types.ActionType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class MetricsUtil {

    // Metric names
    static final public String FILES_ERRORED = "files_errored";
    static final public String FILES_DROPPED = "files_dropped";
    static final public String FILES_COMPLETED = "files_completed";
    static final public String FILES_FILTERED = "files_filtered";
    static final public String FILES_IN = "files_in";
    static final public String FILES_OUT = "files_out";
    static final public String BYTES_IN = "bytes_in";
    static final public String BYTES_OUT = "bytes_out";

    // Tag names
    static final public String ACTION = "action";
    static final public String SOURCE = "source";
    static final public String INGRESS_FLOW = "ingressFlow";
    static final public String EGRESS_FLOW = "egressFlow";

    /** Metrics are confusingly named! Assign the correct keys.
     *
     * @param actionType lowercased, becomes "action" tagged
     * @param actionName becomes "source" tagged
     * @param ingressFlow "ingressFlow" tagged
     * @param egressFlow "egressFlow" tagged
     * @return the map
     */
    static public Map<String, String> tagsFor(@NotNull ActionType actionType, @NotNull String actionName, String ingressFlow, String egressFlow) {
        Map<String, String> tags = new HashMap<>();

        tags.put(ACTION, actionType.name().toLowerCase());
        tags.put(SOURCE, actionName);

        if (ingressFlow != null) {
            tags.put(INGRESS_FLOW, ingressFlow);
        }

        if (egressFlow != null) {
            tags.put(EGRESS_FLOW, egressFlow);
        }

        return tags;
    }
}
