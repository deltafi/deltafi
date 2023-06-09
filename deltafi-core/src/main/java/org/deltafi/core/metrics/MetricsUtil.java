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
package org.deltafi.core.metrics;

import org.deltafi.common.constant.DeltaFiConstants;
import org.deltafi.common.types.ActionEventType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class MetricsUtil {

    /** Helper for generating a default tag list for metrics
     *
     * @param actionType lowercased, becomes "action" tagged
     * @param actionName becomes "source" tagged
     * @param ingressFlow "ingressFlow" tagged
     * @param egressFlow "egressFlow" tagged
     * @return the map
     */
    static public @NotNull Map<String, String> tagsFor(@NotNull ActionEventType actionType, @NotNull String actionName, String ingressFlow, String egressFlow) {
        return tagsFor(actionType.name().toLowerCase(), actionName, ingressFlow, egressFlow);
    }

    /** Helper for generating a default tag list for metrics
     *
     * @param actionType lowercased, becomes "action" tagged
     * @param actionName becomes "source" tagged
     * @param ingressFlow "ingressFlow" tagged
     * @param egressFlow "egressFlow" tagged
     * @return the map
     */
    static public @NotNull Map<String, String> tagsFor(@NotNull String actionType, @NotNull String actionName, String ingressFlow, String egressFlow) {
        Map<String, String> tags = new HashMap<>();

        tags.put(DeltaFiConstants.ACTION, actionType.toLowerCase());
        tags.put(DeltaFiConstants.SOURCE, actionName);

        if (ingressFlow != null) {
            tags.put(DeltaFiConstants.INGRESS_FLOW, ingressFlow);
        }

        if (egressFlow != null) {
            tags.put(DeltaFiConstants.EGRESS_FLOW, egressFlow);
        }

        return tags;
    }
}
