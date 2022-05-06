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
package org.deltafi.actionkit.action.enrich;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.ActionType;
import org.deltafi.core.domain.generated.types.ActionRegistrationInput;
import org.deltafi.core.domain.generated.types.EnrichActionSchemaInput;

import java.util.Collections;
import java.util.List;

/**
 * Specialization class for ENRICH actions.  This class should not be used directly, but instead use one of
 * the provided enrich action implementation classes.
 * @param <P> Parameter class for configuring the enrich action
 * @see EnrichAction
 * @see SimpleEnrichAction
 * @see MultipartEnrichAction
 * @see SimpleMultipartEnrichAction
 */
public abstract class EnrichActionBase<P extends ActionParameters> extends Action<P> {
    public EnrichActionBase(Class<P> actionParametersClass) {
        super(ActionType.ENRICH, actionParametersClass);
    }

    /**
     * Implement to provide a list of required domains for enrichment to proceed
     * @return List of domain name strings
     */
    public abstract List<String> getRequiresDomains();

    /**
     * Implement to provide a list of required enrichments for enrichment to proceed
     * @return List of enrichment name strings
     */
    public List<String> getRequiresEnrichment() {
        return Collections.emptyList();
    }

    @Override
    public void registerSchema(ActionRegistrationInput actionRegistrationInput) {
        EnrichActionSchemaInput input = EnrichActionSchemaInput.newBuilder()
                .id(getClassCanonicalName())
                .paramClass(getParamClass())
                .schema(getDefinition())
                .requiresDomains(getRequiresDomains())
                .requiresEnrichment(getRequiresEnrichment())
                .build();
        actionRegistrationInput.getEnrichActions().add(input);
    }
}
