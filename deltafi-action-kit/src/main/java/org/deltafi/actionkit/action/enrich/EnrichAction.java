/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Specialization class for ENRICH actions.
 * @param <P> Parameter class for configuring the enrich action
 */
public abstract class EnrichAction<P extends ActionParameters> extends Action<P> {
    public EnrichAction(String description) {
        super(ActionType.ENRICH, description);
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

    @Override
    protected final EnrichResultType execute(@NotNull List<DeltaFileMessage> deltaFileMessages,
                                             @NotNull ActionContext context,
                                             @NotNull P params) {
        return enrich(context, params, enrichInput(deltaFileMessages.get(0), context));
    }

    private static EnrichInput enrichInput(DeltaFileMessage deltaFileMessage, ActionContext context) {
        return EnrichInput.builder()
                .content(ContentConverter.convert(deltaFileMessage.getContentList(), context.getContentStorageService()))
                .metadata(deltaFileMessage.getMetadata())
                .domains(deltaFileMessage.domainMap())
                .enrichments(deltaFileMessage.enrichmentMap())
                .build();
    }

    /**
     * Implements the enrichment execution function of an enrich action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param enrichInput Action input from the DeltaFile
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a FilterResult, or
     * a EnrichResult
     * @see EnrichResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract EnrichResultType enrich(@NotNull ActionContext context,
                                            @NotNull P params,
                                            @NotNull EnrichInput enrichInput);
}
