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

import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.content.ContentReference;
import org.deltafi.common.splitter.ContentSplitter;
import org.deltafi.common.splitter.SplitException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.Content;
import org.deltafi.core.parameters.ContentSplitterParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class ContentSplitterTransformActionTest {

    @InjectMocks
    ContentSplitterTransformAction splitterTransformAction;

    @Mock
    ContentSplitter contentSplitter;

    @Test
    void testTransform() {
        Content content = new Content();
        content.setName("input.csv");

        TransformInput transformInput = TransformInput.builder().contentList(List.of(content)).build();
        ContentReference cr = new ContentReference();
        Mockito.when(contentSplitter.splitContent(Mockito.eq(content), Mockito.any())).thenReturn(List.of(cr, cr, cr));

        TransformResultType result = splitterTransformAction.transform(null, new ContentSplitterParameters(), transformInput);

        if (result instanceof TransformResult tr) {
            Assertions.assertEquals(3, tr.getContent().size());

            Content first = tr.getContent().get(0);
            Assertions.assertNotNull(first.getContentReference());
            Assertions.assertEquals("input.0.csv", first.getName());
            Assertions.assertEquals(2, first.getMetadata().size());
            Assertions.assertEquals("0", first.getMetadata().get("fragmentId"));
            Assertions.assertEquals("true", first.getMetadata().get("firstFragment"));

            Content second = tr.getContent().get(1);
            Assertions.assertNotNull(second.getContentReference());
            Assertions.assertEquals("input.1.csv", second.getName());
            Assertions.assertEquals(1, second.getMetadata().size());
            Assertions.assertEquals("1", second.getMetadata().get("fragmentId"));

            Content third = tr.getContent().get(2);
            Assertions.assertNotNull(third.getContentReference());
            Assertions.assertEquals("input.2.csv", third.getName());
            Assertions.assertEquals(2, third.getMetadata().size());
            Assertions.assertEquals("2", third.getMetadata().get("fragmentId"));
            Assertions.assertEquals("true", third.getMetadata().get("lastFragment"));
        } else {
            Assertions.fail("Result was not a TransformResult");
        }

    }

    @Test
    void testTransform_splitterException() {
        ActionContext actionContext = new ActionContext();
        actionContext.setDid("did");
        Content content = new Content();
        content.setName("input.csv");

        TransformInput transformInput = TransformInput.builder().contentList(List.of(content)).build();
        ContentReference cr = new ContentReference();
        Mockito.when(contentSplitter.splitContent(Mockito.eq(content), Mockito.any())).thenThrow(new SplitException("failed to split content"));

        TransformResultType result = splitterTransformAction.transform(actionContext, new ContentSplitterParameters(), transformInput);

        if (result instanceof ErrorResult errorResult) {
            Assertions.assertEquals("failed to split content", errorResult.getErrorCause());
            Assertions.assertTrue(errorResult.getErrorSummary().startsWith("failed to split content: did"));
        } else {
            Assertions.fail("Result was not an ErrorResult");
        }
    }

    @Test
    void buildFileName() {
        String name = splitterTransformAction.buildContentName("input.csv", 1);
        Assertions.assertEquals("input.1.csv", name);

        name = splitterTransformAction.buildContentName("input", 1);
        Assertions.assertEquals("input.1", name);

        name = splitterTransformAction.buildContentName("input.", 1);
        Assertions.assertEquals("input.1.", name);

        name = splitterTransformAction.buildContentName("", 1);
        Assertions.assertEquals("1", name);

        name = splitterTransformAction.buildContentName(null, 1);
        Assertions.assertEquals(null, name);
    }


}