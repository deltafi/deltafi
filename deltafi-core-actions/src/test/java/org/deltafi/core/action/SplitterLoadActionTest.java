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

import lombok.SneakyThrows;
import org.deltafi.test.action.Child;
import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.load.LoadActionTestCase;
import org.deltafi.test.action.load.LoadActionTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class SplitterLoadActionTest extends LoadActionTest {

    @InjectMocks
    SplitterLoadAction action;

    @Test
    @SneakyThrows
    public void testSplitOneResult() {
        LoadActionTestCase testCase = LoadActionTestCase.builder()
                .action(action)
                .testName("oneSplit")
                .inputs(Collections.singletonList(IOContent.builder().name("input.content").contentType("application/binary").build()))
                .parameters(Map.of("reinjectFlow", "testFlow"))
                .expectSplitAction(Collections.singletonList(Child.builder()
                        .name("split.content")
                        .flow("testFlow")
                        .contentType("application/binary")
                        .build()))
                .build();
        execute(testCase);
    }

    @Test
    @SneakyThrows
    public void testSplitTen() {
        LoadActionTestCase testCase = LoadActionTestCase.builder()
                .action(action)
                .testName("tenSplits")
                .inputs(Arrays.asList(
                        IOContent.builder().name("input.content1").contentType("application/binary").build(),
                        IOContent.builder().name("input.content2").contentType("application/binary2").build(),
                        IOContent.builder().name("input.content3").contentType("application/binary3").build(),
                        IOContent.builder().name("input.content4").contentType("application/binary4").build(),
                        IOContent.builder().name("input.content5").contentType("application/binary5").build(),
                        IOContent.builder().name("input.content6").contentType("application/binary6").build(),
                        IOContent.builder().name("input.content7").contentType("application/binary7").build(),
                        IOContent.builder().name("input.content8").contentType("application/binary8").build(),
                        IOContent.builder().name("input.content9").contentType("application/binary9").build(),
                        IOContent.builder().name("input.content10").contentType("application/binary10").build()
                ))
                .parameters(Map.of("reinjectFlow", "testFlow"))
                .expectSplitAction(Arrays.asList(
                        Child.builder().name("split.content1").flow("testFlow").contentType("application/binary").build(),
                        Child.builder().name("split.content2").flow("testFlow").contentType("application/binary2").build(),
                        Child.builder().name("split.content3").flow("testFlow").contentType("application/binary3").build(),
                        Child.builder().name("split.content4").flow("testFlow").contentType("application/binary4").build(),
                        Child.builder().name("split.content5").flow("testFlow").contentType("application/binary5").build(),
                        Child.builder().name("split.content6").flow("testFlow").contentType("application/binary6").build(),
                        Child.builder().name("split.content7").flow("testFlow").contentType("application/binary7").build(),
                        Child.builder().name("split.content8").flow("testFlow").contentType("application/binary8").build(),
                        Child.builder().name("split.content9").flow("testFlow").contentType("application/binary9").build(),
                        Child.builder().name("split.content10").flow("testFlow").contentType("application/binary10").build()
                ))
                .build();
        execute(testCase);
    }

}
