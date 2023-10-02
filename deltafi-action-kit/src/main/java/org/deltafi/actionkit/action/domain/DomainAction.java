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
package org.deltafi.actionkit.action.domain;

import org.deltafi.actionkit.action.Action;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionDescriptor;
import org.deltafi.common.types.ActionType;
import org.deltafi.common.types.DeltaFileMessage;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Specialization class for DOMAIN actions.
 * @param <P> Parameter class for configuring the domain action
 */
public abstract class DomainAction<P extends ActionParameters> extends Action<DomainInput, P, DomainResultType> {
    public DomainAction(@NotNull String description) {
        super(ActionType.DOMAIN, description);
    }

    /**
     * Implement to provide a list of required domains for the domain action to proceed
     * @return List of domain name strings
     */
    public abstract List<String> getRequiresDomains();

    @Override
    public final ActionDescriptor buildActionDescriptor() {
        ActionDescriptor actionDescriptor = super.buildActionDescriptor();
        actionDescriptor.setRequiresDomains(getRequiresDomains());
        return actionDescriptor;
    }

    @Override
    protected DomainInput buildInput(@NotNull ActionContext context, @NotNull DeltaFileMessage deltaFileMessage) {
        return DomainInput.builder()
                .content(ContentConverter.convert(deltaFileMessage.getContentList(), context.getContentStorageService()))
                .metadata(deltaFileMessage.getMetadata())
                .domains(deltaFileMessage.domainMap())
                .build();
    }

    @Override
    protected final DomainResultType execute(@NotNull ActionContext context, @NotNull DomainInput input, @NotNull P params) {
        return extractAndValidate(context, params, input);
    }

    /**
     * Implements the extractAndValidate execution function of a domain action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param domainInput Action input from the DeltaFile
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a
     * FilterResult, or a DomainResult
     * @see DomainResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract DomainResultType extractAndValidate(@NotNull ActionContext context, @NotNull P params, @NotNull DomainInput domainInput);
}
