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
package org.deltafi.core.action.extract;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertErrorResult;
import static org.deltafi.test.asserters.ActionResultAssertions.assertTransformResult;

class ExtractXmlTest {

    ExtractXml action = new ExtractXml();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup();

    @Test
    void testNestedXml() {
        ExtractXmlParameters params = new ExtractXmlParameters();
        params.setXpathToKeysMap(Map.of("/person/name", "nameMetadata", "/person/contact/email", "emailMetadata"));

        ActionContent content = saveXml("<person><name>John</name><contact><email>john@example.com</email></contact></person>");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Map.of("nameMetadata", "John", "emailMetadata", "john@example.com"));
    }

    @Test
    void testMultipleValues_All() {
        singleContentTester(HandleMultipleKeysType.ALL, "first|second|first|fourth");
    }

    @Test
    void testMultipleValues_Distinct() {
        singleContentTester(HandleMultipleKeysType.DISTINCT, "first|second|fourth");
    }

    @Test
    void testMultipleValues_First() {
        singleContentTester(HandleMultipleKeysType.FIRST, "first");
    }

    @Test
    void testMultipleValues_Last() {
        singleContentTester(HandleMultipleKeysType.LAST, "fourth");
    }

    void singleContentTester(HandleMultipleKeysType option, String expected) {
        ExtractXmlParameters params = new ExtractXmlParameters();
        params.setHandleMultipleKeys(option);
        params.setAllKeysDelimiter("|");
        params.setXpathToKeysMap(Map.of("/root/values/value", "valuesMetadata"));

        ActionContent content = saveXml(
                "<root><values><value>first</value><value>second</value><value>first</value><value>fourth</value></values></root>");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Map.of("valuesMetadata", expected));
    }

    @Test
    void testNoErrorOnKeyNotFound() {
        ExtractXmlParameters params = new ExtractXmlParameters();
        params.setXpathToKeysMap(Map.of("/missing/element", "missingMetadata"));

        ActionContent content = saveXml("<root><element>value</element></root>");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedMetadataEquals(Collections.emptyMap());
    }

    @Test
    void testErrorOnKeyNotFound() {
        ExtractXmlParameters params = new ExtractXmlParameters();
        params.setXpathToKeysMap(Map.of("/missing/element", "missingMetadata"));
        params.setErrorOnKeyNotFound(true);

        ActionContent content = saveXml("<root><element>value</element></root>");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertErrorResult(result).hasCause("Key not found: /missing/element");
    }

    @Test
    void testInvalidXml() {
        ExtractXmlParameters params = new ExtractXmlParameters();
        params.setXpathToKeysMap(Map.of("/person/name", "nameMetadata"));

        ActionContent content = saveXml("<person><name>John</name><contact><email>john@example.com</email></contact><person>");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertErrorResult(result).hasCause("Unable to read XML content");
    }

    @Test
    void testInvalidXPath() {
        ExtractXmlParameters params = new ExtractXmlParameters();
        params.setXpathToKeysMap(Map.of("/person/[name=", "nameMetadata"));

        ActionContent content = saveXml("<person><name>John</name></person>");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertErrorResult(result).hasCause("Unable to evaluate XPATH expression");
    }

    @Test
    void testNestedXmlToAnnotations() {
        ExtractXmlParameters params = new ExtractXmlParameters();
        params.setExtractTarget(ExtractTarget.ANNOTATIONS);
        params.setXpathToKeysMap(Map.of("/person/name", "nameMetadata", "/person/contact/email", "emailMetadata"));

        ActionContent content = saveXml("<person><name>John</name><contact><email>john@example.com</email></contact></person>");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedAnnotations(Map.of("nameMetadata", "John", "emailMetadata", "john@example.com"));
    }

    @Test
    void testMultipleValuesToAnnotations_All() {
        singleContentToAnnotationsTester(HandleMultipleKeysType.ALL, "first|second|first|fourth");
    }

    @Test
    void testMultipleValuesToAnnotations_Distinct() {
        singleContentToAnnotationsTester(HandleMultipleKeysType.DISTINCT, "first|second|fourth");
    }

    @Test
    void testMultipleValuesToAnnotations_First() {
        singleContentToAnnotationsTester(HandleMultipleKeysType.FIRST, "first");
    }

    @Test
    void testMultipleValuesToAnnotations_Last() {
        singleContentToAnnotationsTester(HandleMultipleKeysType.LAST, "fourth");
    }

    void singleContentToAnnotationsTester(HandleMultipleKeysType option, String expected) {
        ExtractXmlParameters params = new ExtractXmlParameters();
        params.setExtractTarget(ExtractTarget.ANNOTATIONS);
        params.setHandleMultipleKeys(option);
        params.setAllKeysDelimiter("|");
        params.setXpathToKeysMap(Map.of("/root/values/value", "valuesMetadata"));

        ActionContent content = saveXml(
                "<root><values><value>first</value><value>second</value><value>first</value><value>fourth</value></values></root>");
        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedAnnotations(Map.of("valuesMetadata", expected));
    }

    @Test
    void testNoErrorOnKeyNotFoundToAnnotations() {
        ExtractXmlParameters params = new ExtractXmlParameters();
        params.setExtractTarget(ExtractTarget.ANNOTATIONS);
        params.setXpathToKeysMap(Map.of("/missing/element", "missingMetadata"));
        params.setErrorOnKeyNotFound(false);

        ActionContent content = saveXml("<root><element>value</element></root>");

        TransformInput input = TransformInput.builder().content(List.of(content)).build();

        ResultType result = action.transform(runner.actionContext(), params, input);
        assertTransformResult(result).addedAnnotations(Collections.emptyMap());
    }

    private ActionContent saveXml(String xml) {
        return runner.saveContent(xml, "example.xml", "application/xml");
    }
}
