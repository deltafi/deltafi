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
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.ActionType;

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
    public EnrichActionBase(Class<P> actionParametersClass, String description) {
        super(ActionType.ENRICH, actionParametersClass, description);
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
    public List<String> getRequiresEnrichments() {
        return Collections.emptyList();
    }

    @Override
    public ActionDescriptor buildActionDescriptor() {
        ActionDescriptor actionDescriptor = super.buildActionDescriptor();
        actionDescriptor.setRequiresDomains(getRequiresDomains());
        actionDescriptor.setRequiresEnrichments(getRequiresEnrichments());
        return actionDescriptor;
    }
}
