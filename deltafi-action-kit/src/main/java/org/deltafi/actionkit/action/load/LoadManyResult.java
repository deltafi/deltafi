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

    private final List<ChildLoadResult> loadResults = new ArrayList<>();

    /**
     * @param context Execution context of the action
     */
    public LoadManyResult(@NotNull ActionContext context) {
        super(context);
    }

    /**
     * Add a load result to the list of results
     * @param loadResult A load result to be added to the result object
     */
    public void add(LoadResult loadResult) {
        loadResults.add(new ChildLoadResult(loadResult));
    }

    /**
     * Add a load result to the list of results
     * @param loadResult A load result to be added to the result object
     */
    public void add(ChildLoadResult loadResult) {
        loadResults.add(loadResult);
    }

    /**
     * Get the list of LoadResults held in the child results
     * @return list of LoadResults
     */
    public List<LoadResult> getLoadResults() {
        return loadResults.stream().map(ChildLoadResult::getLoadResult).toList();
    }

    /**
     * Get the list of child results
     * @return list of ChildLoadResults
     */
    public List<ChildLoadResult> getChildLoadResults() {
        return loadResults;
    }

    @Override
    public ActionEventType actionEventType() {
        return ActionEventType.LOAD_MANY;
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setLoadMany(loadResults.stream().map(ChildLoadResult::toEvent).toList());
        return event;
    }
}
