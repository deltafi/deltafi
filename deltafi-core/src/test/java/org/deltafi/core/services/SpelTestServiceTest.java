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
// ABOUTME: Tests for SpelTestService, verifying expression validation and testing.
// ABOUTME: Covers both condition and template expression modes.
package org.deltafi.core.services;

import org.deltafi.common.types.KeyValue;
import org.deltafi.core.generated.types.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpelTestServiceTest {

    private final SpelTestService spelTestService = new SpelTestService();

    @Test
    void testValidateConditionValid() {
        SpelTestResult result = spelTestService.validateExpression(
                "metadata['type'] == 'order'",
                SpelExpressionMode.CONDITION
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void testValidateConditionInvalid() {
        SpelTestResult result = spelTestService.validateExpression(
                "metadata['type' == 'order'",
                SpelExpressionMode.CONDITION
        );

        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void testValidateTemplateValid() {
        SpelTestResult result = spelTestService.validateExpression(
                "{{ metadata['key'] }}",
                SpelExpressionMode.TEMPLATE
        );

        assertThat(result.getValid()).isTrue();
    }

    @Test
    void testValidateTemplateInvalid() {
        SpelTestResult result = spelTestService.validateExpression(
                "{{ metadata['key' }}",
                SpelExpressionMode.TEMPLATE
        );

        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void testConditionMetadataEquals() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(
                        new KeyValue("type", "order")
                ))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "metadata['type'] == 'order'",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testConditionMetadataNotEquals() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(
                        new KeyValue("type", "invoice")
                ))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "metadata['type'] == 'order'",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("false");
    }

    @Test
    void testConditionHasMediaType() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .content(List.of(
                        SpelContentInput.newBuilder()
                                .name("data.json")
                                .mediaType("application/json")
                                .size(1024L)
                                .build()
                ))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "hasMediaType('application/json')",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testConditionHasMediaTypeFalse() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .content(List.of(
                        SpelContentInput.newBuilder()
                                .name("data.xml")
                                .mediaType("application/xml")
                                .size(1024L)
                                .build()
                ))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "hasMediaType('application/json')",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("false");
    }

    @Test
    void testConditionContentSize() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .content(List.of(
                        SpelContentInput.newBuilder()
                                .name("file1.dat")
                                .mediaType("application/octet-stream")
                                .size(100L)
                                .build(),
                        SpelContentInput.newBuilder()
                                .name("file2.dat")
                                .mediaType("application/octet-stream")
                                .size(200L)
                                .build()
                ))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "content.size() > 1",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testConditionContainsKey() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(
                        new KeyValue("priority", "high")
                ))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "metadata.containsKey('priority')",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testConditionComplexExpression() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(
                        new KeyValue("type", "order"),
                        new KeyValue("priority", "high")
                ))
                .content(List.of(
                        SpelContentInput.newBuilder()
                                .name("order.json")
                                .mediaType("application/json")
                                .size(512L)
                                .build()
                ))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "metadata['type'] == 'order' && hasMediaType('application/json')",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testConditionNumericComparisonWithInteger() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(
                        new KeyValue("count", "5")
                ))
                .build();

        // Compare string metadata value against integer literal - should coerce types
        SpelTestResult result = spelTestService.testExpression(
                "metadata['count'] == 5",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testConditionNumericComparisonNotEquals() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(
                        new KeyValue("count", "5")
                ))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "metadata['count'] != 10",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testConditionNumericComparisonWithFloat() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(
                        new KeyValue("price", "19.99")
                ))
                .build();

        // Float equality works with automatic type coercion
        SpelTestResult result = spelTestService.testExpression(
                "metadata['price'] == 19.99",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testTemplateMetadataSubstitution() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(
                        new KeyValue("customer", "acme-corp")
                ))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "{{ metadata['customer'] }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("acme-corp");
    }

    @Test
    void testTemplateDeltaFileName() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .deltaFileName("test-file.json")
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "{{ deltaFileName }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("test-file.json");
    }

    @Test
    void testTemplateDid() {
        String did = UUID.randomUUID().toString();
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .did(did)
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "{{ did }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo(did);
    }

    @Test
    void testTemplateNow() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder().build();

        SpelTestResult result = spelTestService.testExpression(
                "{{ now() }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isNotNull();
        // Result should be a timestamp string
        assertThat(result.getResult()).containsPattern("\\d{4}-\\d{2}-\\d{2}T");
    }

    @Test
    void testTemplateMixedContent() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .deltaFileName("order-001.json")
                .metadata(List.of(
                        new KeyValue("customer", "acme")
                ))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "output/{{ metadata['customer'] }}/{{ deltaFileName }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("output/acme/order-001.json");
    }

    @Test
    void testConditionWithNullTestData() {
        SpelTestResult result = spelTestService.testExpression(
                "metadata.size() == 0",
                SpelExpressionMode.CONDITION,
                null
        );

        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testConditionError() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder().build();

        SpelTestResult result = spelTestService.testExpression(
                "unknownMethod()",
                SpelExpressionMode.CONDITION,
                testData
        );

        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void testValidateConditionWithInvalidMethod() {
        // Invalid method name should fail validation (not just parsing)
        SpelTestResult result = spelTestService.validateExpression(
                "metadata['key'].invalidMethod(5)",
                SpelExpressionMode.CONDITION
        );

        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).isNotNull();
    }

    // === Validation tests for expression splitting and short-circuit handling ===

    @Test
    void testValidateSimpleValidExpression() {
        assertThat(spelTestService.validateExpression(
                "metadata['type'] == 'order'",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateValidMethodCall() {
        assertThat(spelTestService.validateExpression(
                "metadata['count'].gt(5)",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateInvalidMethodCall() {
        SpelTestResult result = spelTestService.validateExpression(
                "metadata['count'].invalidMethod(5)",
                SpelExpressionMode.CONDITION
        );
        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("invalidMethod");
    }

    @Test
    void testValidateAndWithInvalidRightSide() {
        // Even though left side would short-circuit, right side should still be validated
        SpelTestResult result = spelTestService.validateExpression(
                "hasMediaType('') && metadata['abc'].invalidMethod()",
                SpelExpressionMode.CONDITION
        );
        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("invalidMethod");
    }

    @Test
    void testValidateOrWithInvalidRightSide() {
        SpelTestResult result = spelTestService.validateExpression(
                "metadata['x'] == 'y' || metadata['z'].invalidMethod()",
                SpelExpressionMode.CONDITION
        );
        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("invalidMethod");
    }

    @Test
    void testValidateNotWithInvalidExpression() {
        // ! prefix should be stripped and expression still validated
        SpelTestResult result = spelTestService.validateExpression(
                "!metadata['key'].invalidMethod()",
                SpelExpressionMode.CONDITION
        );
        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("invalidMethod");
    }

    @Test
    void testValidateNotWithAndInvalidRightSide() {
        SpelTestResult result = spelTestService.validateExpression(
                "!hasMediaType('') && metadata['abc'].invalidMethod()",
                SpelExpressionMode.CONDITION
        );
        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("invalidMethod");
    }

    @Test
    void testValidateParenthesizedExpression() {
        assertThat(spelTestService.validateExpression(
                "(metadata['type'] == 'order')",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateParenthesizedWithInvalid() {
        SpelTestResult result = spelTestService.validateExpression(
                "(metadata['key'].invalidMethod())",
                SpelExpressionMode.CONDITION
        );
        assertThat(result.getValid()).isFalse();
    }

    @Test
    void testValidateNestedParensWithAnd() {
        assertThat(spelTestService.validateExpression(
                "(metadata['a'] == 'x') && (metadata['b'] == 'y')",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateNestedParensWithInvalid() {
        SpelTestResult result = spelTestService.validateExpression(
                "(metadata['a'] == 'x') && (metadata['b'].invalidMethod())",
                SpelExpressionMode.CONDITION
        );
        assertThat(result.getValid()).isFalse();
    }

    @Test
    void testValidateComplexValidExpression() {
        assertThat(spelTestService.validateExpression(
                "metadata['type'] == 'order' && hasMediaType('application/json') && metadata['priority'].gt(5)",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateContainsKey() {
        assertThat(spelTestService.validateExpression(
                "metadata.containsKey('mykey')",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateContainsKeyWithAnd() {
        assertThat(spelTestService.validateExpression(
                "metadata.containsKey('mykey') && metadata['mykey'] == 'value'",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateHasMediaType() {
        assertThat(spelTestService.validateExpression(
                "hasMediaType('application/json')",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateContentSize() {
        assertThat(spelTestService.validateExpression(
                "content.size() > 0",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateContentIsEmpty() {
        assertThat(spelTestService.validateExpression(
                "!content.isEmpty()",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateEmptyMetadataKeyIsInvalid() {
        // Empty metadata key should still parse but is likely a user error
        // This tests that we handle edge cases gracefully
        assertThat(spelTestService.validateExpression(
                "metadata['']",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue(); // Syntactically valid, even if semantically questionable
    }

    @Test
    void testValidateQuotesInExpressionNotSplit() {
        // Ensure && inside quotes doesn't cause splitting
        assertThat(spelTestService.validateExpression(
                "metadata['key'] == 'a && b'",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateMultipleOrConditions() {
        assertThat(spelTestService.validateExpression(
                "metadata['type'] == 'a' || metadata['type'] == 'b' || metadata['type'] == 'c'",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateMixedAndOr() {
        assertThat(spelTestService.validateExpression(
                "metadata['type'] == 'order' && (metadata['priority'] == 'high' || metadata['priority'] == 'urgent')",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateMixedAndOrWithInvalid() {
        SpelTestResult result = spelTestService.validateExpression(
                "metadata['type'] == 'order' && (metadata['priority'].invalidMethod() || metadata['x'] == 'y')",
                SpelExpressionMode.CONDITION
        );
        assertThat(result.getValid()).isFalse();
    }

    @Test
    void testValidateUserFriendlyErrorMessage() {
        SpelTestResult result = spelTestService.validateExpression(
                "metadata['key'].badMethod(5)",
                SpelExpressionMode.CONDITION
        );
        assertThat(result.getValid()).isFalse();
        // Should say "metadata value" not "org.deltafi.common.rules.CoercibleString"
        assertThat(result.getErrorMessage()).contains("metadata value");
        assertThat(result.getErrorMessage()).doesNotContain("CoercibleString");
    }

    // === Edge cases with quotes and special characters ===

    @Test
    void testValidateParenthesesInQuotes() {
        assertThat(spelTestService.validateExpression(
                "metadata['key'] == '(value)'",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateAndSymbolInQuotes() {
        assertThat(spelTestService.validateExpression(
                "metadata['key'] == 'a && b'",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateOrSymbolInQuotes() {
        assertThat(spelTestService.validateExpression(
                "metadata['key'] == 'a || b'",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateSingleQuoteInDoubleQuotes() {
        assertThat(spelTestService.validateExpression(
                "metadata[\"key\"] == \"it's valid\"",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateDoubleQuoteInSingleQuotes() {
        assertThat(spelTestService.validateExpression(
                "metadata['key'] == 'say \"hello\"'",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateComplexQuotedContent() {
        assertThat(spelTestService.validateExpression(
                "metadata['desc'] == 'has (parens) && symbols || here'",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateNotOperatorWithParens() {
        assertThat(spelTestService.validateExpression(
                "!(metadata['key'] == 'value')",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateDoubleNegation() {
        assertThat(spelTestService.validateExpression(
                "!!metadata.containsKey('key')",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateNestedParensComplex() {
        assertThat(spelTestService.validateExpression(
                "((metadata['a'] == 'x') && (metadata['b'] == 'y')) || metadata['c'] == 'z'",
                SpelExpressionMode.CONDITION
        ).getValid()).isTrue();
    }

    @Test
    void testValidateNestedParensWithInvalidDeep() {
        SpelTestResult result = spelTestService.validateExpression(
                "((metadata['a'] == 'x') && (metadata['b'].invalidMethod())) || metadata['c'] == 'z'",
                SpelExpressionMode.CONDITION
        );
        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("invalidMethod");
    }

    @Test
    void testValidateComparisonMethods() {
        // All comparison methods should be valid
        assertThat(spelTestService.validateExpression("metadata['n'].gt(5)", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['n'].gte(5)", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['n'].lt(5)", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['n'].lte(5)", SpelExpressionMode.CONDITION).getValid()).isTrue();
    }

    @Test
    void testValidateStringMethods() {
        // String methods on metadata values should be valid
        assertThat(spelTestService.validateExpression("metadata['s'].toLowerCase()", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].toUpperCase()", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].contains('x')", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].startsWith('x')", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].endsWith('x')", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].replaceAll('a', 'b')", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].replaceFirst('a', 'b')", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].split(',')", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].indexOf(\"x\")", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].lastIndexOf(\"x\")", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].equalsIgnoreCase('X')", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].strip()", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].stripLeading()", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].stripTrailing()", SpelExpressionMode.CONDITION).getValid()).isTrue();
        assertThat(spelTestService.validateExpression("metadata['s'].repeat(3)", SpelExpressionMode.CONDITION).getValid()).isTrue();
    }

    @Test
    void testTemplateReplaceAllMethod() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(new KeyValue("path", "a/b/c")))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "{{ metadata['path'].replaceAll('/', '-') }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );
        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("a-b-c");
    }

    @Test
    void testTemplateSplitMethod() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(new KeyValue("items", "a,b,c")))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "{{ metadata['items'].split(',')[1] }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );
        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("b");
    }

    @Test
    void testConditionIndexOfMethod() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(new KeyValue("text", "hello world")))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "metadata['text'].indexOf(\"world\") > 0",
                SpelExpressionMode.CONDITION,
                testData
        );
        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testConditionEqualsIgnoreCaseMethod() {
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(new KeyValue("status", "ACTIVE")))
                .build();

        SpelTestResult result = spelTestService.testExpression(
                "metadata['status'].equalsIgnoreCase('active')",
                SpelExpressionMode.CONDITION,
                testData
        );
        assertThat(result.getValid()).isTrue();
        assertThat(result.getResult()).isEqualTo("true");
    }

    @Test
    void testTemplateNumericComparisonMethods() {
        // Numeric comparison methods should work in template expressions
        SpelTestDataInput testData = SpelTestDataInput.newBuilder()
                .metadata(List.of(new KeyValue("count", "10")))
                .build();

        // gt - greater than
        SpelTestResult gt = spelTestService.testExpression(
                "{{ metadata['count'].gt(5) }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );
        assertThat(gt.getValid()).isTrue();
        assertThat(gt.getResult()).isEqualTo("true");

        // lt - less than
        SpelTestResult lt = spelTestService.testExpression(
                "{{ metadata['count'].lt(5) }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );
        assertThat(lt.getValid()).isTrue();
        assertThat(lt.getResult()).isEqualTo("false");

        // gte - greater than or equal
        SpelTestResult gte = spelTestService.testExpression(
                "{{ metadata['count'].gte(10) }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );
        assertThat(gte.getValid()).isTrue();
        assertThat(gte.getResult()).isEqualTo("true");

        // lte - less than or equal
        SpelTestResult lte = spelTestService.testExpression(
                "{{ metadata['count'].lte(10) }}",
                SpelExpressionMode.TEMPLATE,
                testData
        );
        assertThat(lte.getValid()).isTrue();
        assertThat(lte.getResult()).isEqualTo("true");
    }
}
