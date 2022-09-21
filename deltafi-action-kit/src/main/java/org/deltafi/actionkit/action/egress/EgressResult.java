/**
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
package org.deltafi.actionkit.action.egress;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.common.metrics.Metric;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEventType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

import static org.deltafi.common.metrics.MetricsUtil.BYTES_OUT;
import static org.deltafi.common.metrics.MetricsUtil.FILES_OUT;

/**
 * Specialized result class for EGRESS actions
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class EgressResult extends Result {
    private final String destination;
    private final long bytesEgressed;

    /**
     * @param context Context of the executed action
     * @param destination Location where data was egressed
     * @param bytesEgressed Number of bytes egressed in the action
     */
    @Builder
    public EgressResult(@NotNull ActionContext context, String destination, long bytesEgressed) {
        super(context);

        this.destination = destination;
        this.bytesEgressed = bytesEgressed;
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.EGRESS;
    }

    @Override
    public Collection<Metric> getCustomMetrics() {
        ArrayList<Metric> metrics = new ArrayList<>();

        metrics.add(new Metric(FILES_OUT, 1).addTag("endpoint", destination));
        metrics.add(new Metric(BYTES_OUT, bytesEgressed).addTag("endpoint", destination));

        return metrics;
    }
}
