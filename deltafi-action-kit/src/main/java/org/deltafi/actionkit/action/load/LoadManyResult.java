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
package org.deltafi.actionkit.action.load;

import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionEventType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
public class LoadManyResult extends Result<LoadManyResult> implements LoadResultType {
    private final List<ChildLoadResult> childLoadResults = new ArrayList<>();

    public LoadManyResult(@NotNull ActionContext context) {
        super(context, ActionEventType.LOAD_MANY);
    }

    /**
     * Add a load result to the list of results
     * @param loadResult A load result to be added to the result object
     */
    public void add(LoadResult loadResult) {
        childLoadResults.add(new ChildLoadResult(loadResult));
    }

    /**
     * Add a child load result to the list of results
     * @param childLoadResult A child load result to be added to the result object
     */
    public void add(ChildLoadResult childLoadResult) {
        childLoadResults.add(childLoadResult);
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setLoadMany(childLoadResults.stream().map(ChildLoadResult::toEvent).toList());
        return event;
    }
}
