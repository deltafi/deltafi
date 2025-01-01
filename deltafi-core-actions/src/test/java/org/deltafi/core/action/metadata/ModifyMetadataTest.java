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
package org.deltafi.core.action.metadata;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.*;

class ModifyMetadataTest {

    ModifyMetadata action = new ModifyMetadata();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testTransform() {
        ModifyMetadataParameters params = new ModifyMetadataParameters();
        params.setAddOrModifyMetadata(new HashMap<>(Map.of("key1", "value1", "key2", "value2")));
        params.setCopyMetadata(Map.of("origKey1", "key3, key4 ,key5"));
        params.setDeleteMetadataKeys(List.of("origKey2", "origKey3", "key5"));

        TransformInput input = TransformInput.builder()
                .metadata(Map.of("origKey1", "origVal1", "origKey2", "origVal2")).build();

        ResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .addedMetadata(Map.of("key1", "value1", "key2", "value2", "key3", "origVal1", "key4", "origVal1"))
                .deletedMetadataKey("origKey2");
    }
}
