/**
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
package org.deltafi.actionkit.action.format;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Specialization class for FORMAT actions.
 * @param <P> Parameter class for configuring the format action
 */
public abstract class FormatAction<P extends ActionParameters> extends Action<P> {
    public FormatAction(String description) {
        super(ActionType.FORMAT, description);
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
    protected final FormatResultType execute(@NotNull DeltaFileMessage deltaFileMessage,
                                             @NotNull ActionContext context,
                                             @NotNull P params) {
        return format(context, params, formatInput(deltaFileMessage, context));
    }

    private static FormatInput formatInput(DeltaFileMessage deltaFileMessage, ActionContext context) {
        return FormatInput.builder()
                .sourceFilename(deltaFileMessage.getSourceFilename())
                .ingressFlow(deltaFileMessage.getIngressFlow())
                .contentList(deltaFileMessage.getContentList())
                .metadata(deltaFileMessage.getMetadata())
                .domains(deltaFileMessage.domainMap())
                .enrichment(deltaFileMessage.enrichmentMap())
                .actionContext(context)
                .build();
    }

    /**
     * Implements the format execution function of a format action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param formatInput Action input from the DeltaFile
     * @return A result object containing results for the action execution.
     *         The result can be an ErrorResult, FilterResult, FormatResult, or FormatManyResult
     * @see FormatResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     * @see FormatManyResult
     */
    public abstract FormatResultType format(@NotNull ActionContext context,
                                            @NotNull P params,
                                            @NotNull FormatInput formatInput);
}
