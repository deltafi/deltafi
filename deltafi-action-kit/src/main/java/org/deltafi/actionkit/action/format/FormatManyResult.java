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
package org.deltafi.actionkit.action.format;

import lombok.EqualsAndHashCode;
import org.deltafi.actionkit.action.Result;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;
import org.deltafi.common.types.ActionEventType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Specialized result class for FORMAT actions that generate multiple formatted results to be independently egressed.
 * Each FormatResult added to the FormatManyResult will be processed by applicable egress actions.
 */
@EqualsAndHashCode(callSuper = true)
public class FormatManyResult extends Result<FormatManyResult> implements FormatResultType {
    private final List<ChildFormatResult> childFormatResults = new ArrayList<>();

    /**
     * @param context Execution context of the action
     */
    public FormatManyResult(@NotNull ActionContext context) {
        super(context, ActionEventType.FORMAT_MANY);
    }

    /**
     * Add a new format result
     * @param formatResult A format result to be added to the result object
     */
    @SuppressWarnings("unused")
    public void add(@NotNull FormatResult formatResult) {
        childFormatResults.add(new ChildFormatResult(formatResult));
    }

    public List<FormatResult> getChildFormatResults() {
        return childFormatResults.stream().map(ChildFormatResult::getFormatResult).toList();
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setFormatMany(childFormatResults.stream().map(ChildFormatResult::toEvent).toList());
        return event;
    }
}
