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
import org.deltafi.common.types.ActionInput;

/**
 * Service interface for publishing and retrieving action events
 */
public interface ActionEventService {
    /**
     * Request an ActionInput object from the ActionEvent queue for the specified action
     * @param actionClassName Name of action for Action event request
     * @return next Action on the queue for the given action name
     * @throws JsonProcessingException if the incoming event cannot be serialized
     */
    ActionInput getAction(String actionClassName) throws JsonProcessingException;

    /**
     * Submit a result object for action processing
     * @param result Result object to be posted to the action queue
     * @throws JsonProcessingException if the outgoing event cannot be deserialized
     */
    void submitResult(Result result) throws JsonProcessingException;
}