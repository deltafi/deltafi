/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.actionkit.action.filter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Metric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exception that is mapped to an FilterResult
 */
@Builder(builderMethodName = "filterCause")
@AllArgsConstructor
@SuppressWarnings("unused")
public class FilterResultException extends RuntimeException {
    private final String filterCause;
    private final String filterContext;
    @Singular
    private final Map<String, String> annotations;
    @Singular
    private final List<Metric> metrics;

    /**
     * Create a new FilterResultException
     * @param filterCause Message explaining the filter result
     */
    public FilterResultException(String filterCause) {
        this(filterCause, null);
    }

    /**
     * Create a new FilterResultException
     * @param filterCause Message explaining the error result
     * @param filterContext Additional details about the error
     */
    public FilterResultException(String filterCause, String filterContext) {
        super(filterCause);
        this.filterCause = filterCause;
        this.filterContext = filterContext;
        this.annotations = new HashMap<>();
        this.metrics = new ArrayList<>();
    }

    // make javadoc generator happy
    public static class FilterResultExceptionBuilder {}

    /**
     * Create a new FilterResultExceptionBuilder with the required filterCause field populated
     * @param filterCause Message explaining the filter result
     * @return new ErrorResultExceptionBuilder
     */
    public static FilterResultExceptionBuilder filterCause(String filterCause) {
        return new FilterResultExceptionBuilder().filterCause(filterCause);
    }

    public FilterResult toFilterResult(ActionContext context) {
        FilterResult filterResult = new FilterResult(context, filterCause, filterContext);
        if (annotations != null) {
            filterResult.addAnnotations(annotations);
        }
        if (metrics != null) {
            metrics.forEach(filterResult::add);
        }
        return filterResult;
    }
}
