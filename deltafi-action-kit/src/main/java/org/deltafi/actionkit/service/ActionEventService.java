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
package org.deltafi.actionkit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.generated.types.ActionEventInput;

import java.util.Collections;
import java.util.List;

public interface ActionEventService {
    default void putAction(String actionClassName, ActionInput actionEventInput) {}
    ActionInput getAction(String actionClassName) throws JsonProcessingException, InterruptedException;
    void submitResult(Result result) throws JsonProcessingException;
    default List<ActionEventInput> getResults(String actionClassName) { return Collections.emptyList(); }
}