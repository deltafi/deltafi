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
package org.deltafi.core.action.delay;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DelayTest {
    @Test
    void delays() {
        Delay action = new Delay();

        ActionContext context = ActionContext.builder().build();
        List<ActionContent> content = List.of(ActionContent.emptyContent(context, "test", MediaType.TEXT_PLAIN));
        Map<String, String> metadata = Map.of("key1", "value1", "key2", "value2");

        long startTimeMs = System.currentTimeMillis();

        DelayParameters delayParameters = new DelayParameters();
        delayParameters.setMinDelayMS(500);
        delayParameters.setMaxDelayMS(1000);

        TransformResultType transformResultType = action.transform(context, delayParameters,
                TransformInput.builder().content(content).metadata(metadata).build());

        long elapsedTimeMs = System.currentTimeMillis() - startTimeMs;
        assertTrue(elapsedTimeMs >= 500 && elapsedTimeMs <= 1050);

        assertInstanceOf(TransformResult.class, transformResultType);
        assertEquals(content, ((TransformResult) transformResultType).getContent());
        assertEquals(metadata, ((TransformResult) transformResultType).getMetadata());
    }
}
