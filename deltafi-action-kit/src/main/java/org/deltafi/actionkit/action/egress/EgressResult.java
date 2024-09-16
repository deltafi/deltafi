/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEventType;
import org.deltafi.common.types.Metric;
import org.jetbrains.annotations.NotNull;

import static org.deltafi.common.constant.DeltaFiConstants.BYTES_OUT;
import static org.deltafi.common.constant.DeltaFiConstants.FILES_OUT;

/**
 * Specialized result class for EGRESS actions
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class EgressResult extends Result<EgressResult> implements EgressResultType {
    /**
     * @param context Context of the executed action
     * @param destination Location where data was egressed
     * @param bytesEgressed Number of bytes egressed in the action
     */
    @Builder
    public EgressResult(@NotNull ActionContext context, @NotNull String destination, long bytesEgressed) {
        super(context, ActionEventType.EGRESS);

        String endpoint = destination.replace(';', '_').replace('=', '_');
        add(new Metric(FILES_OUT, 1).addTag("endpoint", endpoint));
        add(new Metric(BYTES_OUT, bytesEgressed).addTag("endpoint", endpoint));
    }
}
