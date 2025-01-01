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

import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unused")
public class FilterEgress extends EgressAction<ActionParameters> {
    public FilterEgress() {
        super("Filters on egress");
    }

    @Override
    public EgressResultType egress(@NotNull ActionContext context, @NotNull ActionParameters p, @NotNull EgressInput input) {
        return new FilterResult(context, "filtered");
    }
}
