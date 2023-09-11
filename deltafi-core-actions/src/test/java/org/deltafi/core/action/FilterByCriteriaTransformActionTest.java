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
package org.deltafi.core.action;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.core.parameters.FilterBehavior;
import org.deltafi.core.parameters.FilterByCriteriaParameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.*;

class FilterByCriteriaTransformActionTest {
    private static final ActionContext CONTEXT = new ActionContext();

    FilterByCriteriaTransformAction action = new FilterByCriteriaTransformAction();

    @Test
    void testFilterBehaviorAny() {
        FilterByCriteriaParameters params = new FilterByCriteriaParameters();
        params.setFilterBehavior(FilterBehavior.ANY);
        params.setFilterExpressions(List.of("content.isEmpty()", "metadata.containsKey('someKey')"));

        ResultType result = action.transform(CONTEXT, params, createInput());

        assertFilterResult(result)
            .hasCause("Filtered because at least one of the criteria matched");
    }

    @Test
    void testFilterBehaviorAnyDoesNotFilter() {
        FilterByCriteriaParameters params = new FilterByCriteriaParameters();
        params.setFilterBehavior(FilterBehavior.ANY);
        params.setFilterExpressions(List.of("content.isEmpty()", "metadata.containsKey('someOtherKey')"));

        ResultType result = action.transform(CONTEXT, params, createInput());

        assertTransformResult(result).hasContentCount(1);
    }

    @Test
    void testFilterBehaviorAllMatch() {
        FilterByCriteriaParameters params = new FilterByCriteriaParameters();
        params.setFilterBehavior(FilterBehavior.ALL);
        params.setFilterExpressions(List.of("content.size() > 0", "metadata.containsKey('someKey')"));

        ResultType result = action.transform(CONTEXT, params, createInput());

        assertFilterResult(result)
            .hasCause("Filtered because all of the criteria matched");
    }

    @Test
    void testFilterBehaviorAllMatchDoesNotFilter() {
        FilterByCriteriaParameters params = new FilterByCriteriaParameters();
        params.setFilterBehavior(FilterBehavior.ALL);
        params.setFilterExpressions(List.of("content.size() == 0", "metadata.containsKey('someKey')"));

        ResultType result = action.transform(CONTEXT, params, createInput());

        assertTransformResult(result).hasContentCount(1);
    }

    @Test
    void testFilterBehaviorNoneMatch() {
        FilterByCriteriaParameters params = new FilterByCriteriaParameters();
        params.setFilterBehavior(FilterBehavior.NONE);
        params.setFilterExpressions(List.of("content.size() == 0", "metadata.containsKey('anotherKey')"));

        ResultType result = action.transform(CONTEXT, params, createInput());

        assertFilterResult(result)
            .hasCause("Filtered because none of the criteria matched");
    }



    @Test
    void testFilterBehaviorNoneMatchDoesNotFilter() {
        FilterByCriteriaParameters params = new FilterByCriteriaParameters();
        params.setFilterBehavior(FilterBehavior.NONE);
        params.setFilterExpressions(List.of("content.size() > 0", "metadata.containsKey('someKey')"));

        ResultType result = action.transform(CONTEXT, params, createInput());

        assertTransformResult(result).hasContentCount(1);
    }

    private TransformInput createInput() {
        Map<String, String> metadata = Map.of("someKey", "someValue");
        List<ActionContent> content = List.of(ActionContent.emptyContent(CONTEXT, "example.json", "application/json"));
        return TransformInput.builder().content(content).metadata(metadata).build();
    }
}
