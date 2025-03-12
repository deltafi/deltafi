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
package org.deltafi.core.action.mediatype;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.transform.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.asserters.ErrorResultAssert;
import org.deltafi.test.asserters.TransformResultAssert;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModifyMediaTypeTest {

    ModifyMediaType action = new ModifyMediaType();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup("ModifyMediaTypeTest");
    ActionContext context = runner.actionContext();

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

        TransformResultAssert.assertThat(result);
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

        ErrorResultAssert.assertThat(result);
    }

    @Test
    void autodetectsMediaTypes() {
        TransformInput input = TransformInput.builder()
                .content(runner.saveContentFromResource("foobar.tar", "foobar.zip", "thing1.txt", "stix1.xml", "unknown.abc"))
                .build();
        input.getContent().get(0).setMediaType("application/data");
        input.getContent().get(1).setMediaType("application/data");
        input.getContent().get(2).setMediaType("*/*");
        input.getContent().get(3).setMediaType("text/xml");
        input.getContent().get(4).setMediaType("*/*");

        TransformResultType result = action.transform(runner.actionContext(), new ModifyMediaTypeParameters(), input);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, input.content(0).getName(), "application/x-tar", input.content(0).loadBytes())
                .hasContentMatchingAt(1, input.content(1).getName(), "application/zip", input.content(1).loadBytes())
                .hasContentMatchingAt(2, input.content(2).getName(), "text/plain", input.content(2).loadBytes())
                .hasContentMatchingAt(3, input.content(3).getName(), "application/xml", input.content(3).loadBytes())
                .hasContentMatchingAt(4, input.content(4).getName(), "text/plain", input.content(4).loadBytes());
    }

    @Test
    void autodetectsMediaTypesByNameOnly() {
        TransformInput input = TransformInput.builder()
                .content(runner.saveContentFromResource("foobar.tar", "foobar.zip", "thing1.txt", "stix1.xml", "unknown.abc"))
                .build();
        input.getContent().get(0).setMediaType("application/data");
        input.getContent().get(1).setMediaType("application/data");
        input.getContent().get(2).setMediaType("*/*");
        input.getContent().get(3).setMediaType("text/xml");
        input.getContent().get(4).setMediaType("*/*");

        ModifyMediaTypeParameters params = new ModifyMediaTypeParameters();
        params.setAutodetectByNameOnly(true);
        TransformResultType result = action.transform(runner.actionContext(), params, input);

        TransformResultAssert.assertThat(result)
                .hasContentMatchingAt(0, input.content(0).getName(), "application/x-tar", input.content(0).loadBytes())
                .hasContentMatchingAt(1, input.content(1).getName(), "application/zip", input.content(1).loadBytes())
                .hasContentMatchingAt(2, input.content(2).getName(), "text/plain", input.content(2).loadBytes())
                .hasContentMatchingAt(3, input.content(3).getName(), "application/xml", input.content(3).loadBytes())
                .hasContentMatchingAt(4, input.content(4).getName(), "application/octet-stream", input.content(4).loadBytes());
    }
}
