/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Interface for action results that can be converted to ActionEvents.
// ABOUTME: All action result types implement this interface.
package org.deltafi.actionkit.action;

import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionEvent;

/**
 * Interface for action results
 */
public interface ResultType {
    /**
     * Convert this result to an ActionEvent for dispatching back to the core.
     * @return the ActionEvent representation of this result
     */
    ActionEvent toEvent();

    /**
     * Get the action context associated with this result.
     * @return the action execution context
     */
    ActionContext getContext();
}
