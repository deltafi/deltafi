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
// ABOUTME: Unit tests for DeltaFileFlow class.
// ABOUTME: Tests content resolution including contentAtOrBefore() for errored actions.
package org.deltafi.core.types;

import org.deltafi.common.types.ActionState;
import org.deltafi.common.types.Content;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaFileFlowTest {

    @Test
    void contentAtOrBefore_completeAction_returnsActionContent() {
        Content actionContent = content("output.txt");
        DeltaFileFlow flow = flowWithActions(completeAction(actionContent));

        List<Content> result = flow.contentAtOrBefore(0);

        assertThat(result).containsExactly(actionContent);
    }

    @Test
    void contentAtOrBefore_erroredSecondAction_returnsPreviousCompleteActionContent() {
        Content action0Content = content("action0.txt");
        DeltaFileFlow flow = flowWithActions(
                completeAction(action0Content),
                erroredAction()
        );

        List<Content> result = flow.contentAtOrBefore(1);

        assertThat(result).containsExactly(action0Content);
    }

    @Test
    void contentAtOrBefore_chainOfErroredActions_returnsLastCompleteActionContent() {
        Content action0Content = content("action0.txt");
        DeltaFileFlow flow = flowWithActions(
                completeAction(action0Content),
                erroredAction(),
                erroredAction()
        );

        List<Content> result = flow.contentAtOrBefore(2);

        assertThat(result).containsExactly(action0Content);
    }

    @Test
    void contentAtOrBefore_completeActionInMiddle_returnsCorrectContent() {
        Content action0Content = content("action0.txt");
        Content action1Content = content("action1.txt");
        DeltaFileFlow flow = flowWithActions(
                completeAction(action0Content),
                completeAction(action1Content),
                erroredAction()
        );

        // Asking for content at action index 2 (errored) should return action 1's content
        List<Content> result = flow.contentAtOrBefore(2);
        assertThat(result).containsExactly(action1Content);

        // Asking for content at action index 1 (complete) should return its own content
        result = flow.contentAtOrBefore(1);
        assertThat(result).containsExactly(action1Content);
    }

    @Test
    void contentAtOrBefore_completeActionWithEmptyContent_returnsEmptyList() {
        DeltaFileFlow flow = flowWithActions(completeActionWithEmptyContent());

        List<Content> result = flow.contentAtOrBefore(0);

        assertThat(result).isEmpty();
    }

    @Test
    void contentAtOrBefore_erroredActionAfterCompleteWithEmptyContent_returnsEmptyList() {
        DeltaFileFlow flow = flowWithActions(
                completeActionWithEmptyContent(),
                erroredAction()
        );

        List<Content> result = flow.contentAtOrBefore(1);

        // The previous complete action legitimately produced no content
        assertThat(result).isEmpty();
    }

    @Test
    void contentAtOrBefore_negativeIndex_returnsEmptyList() {
        DeltaFileFlow flow = flowWithActions(completeAction(content("action.txt")));

        List<Content> result = flow.contentAtOrBefore(-1);

        assertThat(result).isEmpty();
    }

    @Test
    void contentAtOrBefore_indexOutOfBounds_returnsEmptyList() {
        DeltaFileFlow flow = flowWithActions(completeAction(content("action.txt")));

        List<Content> result = flow.contentAtOrBefore(5);

        assertThat(result).isEmpty();
    }

    @Test
    void contentAtOrBefore_completeActionWithNullContent_returnsEmptyList() {
        DeltaFileFlow flow = flowWithActions(completeActionWithNullContent());

        List<Content> result = flow.contentAtOrBefore(0);

        assertThat(result).isEmpty();
    }

    @Test
    void contentAtOrBefore_filteredAction_traversesBack() {
        Content action0Content = content("action0.txt");
        DeltaFileFlow flow = flowWithActions(
                completeAction(action0Content),
                filteredAction()
        );

        List<Content> result = flow.contentAtOrBefore(1);

        assertThat(result).containsExactly(action0Content);
    }

    @Test
    void contentAtOrBefore_retriedAction_traversesBack() {
        Content action0Content = content("action0.txt");
        DeltaFileFlow flow = flowWithActions(
                completeAction(action0Content),
                retriedAction()
        );

        List<Content> result = flow.contentAtOrBefore(1);

        assertThat(result).containsExactly(action0Content);
    }

    // Helper methods

    private DeltaFileFlow flowWithActions(Action... actions) {
        return DeltaFileFlow.builder()
                .actions(List.of(actions))
                .build();
    }

    private Content content(String name) {
        Content c = new Content();
        c.setName(name);
        c.setMediaType("text/plain");
        c.setSize(100L);
        return c;
    }

    private Action completeAction(Content... contents) {
        return Action.builder()
                .state(ActionState.COMPLETE)
                .content(List.of(contents))
                .build();
    }

    private Action completeActionWithEmptyContent() {
        return Action.builder()
                .state(ActionState.COMPLETE)
                .content(List.of())
                .build();
    }

    private Action completeActionWithNullContent() {
        return Action.builder()
                .state(ActionState.COMPLETE)
                .content(null)
                .build();
    }

    private Action erroredAction() {
        return Action.builder()
                .state(ActionState.ERROR)
                .content(List.of())
                .build();
    }

    private Action filteredAction() {
        return Action.builder()
                .state(ActionState.FILTERED)
                .content(List.of())
                .build();
    }

    private Action retriedAction() {
        return Action.builder()
                .state(ActionState.RETRIED)
                .content(List.of())
                .build();
    }
}
