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
package org.deltafi.actionkit.action.delete;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.MultipartTransformAction;
import org.deltafi.actionkit.action.transform.SimpleMultipartTransformAction;
import org.deltafi.actionkit.action.transform.SimpleTransformAction;
import org.deltafi.actionkit.action.transform.TransformAction;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.DeleteActionSchemaInput;

/**
 * Specialization class for DELETE actions.
 * @see DeleteResult
 */
public abstract class DeleteActionBase extends Action<ActionParameters> {
    protected DeleteActionBase() {
        super(ActionType.DELETE, ActionParameters.class);
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        DeleteActionSchemaInput input = DeleteActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .build();
        actionRegistrationInput.getDeleteActions().add(input);
    }
}
