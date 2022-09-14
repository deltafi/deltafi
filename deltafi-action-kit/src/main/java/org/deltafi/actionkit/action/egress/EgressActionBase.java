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
package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.ActionRegistrationInput;
import org.deltafi.common.types.EgressActionSchemaInput;

/**
 * Specialization class for EGRESS actions.  This class should not be used directly, but instead use one of
 * the provided egress action implementation classes.
 * @param <P> Parameter class for configuring the egress action
 * @see EgressAction
 * @see SimpleEgressAction
 * @see MultipartEgressAction
 * @see SimpleMultipartEgressAction
 */
public abstract class EgressActionBase<P extends ActionParameters> extends Action<P> {
    public EgressActionBase(Class<P> actionParametersClass) {
        super(ActionType.EGRESS, actionParametersClass);
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        EgressActionSchemaInput input = EgressActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .build();
        actionRegistrationInput.getEgressActions().add(input);
    }
}
