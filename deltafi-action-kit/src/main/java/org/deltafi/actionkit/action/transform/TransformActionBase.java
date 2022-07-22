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
package org.deltafi.actionkit.action.transform;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.core.domain.api.types.ActionType;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.TransformActionSchemaInput;

/**
 * Specialization class for TRANSFORM actions.  This class should not be used directly, but instead use one of
 * the provided transform action implementation classes.
 * @param <P> Parameter class for configuring the transform action
 * @see TransformAction
 * @see SimpleTransformAction
 * @see MultipartTransformAction
 * @see SimpleMultipartTransformAction
 */
public abstract class TransformActionBase<P extends ActionParameters> extends Action<P> {
    public TransformActionBase(Class<P> actionParametersClass) {
        super(ActionType.TRANSFORM, actionParametersClass);
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        TransformActionSchemaInput input = TransformActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .build();
        actionRegistrationInput.getTransformActions().add(input);
    }
}
