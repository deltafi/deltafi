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
package org.deltafi.core.action;

import org.deltafi.test.action.Child;
import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.transform.TransformActionTest;
import org.deltafi.test.action.transform.TransformActionTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class LineSplitterTransformActionTest extends TransformActionTest {

    @InjectMocks
    LineSplitterTransformAction action;

    // TODO: restore

    @Test
    void testCommentsAndHeader() {
        TransformActionTestCase testCase = TransformActionTestCase.builder()
                .action(action)
                .testName("commentsAndHeader")
                .inputs(Collections.singletonList(IOContent.builder().name("input.content").contentType("application/binary").build()))
                .parameters(Map.of("includeHeaderInAllChunks", "true", "commentChars", "#", "maxRows", "1"))
                .expectTransformResult(List.of(
                        Child.builder().name("content.0").flow("testFlow").contentType("application/binary").build(),
                        Child.builder().name("content.1").flow("testFlow").contentType("application/binary").build(),
                        Child.builder().name("content.2").flow("testFlow").contentType("application/binary").build()
                ))
                .build();
        execute(testCase);
    }

    @Test
    void testDontIncludeHeader() {
        TransformActionTestCase testCase = TransformActionTestCase.builder()
                .action(action)
                .testName("dontIncludeHeader")
                .inputs(Collections.singletonList(IOContent.builder().name("input.content").contentType("application/binary").build()))
                .parameters(Map.of("includeHeaderInAllChunks", "false", "commentChars", "#", "maxRows", "1"))
                .expectTransformResult(List.of(
                        Child.builder().name("content.0").flow("testFlow").contentType("application/binary").build(),
                        Child.builder().name("content.1").flow("testFlow").contentType("application/binary").build(),
                        Child.builder().name("content.2").flow("testFlow").contentType("application/binary").build()
                ))
                .build();
        execute(testCase);
    }

    @Test
    void testHeaderExceedsMaxSize() {
        TransformActionTestCase testCase = TransformActionTestCase.builder()
                .action(action)
                .testName("commentsAndHeader")
                .inputs(Collections.singletonList(IOContent.builder().name("input.content").contentType("application/binary").build()))
                .parameters(Map.of("maxSize", "1"))
                .expectError("The current line will not fit within the max size limit")
                .build();
        execute(testCase);
    }

    @Test
    void testHeaderOnly() {
        TransformActionTestCase testCase = TransformActionTestCase.builder()
                .action(action)
                .testName("headerOnly")
                .inputs(Collections.singletonList(IOContent.builder().name("input.content").contentType("application/binary").build()))
                .parameters(Map.of("includeHeaderInAllChunks", "true", "commentChars", "#", "maxRows", "1"))
                .expectTransformResult(List.of(Child.builder().name("content.0").flow("testFlow").contentType("application/binary").build()))
                .build();
        execute(testCase);
    }
}