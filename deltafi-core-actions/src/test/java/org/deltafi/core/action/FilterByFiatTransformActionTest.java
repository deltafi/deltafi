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
import lombok.extern.slf4j.Slf4j;
import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.transform.TransformActionTest;
import org.deltafi.test.action.transform.TransformActionTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@Slf4j
@ExtendWith(MockitoExtension.class)
class FilterByFiatTransformActionTest extends TransformActionTest {

    @InjectMocks
    FilterByFiatTransformAction action;

    @Test
    void testTransform() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .expectFilter("Filtered by fiat")
                .build());

    }

    @Test
    void transformTest2() {
        TransformActionTestCase testCase = TransformActionTestCase.builder()
                .testName("transform")
                .action(action)
                .inputs(Collections.singletonList(IOContent.builder().name("content").contentType("application/binary").build()))
                .expectFilter("Filtered by fiat")
                .build();
        execute(testCase);
    }


}
