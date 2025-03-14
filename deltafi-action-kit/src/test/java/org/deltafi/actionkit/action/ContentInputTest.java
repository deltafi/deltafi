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
package org.deltafi.actionkit.action;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.types.ActionContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.List;

public class ContentInputTest {
    @Test
    void getsContentByName() {
        ActionContext actionContext = ActionContext.builder().build();
        ActionContent content1 = ActionContent.emptyContent(actionContext, "content1", MediaType.TEXT_PLAIN);
        ActionContent content2 = ActionContent.emptyContent(actionContext, "content2", MediaType.TEXT_PLAIN);
        ContentInput contentInput = ContentInput.builder().content(List.of(content1, content2)).build();

        Assertions.assertTrue(contentInput.content("content").isEmpty());
        Assertions.assertEquals(content1, contentInput.content("content1").orElse(null));
        Assertions.assertEquals(content2, contentInput.content("content2").orElse(null));
    }
}
