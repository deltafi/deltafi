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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.egress.SimpleEgressAction;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.FormattedData;
import org.deltafi.common.types.SourceInfo;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
@Action
public class FilterEgressAction extends SimpleEgressAction {
    @Override
    public Result egress(@NotNull ActionContext context, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        return new FilterResult(context, "filtered");
    }
}
