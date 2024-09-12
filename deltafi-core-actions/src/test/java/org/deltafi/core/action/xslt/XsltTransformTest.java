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
package org.deltafi.core.action.xslt;

import org.assertj.core.api.Assertions;
import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.deltafi.test.asserters.ActionResultAssertions.assertErrorResult;
import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

class XsltTransformTest {
    
    XsltTransform action = new XsltTransform();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();
    ActionContext context = runner.actionContext();

    @Test
    void testXsltTransformWithMatchingFilters() {
        XsltParameters params = createParameters();
        params.setContentIndexes(List.of(0));
        params.setFilePatterns(List.of("example.*"));

        TransformInput input = createInput();
        ResultType result = action.transform(context, params, input);

        assertTransformResult(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, this::checkContent);
    }

    @Test
    void testXsltTransformNoMatch() {
        XsltParameters params = createParameters();
        params.setContentIndexes(List.of(1));

        TransformInput input = createInput();
        ResultType result = action.transform(context, params, input);

        assertTransformResult(result)
                .hasContentCount(1)
                .hasContentMatchingAt(0, content -> {
                    // Content should remain unchanged
                    Assertions.assertThat(content.getName()).isEqualTo("example.xml");
                    Assertions.assertThat(content.loadString()).contains("<original>");
                    Assertions.assertThat(content.loadString()).doesNotContain("<new>");
                    return true;
                });
    }

    @Test
    void testXsltTransformWithInvalidSpec() {
        XsltParameters params = new XsltParameters();
        params.setXslt("INVALID");

        TransformInput input = createInput();
        assertErrorResult(action.transform(context, params, input))
                .hasCause("Error transforming content at index 0")
                .hasContextLike("[\\s\\S]*Content is not allowed in prolog[\\s\\S]*");
    }

    @Test
    void testXsltTransformWithInvalidInput() {
        XsltParameters params = createParameters();

        TransformInput input = createBadInput();
        assertErrorResult(action.transform(context, params, input))
                .hasCause("Error transforming content at index 0");
    }

    private XsltParameters createParameters() {
        XsltParameters params = new XsltParameters();
        params.setXslt(
                "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">" +
                        "<xsl:template match=\"original\">" +
                        "  <new><xsl:apply-templates select=\"@* | node()\"/></new>" +
                        "</xsl:template>" +
                        "<xsl:template match=\"@* | node()\">" +
                        "  <xsl:copy><xsl:apply-templates select=\"@* | node()\"/></xsl:copy>" +
                        "</xsl:template>" +
                        "</xsl:stylesheet>"
        );
        return params;
    }

    private boolean checkContent(ActionContent content) {
        Assertions.assertThat(content.getName()).isEqualTo("example.xml");
        Assertions.assertThat(content.loadString()).contains("<new>");
        Assertions.assertThat(content.loadString()).doesNotContain("<original>");
        return true;
    }

    private TransformInput createInput() {
        ActionContent content = runner.saveContent("<original>value</original>", "example.xml", "text/xml");
        return TransformInput.builder().content(List.of(content)).build();
    }

    private TransformInput createBadInput() {
        ActionContent content = runner.saveContent("INVALID", "example.xml", "application/xml");
        return TransformInput.builder().content(List.of(content)).build();
    }
}
