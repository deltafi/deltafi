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
package org.deltafi.core.action.mediatype;

import org.apache.tika.exception.TikaException;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.asserters.ContentAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertErrorResult;
import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModifyMediaTypeTest {

    ModifyMediaType action = new ModifyMediaType();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action, "ModifyMediaTypeTest");
    ActionContext context = runner.actionContext();

    ModifyMediaTypeTest() throws TikaException, IOException {
    }

    @Test
    void modifiesMediaTypes() {
        ModifyMediaTypeParameters params = new ModifyMediaTypeParameters();
        params.setMediaTypeMap(Map.of("image/*", "image/png", "video/mp4", "override/me"));
        params.setIndexMediaTypeMap(Map.of(1, "video/mp4", 5, "ignore/me"));
        params.setErrorOnMissingIndex(false);

        TransformInput input = TransformInput.builder()
                .content(List.of(
                        runner.saveEmptyContent("file1.jpg", "image/jpeg"),
                        runner.saveEmptyContent("file2.mp4", "video/mp4"),
                        runner.saveEmptyContent("file3.png", "image/png")))
                .build();

        ResultType result = action.transform(context, params, input);

        assertTransformResult(result);
        TransformResult transformResult = (TransformResult) result;

        assertEquals("image/png", transformResult.getContent().get(0).getMediaType());
        assertEquals("video/mp4", transformResult.getContent().get(1).getMediaType());
        assertEquals("image/png", transformResult.getContent().get(2).getMediaType());
    }

    @Test
    void errorsOnIndexOutOfBounds() {
        ModifyMediaTypeParameters params = new ModifyMediaTypeParameters();
        params.setIndexMediaTypeMap(Map.of(10, "video/mp4"));
        params.setErrorOnMissingIndex(true);

        TransformInput input = TransformInput.builder()
                .content(List.of(runner.saveEmptyContent("file1.jpg", "image/jpeg")))
                .build();

        ResultType result = action.transform(context, params, input);

        assertErrorResult(result);
    }

    @Test
    void autodetectsMediaTypes() {
        TransformInput input = TransformInput.builder()
                .content(runner.saveContentFromResource("foobar.tar", "foobar.zip", "thing1.txt", "stix1.xml"))
                .build();
        input.getContent().get(0).setMediaType("application/data");
        input.getContent().get(1).setMediaType("application/data");
        input.getContent().get(2).setMediaType("*/*");
        input.getContent().get(3).setMediaType("text/xml");

        TransformResultType result = action.transform(runner.actionContext(), new ModifyMediaTypeParameters(), input);

        assertTransformResult(result)
                .hasContentMatchingAt(0, actionContent -> contentMatches(actionContent, "foobar.tar", "application/x-tar"))
                .hasContentMatchingAt(1, actionContent -> contentMatches(actionContent, "foobar.zip", "application/zip"))
                .hasContentMatchingAt(2, actionContent -> contentMatches(actionContent, "thing1.txt", "text/plain"))
                .hasContentMatchingAt(3, actionContent -> contentMatches(actionContent, "stix1.xml", "application/xml"));
    }

    private boolean contentMatches(ActionContent actionContent, String expectedName, String expectedMediaType) {
        ContentAssert.assertThat(actionContent)
                .hasName(expectedName)
                .hasMediaType(expectedMediaType);

        return true;
    }
}
