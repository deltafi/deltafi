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

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Base class for a DOMAIN action that needs to extend ActionParameters for configuration
 *
 * @see SimpleDomainAction
 */
public abstract class DomainAction<P extends ActionParameters> extends DomainActionBase<P> {
    public DomainAction(Class<P> actionParametersClass, String description) {
        super(actionParametersClass, description);
    }

    @Override
    protected final ResultType execute(@NotNull DeltaFile deltaFile, @NotNull ActionContext context, @NotNull P params) {
        return extractAndValidate(context,
                params,
                deltaFile.getSourceInfo(),
                deltaFile.getLastProtocolLayerMetadataAsMap(),
                deltaFile.domainMap());
    }

    /**
     * Implements the extractAndValidate execution function of a domain action
     * @param context The action configuration context object for this action execution
     * @param params The parameter class that configures the behavior of this action execution
     * @param sourceInfo The source info for this action execution
     * @param metadata The metadata generated from the most recently executed action prior to this domain action
     * @param domains A map of domain names with their associated domain values for this action
     * @return A result object containing results for the action execution.  The result can be an ErrorResult, a FilterResult, or
     * a DomainResult
     * @see DomainResult
     * @see org.deltafi.actionkit.action.error.ErrorResult
     * @see org.deltafi.actionkit.action.filter.FilterResult
     */
    public abstract DomainResultType extractAndValidate(@NotNull ActionContext context,
                                                        @NotNull P params,
                                                        @NotNull SourceInfo sourceInfo,
                                                        @NotNull Map<String, String> metadata,
                                                        @NotNull Map<String, Domain> domains);
}
