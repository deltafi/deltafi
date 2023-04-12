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
package org.deltafi.core.nifi;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashMap;

class FlowFileUtilTest {

    @Test
    @SneakyThrows
    void testUTF32Metadata() {
        InputStream dataStream = new ClassPathResource("rest-test/flowfile").getInputStream();
        FlowFile flowFile = FlowFileUtil.unarchiveFlowfileV1(dataStream, new HashMap<>());
        Assertions.assertEquals("\uD84E\uDCE7", flowFile.metadata().get("encodedString"));
        Assertions.assertEquals(12, flowFile.metadata().size());
    }

}