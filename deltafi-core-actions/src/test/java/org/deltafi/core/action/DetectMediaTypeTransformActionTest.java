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
import org.deltafi.test.action.IOContent;
import org.deltafi.test.action.transform.TransformActionTest;
import org.deltafi.test.action.transform.TransformActionTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class DetectMediaTypeTransformActionTest extends TransformActionTest {

    @InjectMocks
    DetectMediaTypeTransformAction action;

    @Test
    void testTransform() {
        TransformActionTestCase testCase = TransformActionTestCase.builder()
                .action(action)
                .inputs(List.of(IOContent.builder().name("foobar.tar").contentType("application/data").build(),
                        IOContent.builder().name("foobar.zip").contentType("application/data").build(),
                        IOContent.builder().name("thing1.txt").contentType("*/*").build()))
                .expectTransformResult(List.of(IOContent.builder().name("foobar.tar").contentType("application/x-tar").build(),
                        IOContent.builder().name("foobar.zip").contentType("application/zip").build(),
                        IOContent.builder().name("thing1.txt").contentType("text/plain").build()))
                .build();
        execute(testCase);
    }
}
