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
package org.deltafi.actionkit.action.egress;

import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EgressActionTest {

    private static final String TEST_FLOW = "testFlow";
    private static final String TEST_FILENAME = "test.txt";
    private static final String TEST_MEDIA_TYPE = "text/plain";

    @Mock
    private ActionContext actionContext;

    @Mock
    private Content content;

    private TestEgressAction testEgressAction;
    private ActionParameters testParams;

    @BeforeEach
    void setup() {
        testEgressAction = new TestEgressAction();
        testParams = new ActionParameters() {};
    }

    @Test
    void testBuildInputWithEmptyContentList() {
        DeltaFileMessage deltaFileMessage = new DeltaFileMessage();
        deltaFileMessage.setContentList(Collections.emptyList());

        // When
        EgressInput input = testEgressAction.buildInput(actionContext, deltaFileMessage);
        
        // Then
        assertNotNull(input);
        assertNull(input.getContent());
        assertFalse(input.hasContent());
    }

    @Test
    void testBuildInputWithNullContentList() {
        DeltaFileMessage deltaFileMessage = new DeltaFileMessage();
        deltaFileMessage.setContentList(null);

        // When
        EgressInput input = testEgressAction.buildInput(actionContext, deltaFileMessage);
        
        // Then
        assertNotNull(input);
        assertNull(input.getContent());
        assertFalse(input.hasContent());
    }

    @Test
    void testBuildInputWithValidContent() {
        // Given
        DeltaFileMessage deltaFileMessage = new DeltaFileMessage();
        deltaFileMessage.setContentList(List.of(content));
        when(content.getName()).thenReturn(TEST_FILENAME);
        when(content.getMediaType()).thenReturn(TEST_MEDIA_TYPE);

        // When
        EgressInput input = testEgressAction.buildInput(actionContext, deltaFileMessage);
        
        // Then
        assertNotNull(input);
        assertNotNull(input.getContent());
        assertEquals(TEST_FILENAME, input.getContent().getName());
        assertEquals(TEST_MEDIA_TYPE, input.getContent().getMediaType());
    }

    @Test
    void testBuildInputWithMetadata() {
        // Given
        DeltaFileMessage deltaFileMessage = new DeltaFileMessage();
        deltaFileMessage.setContentList(List.of(content));
        
        String testKey = "testKey";
        String testValue = "testValue";
        deltaFileMessage.setMetadata(Map.of(testKey, testValue));
        
        // When
        EgressInput input = testEgressAction.buildInput(actionContext, deltaFileMessage);
        
        // Then
        assertNotNull(input.getMetadata());
        assertEquals(testValue, input.getMetadata().get(testKey));
    }

    @Test
    void testEgressExecuteFlow() {
        // Given
        Content content = mock(Content.class);

        DeltaFileMessage deltaFileMessage = new DeltaFileMessage(Collections.emptyMap(), List.of(content));
        EgressInput input = testEgressAction.buildInput(actionContext, deltaFileMessage);
        
        // When
        EgressResult result = (EgressResult) testEgressAction.execute(actionContext, input, testParams);
        
        // Then
        assertNotNull(result);
        assertEquals(actionContext, result.getContext());
    }

    @Test
    void testGetActionType() {
        assertEquals(ActionType.EGRESS, testEgressAction.getActionType());
    }

    // Test implementation of EgressAction for testing
    private static class TestEgressAction extends EgressAction<ActionParameters> {
        public TestEgressAction() {
            super("Test Egress Action");
        }

        @Override
        public EgressResultType egress(@NotNull ActionContext context, @NotNull ActionParameters params, @NotNull EgressInput egressInput) {
            return new EgressResult(context);
        }
    }
}
