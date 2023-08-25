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

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ReinjectEvent;
import org.deltafi.core.parameters.NoMatchBehavior;
import org.deltafi.core.parameters.RouteByCriteriaParameters;
import org.deltafi.test.action.transform.TransformActionTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.*;

class RouteByCriteriaTransformActionTest extends TransformActionTest {

    RouteByCriteriaTransformAction action = new RouteByCriteriaTransformAction();

    private static final String SOURCE_FLOW = "sourceFlow";

    @Test
    void testRouteExpressionMatch() {
        RouteByCriteriaParameters params = new RouteByCriteriaParameters();
        params.setRoutingExpressions(Map.of("metadata.containsKey('someKey')", "flow1"));

        TransformInput input = createInput();
        ResultType result = action.transform(context(), params, input);

        assertReinjectResult(result)
                .hasReinjectEventSize(1)
                .hasReinjectEventMatching(reinjectEvent -> reinjectMatcher(reinjectEvent, "routed-from-" + SOURCE_FLOW, "flow1", input.getMetadata()));
    }

    @Test
    void testRouteSecondExpressionMatch() {
        RouteByCriteriaParameters params = new RouteByCriteriaParameters();
        params.setRoutingExpressions(Map.of("metadata.containsKey('anotherKey')", "flow1"));
        params.setRoutingExpressions(Map.of("metadata.containsKey('someKey')", "flow2"));

        TransformInput input = createInput();
        ResultType result = action.transform(context(), params, input);

        assertReinjectResult(result)
                .hasReinjectEventSize(1)
                .hasReinjectEventMatching(reinjectEvent -> reinjectMatcher(reinjectEvent, "routed-from-" + SOURCE_FLOW, "flow2", input.getMetadata()));
    }

    @Test
    void testNoRouteExpressionMatchWithErrorBehavior() {
        RouteByCriteriaParameters params = new RouteByCriteriaParameters();
        params.setRoutingExpressions(Map.of("content.size() > 1", "flow1"));
        params.setNoMatchBehavior(NoMatchBehavior.ERROR);

        ResultType result = action.transform(context(), params, createInput());

        assertErrorResult(result)
                .hasCause("No routing expressions matched");
    }

    @Test
    void testNoRouteExpressionMatchWithFilterBehavior() {
        RouteByCriteriaParameters params = new RouteByCriteriaParameters();
        params.setRoutingExpressions(Map.of("content.size() > 1", "flow1"));
        params.setNoMatchBehavior(NoMatchBehavior.FILTER);

        ResultType result = action.transform(context(), params, createInput());

        assertFilterResult(result)
                .hasCause("No routing expressions matched");
    }

    @Test
    void testNoRouteExpressionMatchWithPassThroughBehavior() {
        RouteByCriteriaParameters params = new RouteByCriteriaParameters();
        params.setRoutingExpressions(Map.of("content.size() > 1", "flow1"));
        params.setNoMatchBehavior(NoMatchBehavior.PASSTHROUGH);

        ResultType result = action.transform(context(), params, createInput());

        assertTransformResult(result)
                .hasContentCount(1);
    }

    @Test
    void testEmptyParameters() {
        RouteByCriteriaParameters params = new RouteByCriteriaParameters();
        params.setNoMatchBehavior(NoMatchBehavior.FILTER);

        ResultType result = action.transform(context(), params, createInput());

        assertFilterResult(result);
    }

    private TransformInput createInput() {
        Map<String, String> metadata = Map.of("someKey", "someValue");
        List<ActionContent> content = List.of(ActionContent.emptyContent(context(), "example.json", "application/json"));
        return TransformInput.builder().content(content).metadata(metadata).build();
    }

    private boolean reinjectMatcher(ReinjectEvent event, String filename, String flow, Map<String, String> metadata) {
        Assertions.assertThat(event.getFilename()).isEqualTo(filename);
        Assertions.assertThat(event.getFlow()).isEqualTo(flow);
        Assertions.assertThat(event.getAnnotations()).isEmpty();
        Assertions.assertThat(event.getMetadata()).isEqualTo(metadata);
        Assertions.assertThat(event.getDeleteMetadataKeys()).isEmpty();
        Assertions.assertThat(event.getContent()).hasSize(1);
        return true;
    }

    @Override
    protected ActionContext context() {
        ActionContext context = super.context();
        context.setFlow(SOURCE_FLOW);
        return context;
    }
}
