/**
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

import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEventInput;
import org.deltafi.common.types.ActionEventType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LoadManyResult extends Result implements LoadResultType {

    private List<LoadResult> loadResults = new ArrayList<>();

    /**
     * @param context Execution context of the action
     */
    public LoadManyResult(@NotNull ActionContext context) {
        super(context);
    }

    /**
     * Add a new load result
     * @param loadResult A load result to be added to the result object
     */
    @SuppressWarnings("unused")
    public void add(LoadResult loadResult) {
        loadResults.add(loadResult);
    }

    @SuppressWarnings("unused")
    public List<LoadResult> getLoadResults() { return loadResults; }

    @Override
    public ActionEventType actionEventType() {
        return ActionEventType.LOAD_MANY;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setLoadMany(loadResults.stream().map(l -> l.toEvent().getLoad()).toList());
        return event;
    }
}
