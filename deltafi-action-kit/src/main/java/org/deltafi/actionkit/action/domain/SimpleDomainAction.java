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
package org.deltafi.actionkit.action.domain;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Domain;
import org.deltafi.common.types.SourceInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Base class for a DOMAIN action that does not need to extend ActionParameters for configuration
 *
 * @see DomainAction
 */
@SuppressWarnings("unused")
public abstract class SimpleDomainAction extends DomainAction<ActionParameters> {
    public SimpleDomainAction() {
        super(ActionParameters.class);
    }

    @Override
    public final Result extractAndValidate(@NotNull ActionContext context,
                               @NotNull ActionParameters params,
                               @NotNull SourceInfo sourceInfo,
                               @NotNull Map<String, String> metadata,
                               @NotNull Map<String, Domain> domains) {
        return extractAndValidate(context, sourceInfo, metadata, domains);
    }

    /**
     * Implements the extractAndValidate execution function of a domain action
     * @param context The action configuration context object for this action execution
     * @param sourceInfo The source info for this action execution
     * @param metadata The metadata generated from the most recently executed action prior to this domain action
     * @param domains A map of domain names with their associated domain values for this action
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a FilterResult, or
     * a DomainResult
     * @see DomainResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract Result extractAndValidate(@NotNull ActionContext context,
                                  @NotNull SourceInfo sourceInfo,
                                  @NotNull Map<String, String> metadata,
                                  @NotNull Map<String, Domain> domains);
}
