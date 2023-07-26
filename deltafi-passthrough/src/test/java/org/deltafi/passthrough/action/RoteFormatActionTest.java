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
package org.deltafi.passthrough.action;

import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.format.FormatActionTest;
import org.deltafi.test.action.format.FormatActionTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class RoteFormatActionTest extends FormatActionTest {

    @InjectMocks
    RoteFormatAction action;

    @Test
    void testDefault() {
        FormatActionTestCase testCase = FormatActionTestCase.builder()
                .action(action)
                .testName("testDefault")
                .inputs(List.of(
                        IOContent.builder().name("input.file1").contentType("application/binary").build(),
                        IOContent.builder().name("input.file2").contentType("application/binary").build()))
                .inputDomains(Collections.emptyMap())
                .parameters(Map.of("minRoteDelayMS", "1", "maxRoteDelayMS", "2"))
                .expectFormatAction(IOContent.builder().name("file1").contentType("application/binary").build())
                .build();
        execute(testCase);
    }
}
