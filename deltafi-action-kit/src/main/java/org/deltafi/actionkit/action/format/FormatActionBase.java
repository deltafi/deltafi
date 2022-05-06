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
package org.deltafi.actionkit.action.format;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.FormatActionSchemaInput;

import java.util.Collections;
import java.util.List;

/**
 * Specialization class for FORMAT actions.  This class should not be used directly, but instead use one of
 * the provided format action implementation classes.
 * @param <P> Parameter class for configuring the format action
 * @see FormatAction
 * @see SimpleFormatAction
 * @see MultipartFormatAction
 * @see SimpleMultipartFormatAction
 */
public abstract class FormatActionBase<P extends ActionParameters> extends Action<P> {
    public FormatActionBase(Class<P> actionParametersClass) {
        super(ActionType.FORMAT, actionParametersClass);
    }

    /**
     * Implement to provide a list of required domains for formatting to proceed
     * @return List of domain name strings
     */
    public abstract List<String> getRequiresDomains();

    /**
     * Implement to provide a list of required enrichments for formatting to proceed
     * @return List of enrichment name strings
     */
    public List<String> getRequiresEnrichment() {
        return Collections.emptyList();
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        FormatActionSchemaInput input = FormatActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .requiresDomains(getRequiresDomains())
                .requiresEnrichment(getRequiresEnrichment())
                .build();
        actionRegistrationInput.getFormatActions().add(input);
    }
}
