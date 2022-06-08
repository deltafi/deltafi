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
package org.deltafi.actionkit.action.validate;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionType;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.ValidateActionSchemaInput;

/**
 * Specialization class for VALIDATE actions.  This class should not be used directly, but instead use one of
 * the provided validate action implementation classes.
 * @param <P> Parameter class for configuring the validate action
 * @see ValidateAction
 * @see SimpleValidateAction
 * @see MultipartValidateAction
 * @see SimpleMultipartValidateAction
 */
public abstract class ValidateActionBase<P extends ActionParameters> extends Action<P> {
    public ValidateActionBase(Class<P> actionParametersClass) {
        super(ActionType.VALIDATE, actionParametersClass);
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        ValidateActionSchemaInput input = ValidateActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .build();
        actionRegistrationInput.getValidateActions().add(input);
    }
}