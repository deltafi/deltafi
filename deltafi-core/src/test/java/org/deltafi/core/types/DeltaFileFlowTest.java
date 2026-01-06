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
import org.deltafi.common.types.FlowType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaFileFlowTest {

    @Test
    void contentAtOrBefore_completeAction_returnsActionContent() {
        Content actionContent = content("output.txt");
        DeltaFileFlow flow = flowWithActions(completeAction(actionContent));

        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(0);
        List<Content> result = contentAndMetadata.contentList();

        assertThat(result).containsExactly(actionContent);
    }

    @Test
    void contentAtOrBefore_erroredSecondAction_returnsPreviousCompleteActionContent() {
        Content action0Content = content("action0.txt");
        DeltaFileFlow flow = flowWithActions(
                completeAction(action0Content),
                erroredAction()
        );

        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(1);
        List<Content> result = contentAndMetadata.contentList();

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

        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(2);
        List<Content> result = contentAndMetadata.contentList();

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
        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(2);
        List<Content> result = contentAndMetadata.contentList();
        assertThat(result).containsExactly(action1Content);

        // Asking for content at action index 1 (complete) should return its own content
        contentAndMetadata = flow.contentAtOrBefore(1);
        result = contentAndMetadata.contentList();
        assertThat(result).containsExactly(action1Content);
    }

    @Test
    void contentAtOrBefore_completeActionWithEmptyContent_returnsEmptyList() {
        DeltaFileFlow flow = flowWithActions(completeActionWithEmptyContent());

        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(0);
        List<Content> result = contentAndMetadata.contentList();

        assertThat(result).isEmpty();
    }

    @Test
    void contentAtOrBefore_erroredActionAfterCompleteWithEmptyContent_returnsEmptyList() {
        DeltaFileFlow flow = flowWithActions(
                completeActionWithEmptyContent(),
                erroredAction()
        );

        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(1);
        List<Content> result = contentAndMetadata.contentList();

        // The previous complete action legitimately produced no content
        assertThat(result).isEmpty();
    }

    @Test
    void contentAtOrBefore_negativeIndex_returnsEmptyList() {
        DeltaFileFlow flow = flowWithActions(completeAction(content("action.txt")));

        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(-1);
        assertThat(contentAndMetadata).isEqualTo(ContentAndMetadata.EMPTY);
    }

    @Test
    void contentAtOrBefore_indexOutOfBounds_returnsEmptyList() {
        DeltaFileFlow flow = flowWithActions(completeAction(content("action.txt")));

        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(5);
        assertThat(contentAndMetadata).isEqualTo(ContentAndMetadata.EMPTY);
    }

    @Test
    void contentAtOrBefore_completeActionWithNullContent_returnsEmptyList() {
        DeltaFileFlow flow = flowWithActions(completeActionWithNullContent());

        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(0);
        List<Content> result = contentAndMetadata.contentList();

        assertThat(result).isEmpty();
    }

    @Test
    void contentAtOrBefore_filteredAction_traversesBack() {
        Content action0Content = content("action0.txt");
        DeltaFileFlow flow = flowWithActions(
                completeAction(action0Content),
                filteredAction()
        );


        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(1);
        List<Content> result = contentAndMetadata.contentList();

        assertThat(result).containsExactly(action0Content);
    }

    @Test
    void contentAtOrBefore_retriedAction_traversesBack() {
        Content action0Content = content("action0.txt");
        DeltaFileFlow flow = flowWithActions(
                completeAction(action0Content),
                retriedAction()
        );

        ContentAndMetadata contentAndMetadata = flow.contentAtOrBefore(1);
        List<Content> result = contentAndMetadata.contentList();

        assertThat(result).containsExactly(action0Content);
    }

    @Test
    void contentAtOrBefore_ingressAction() {
        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow ingressFlow = deltaFile.getFlow("ingress");
        ContentAndMetadata contentAndMetadata = ingressFlow.contentAtOrBefore(0);
        assertThat(contentAndMetadata.metadata()).hasSize(3)
                .containsEntry("type", "document")
                .containsEntry("priority", "low")
                .containsEntry("level", "one");
    }

    @Test
    void contentAtOrBefore_useInputMetadata() {
        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow erroredAfterIngress = deltaFile.getFlow("error-after-ingress");
        ContentAndMetadata contentAndMetadata = erroredAfterIngress.contentAtOrBefore(0);

        // this should have all the input metadata from the ingress flow
        assertThat(contentAndMetadata.metadata()).hasSize(3)
                .containsEntry("type", "document")
                .containsEntry("priority", "low")
                .containsEntry("level", "one");

        DeltaFileFlow egressError = deltaFile.getFlow("egress");
        ContentAndMetadata egressMetadata = egressError.contentAtOrBefore(0);

        // this comes after the transform flow which removed the type key and changed teh priority/level values
        assertThat(egressMetadata.metadata()).hasSize(2)
                .containsEntry("priority", "medium")
                .containsEntry("level", "two");
    }

    @Test
    void contentAtOrBefore_applyCorrectMetadataByIndex() {
        DeltaFile deltaFile = deltaFile();
        DeltaFileFlow transformFlow = deltaFile.getFlow("transform");

        ContentAndMetadata atZero = transformFlow.contentAtOrBefore(0);
        assertThat(atZero.metadata()).hasSize(2)
                .containsEntry("priority", "medium")
                .containsEntry("level", "one");

        ContentAndMetadata atOne = transformFlow.contentAtOrBefore(1);
        assertThat(atOne.metadata()).hasSize(2)
                .containsEntry("priority", "medium")
                .containsEntry("level", "two");

        ContentAndMetadata atTwo = transformFlow.contentAtOrBefore(2);
        // action at 2 was errored, it should fall back to the metadata at index 1
        assertThat(atTwo).isEqualTo(atOne);
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

    private DeltaFile deltaFile() {
        DeltaFile deltaFile = new DeltaFile();

        // build up a data source flow
        DeltaFileFlow ingressFlow = new  DeltaFileFlow();
        ingressFlow.setFlowDefinition(new FlowDefinition(0, "ingress", FlowType.REST_DATA_SOURCE));
        Action ingressAction = completeAction(content("ingress.txt"));
        ingressAction.setMetadata(Map.of("level", "one", "priority", "low", "type", "document"));
        ingressFlow.setActions(List.of(ingressAction));
        deltaFile.setFlows(new HashSet<>(Set.of(ingressFlow)));

        // build up a transform flow
        DeltaFileFlow transform = deltaFile.addFlow(new FlowDefinition(1, "transform", FlowType.TRANSFORM), ingressFlow, OffsetDateTime.now());
        Action transformOne = completeAction(content("header.txt"), content("body.txt"));
        transformOne.setMetadata(Map.of("priority", "medium"));
        transformOne.setDeleteMetadataKeys(List.of("type"));
        transform.getActions().add(transformOne);

        Action transformTwo = completeAction(transformOne.getContent().getFirst(), transformOne.getContent().getLast());
        transformTwo.setMetadata(Map.of("level", "two"));
        transform.getActions().add(transformTwo);

        Action transformThree = erroredAction();
        transform.getActions().add(transformThree);

        // no complete actions branched off ingress
        DeltaFileFlow errorAfterIngress = deltaFile.addFlow(new FlowDefinition(2, "error-after-ingress", FlowType.TRANSFORM), ingressFlow, OffsetDateTime.now());
        errorAfterIngress.getActions().add(erroredAction());

        // no complete actions branched off of transform
        DeltaFileFlow egress = deltaFile.addFlow(new FlowDefinition(3, "egress", FlowType.DATA_SINK), transform, OffsetDateTime.now());
        egress.getActions().add(erroredAction());

        deltaFile.wireBackPointers();
        return deltaFile;
    }
}
