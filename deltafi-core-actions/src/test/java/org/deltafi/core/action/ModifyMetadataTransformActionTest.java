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

import java.util.List;
import java.util.Map;

@Component
@Slf4j
@ExtendWith(MockitoExtension.class)
class ModifyMetadataTransformActionTest extends TransformActionTest {

    @InjectMocks
    ModifyMetadataTransformAction action;

    @Test
    void testTransform() {
        execute(TransformActionTestCase.builder()
                .action(action)
                .parameters(Map.of("addOrModifyMetadata", Map.of("key1", "value1", "key2", "value2"),
                        "copyMetadata", Map.of("origKey1", "key3, key4 ,key5"),
                        "deleteMetadataKeys", List.of("origKey2", "origKey3", "key5")))
                .inputs(List.of(IOContent.builder().name("test.txt").contentType("*/*").build()))
                .sourceMetadata(Map.of("origKey1", "origVal1", "origKey2", "origVal2"))
                .expectTransformResult(List.of(IOContent.builder().name("test.txt").contentType("*/*").build()))
                .resultMetadata(Map.of("key1", "value1", "key2", "value2", "key3", "origVal1", "key4", "origVal1"))
                .resultDeleteMetadataKeys(List.of("origKey2"))
                .build());

    }
}
