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
import org.deltafi.actionkit.action.domain.DomainInput;
import org.deltafi.common.types.Domain;
import org.deltafi.core.parameters.ExtractXmlAnnotationsParameters;
import org.deltafi.core.parameters.HandleMultipleKeysType;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.deltafi.test.asserters.ActionResultAssertions.assertDomainResult;
import static org.deltafi.test.asserters.ActionResultAssertions.assertErrorResult;

class ExtractXmlAnnotationsDomainActionTest {
    private static final String DOMAIN_NAME1 = "domainName1";
    private static final String DOMAIN_NAME2 = "domainName2";

    ExtractXmlAnnotationsDomainAction action = new ExtractXmlAnnotationsDomainAction();
    DeltaFiTestRunner runner = DeltaFiTestRunner.setup(action);

    @Test
    void testNestedXml() {
        ExtractXmlAnnotationsParameters params = new ExtractXmlAnnotationsParameters();
        params.setXpathToMetadataKeysMap(Map.of("/person/name", "nameAnnotation", "/person/contact/email", "emailAnnotation"));

        String xml = "<person><name>John</name><contact><email>john@example.com</email></contact></person>";
        DomainInput input = DomainInput.builder().domains(domainMap(xml)).build();

        ResultType result = action.extractAndValidate(runner.actionContext(), params, input);
        assertDomainResult(result).addedAnnotations(Map.of("nameAnnotation", "John", "emailAnnotation", "john@example.com"));
    }

    @Test
    void testOption_All() {
        singleDomainTester(HandleMultipleKeysType.ALL, "first|second|first|fourth");
    }

    @Test
    void testOption_Distinct() {
        singleDomainTester(HandleMultipleKeysType.DISTINCT, "first|second|fourth");
    }

    @Test
    void testOption_First() {
        singleDomainTester(HandleMultipleKeysType.FIRST, "first");
    }

    @Test
    void testOption_Last() {
        singleDomainTester(HandleMultipleKeysType.LAST, "fourth");
    }

    private void singleDomainTester(HandleMultipleKeysType optioon, String expected) {
        ExtractXmlAnnotationsParameters params = new ExtractXmlAnnotationsParameters();
        params.setHandleMultipleKeys(optioon);
        params.setAllKeysDelimiter("|");
        params.setXpathToMetadataKeysMap(Map.of("/root/values/value", "valuesAnnotation"));

        String xml = "<root><values><value>first</value><value>second</value><value>first</value><value>fourth</value></values></root>";
        DomainInput input = DomainInput.builder().domains(domainMap(xml)).build();

        ResultType result = action.extractAndValidate(runner.actionContext(), params, input);
        assertDomainResult(result).addedAnnotations(Map.of("valuesAnnotation", expected));
    }

    @Test
    void testMultipleDomainsAllValues() {
        multiDomainTester(HandleMultipleKeysType.ALL, Collections.emptyList(), "first,second,third,third,fourth,fifth");
    }

    @Test
    void testMultipleDomainsDistinctValues() {
        multiDomainTester(HandleMultipleKeysType.DISTINCT, Collections.emptyList(), "first,second,third,fourth,fifth");
    }

    @Test
    void testMultipleDomainsWithFilter() {
        multiDomainTester(HandleMultipleKeysType.ALL, List.of(DOMAIN_NAME2), "third,fourth,fifth");
    }

    private void multiDomainTester(HandleMultipleKeysType optioon, List<String> domains, String expected) {
        ExtractXmlAnnotationsParameters params = new ExtractXmlAnnotationsParameters();
        params.setHandleMultipleKeys(optioon);
        if (!domains.isEmpty()) {
            params.setDomains(domains);
        }
        params.setXpathToMetadataKeysMap(Map.of("/root/values/value", "valuesAnnotation"));

        String xml1 = "<root><values><value>first</value><value>second</value><value>third</value></values></root>";
        String xml2 = "<root><values><value>third</value><value>fourth</value><value>fifth</value></values></root>";
        DomainInput input = DomainInput.builder().domains(domainMap(xml1, xml2)).build();

        ResultType result = action.extractAndValidate(runner.actionContext(), params, input);
        assertDomainResult(result).addedAnnotations(Map.of("valuesAnnotation", expected));
    }

    @Test
    void testErrorOnKeyNotFound() {
        ExtractXmlAnnotationsParameters params = new ExtractXmlAnnotationsParameters();
        params.setXpathToMetadataKeysMap(Map.of("/missing/element", "missingAnnotation"));
        params.setErrorOnKeyNotFound(true);

        String xml = "<root><element>value</element></root>";
        DomainInput input = DomainInput.builder().domains(domainMap(xml)).build();

        ResultType result = action.extractAndValidate(runner.actionContext(), params, input);
        assertErrorResult(result).hasCause("Key not found: /missing/element");
    }

    @Test
    void testNoErrorOnKeyNotFound() {
        ExtractXmlAnnotationsParameters params = new ExtractXmlAnnotationsParameters();
        params.setXpathToMetadataKeysMap(Map.of("/missing/element", "missingAnnotation"));
        params.setErrorOnKeyNotFound(false);

        String xml = "<root><element>value</element></root>";
        DomainInput input = DomainInput.builder().domains(domainMap(xml)).build();

        ResultType result = action.extractAndValidate(runner.actionContext(), params, input);
        assertDomainResult(result).annotationsIsEmpty();
    }

    @Test
    void testInvalidXml() {
        ExtractXmlAnnotationsParameters params = new ExtractXmlAnnotationsParameters();
        params.setXpathToMetadataKeysMap(Map.of("/person/name", "nameAnnotation"));

        String xml = "<person><name>John</name><contact><email>john@example.com</email></contact><person>";
        DomainInput input = DomainInput.builder().domains(domainMap(xml)).build();

        ResultType result = action.extractAndValidate(runner.actionContext(), params, input);
        assertErrorResult(result).hasCause("Unable to read XML domain");
    }

    @Test
    void testInvalidXPath() {
        ExtractXmlAnnotationsParameters params = new ExtractXmlAnnotationsParameters();
        params.setXpathToMetadataKeysMap(Map.of("/person/[name=", "nameAnnotation"));

        String xml = "<person><name>John</name></person>";
        DomainInput input = DomainInput.builder().domains(domainMap(xml)).build();

        ResultType result = action.extractAndValidate(runner.actionContext(), params, input);
        assertErrorResult(result).hasCause("Unable to evaluate XPATH expression");
    }

    private Map<String, Domain> domainMap(String xml) {
        return Map.of(DOMAIN_NAME1, makeDomain(DOMAIN_NAME1, xml));
    }

    private Map<String, Domain> domainMap(String xml1, String xml2) {
        return Map.of(DOMAIN_NAME1, makeDomain(DOMAIN_NAME1, xml1), DOMAIN_NAME2, makeDomain(DOMAIN_NAME2, xml2));
    }

    private Domain makeDomain(String name, String xml) {
        return Domain.builder().name(name).value(xml).mediaType("application/xml").build();
    }
}
