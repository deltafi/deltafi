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

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.core.parameters.ExtractXmlMetadataParameters;
import org.deltafi.core.parameters.HandleMultipleKeysType;
import org.deltafi.test.action.transform.TransformActionTest;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertErrorResult;
import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

class ExtractXmlMetadataTransformActionTest extends TransformActionTest {

    ExtractXmlMetadataTransformAction action = new ExtractXmlMetadataTransformAction();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action);

    @Test
    void testNestedXml() {
        ExtractXmlMetadataParameters params = new ExtractXmlMetadataParameters();
        params.setXpathToMetadataKeysMap(Map.of("/person/name", "nameMetadata", "/person/contact/email", "emailMetadata"));

        ActionContent content = saveXml("<person><name>John</name><contact><email>john@example.com</email></contact></person>");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Map.of("nameMetadata", "John", "emailMetadata", "john@example.com"));
    }

    @Test
    void testMultipleValues() {
        ExtractXmlMetadataParameters params = new ExtractXmlMetadataParameters();
        params.setHandleMultipleKeys(HandleMultipleKeysType.ALL);
        params.setAllKeysDelimiter("|");
        params.setXpathToMetadataKeysMap(Map.of("/root/values/value", "valuesMetadata"));

        ActionContent content = saveXml("<root><values><value>first</value><value>second</value><value>third</value></values></root>");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Map.of("valuesMetadata", "first|second|third"));
    }

    @Test
    void testErrorOnKeyNotFound() {
        ExtractXmlMetadataParameters params = new ExtractXmlMetadataParameters();
        params.setXpathToMetadataKeysMap(Map.of("/missing/element", "missingMetadata"));
        params.setErrorOnKeyNotFound(true);

        ActionContent content = saveXml("<root><element>value</element></root>");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertErrorResult(result).hasCause("Key not found: /missing/element");
    }

    @Test
    void testNoErrorOnKeyNotFound() {
        ExtractXmlMetadataParameters params = new ExtractXmlMetadataParameters();
        params.setXpathToMetadataKeysMap(Map.of("/missing/element", "missingMetadata"));
        params.setErrorOnKeyNotFound(false);

        ActionContent content = saveXml("<root><element>value</element></root>");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Collections.emptyMap());
    }

    @Test
    void testInvalidXml() {
        ExtractXmlMetadataParameters params = new ExtractXmlMetadataParameters();
        params.setXpathToMetadataKeysMap(Map.of("/person/name", "nameMetadata"));

        ActionContent content = saveXml("<person><name>John</name><contact><email>john@example.com</email></contact><person>");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertErrorResult(result).hasCause("Unable to read XML content");
    }

    @Test
    void testInvalidXPath() {
        ExtractXmlMetadataParameters params = new ExtractXmlMetadataParameters();
        params.setXpathToMetadataKeysMap(Map.of("/person/[name=", "nameMetadata"));

        ActionContent content = saveXml("<person><name>John</name></person>");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertErrorResult(result).hasCause("Unable to evaluate XPATH expression");
    }

    private ActionContent saveXml(String xml) {
        return ActionContent.saveContent(runner.actionContext(), xml, "example.xml","application/xml");
    }
}
