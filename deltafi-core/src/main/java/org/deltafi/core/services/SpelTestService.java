/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Provides SpEL expression testing and validation for the UI.
// ABOUTME: Supports both condition expressions (boolean) and template expressions (string substitution).
package org.deltafi.core.services;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.content.Segment;
import org.deltafi.common.rules.CoercibleString;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.KeyValue;
import org.deltafi.common.util.ParameterUtil;
import org.deltafi.core.generated.types.SpelContentInput;
import org.deltafi.core.generated.types.SpelExpressionMode;
import org.deltafi.core.generated.types.SpelTestDataInput;
import org.deltafi.core.generated.types.SpelTestResult;
import org.deltafi.core.util.ExpressionHelpers;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.OpAnd;
import org.springframework.expression.spel.ast.OpOr;
import org.springframework.expression.spel.ast.OperatorNot;
import org.springframework.expression.spel.standard.SpelExpression;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SpelTestService {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    // Pattern to extract metadata keys from expressions like metadata['key'] or metadata["key"]
    private static final Pattern METADATA_KEY_PATTERN = Pattern.compile("metadata\\s*\\[\\s*['\"]([^'\"]+)['\"]\\s*]");
    // Pattern to extract metadata keys from containsKey('key') calls
    private static final Pattern CONTAINS_KEY_PATTERN = Pattern.compile("containsKey\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    // Pattern to extract media types from hasMediaType('type') calls
    private static final Pattern MEDIA_TYPE_PATTERN = Pattern.compile("hasMediaType\\s*\\(\\s*['\"]([^'\"]*)['\"]\\s*\\)");
    // Default test content to ensure content-related expressions can be evaluated
    private static final SpelContentInput DEFAULT_CONTENT = SpelContentInput.newBuilder().name("test.dat").mediaType("application/octet-stream").size(100L).build();

    /**
     * Validate a SpEL expression by parsing it and validating each sub-expression
     * in the AST independently. This avoids short-circuit evaluation hiding errors.
     *
     * @param expression the SpEL expression to validate
     * @param mode       whether this is a CONDITION or TEMPLATE expression
     * @return validation result with error details if invalid
     */
    public SpelTestResult validateExpression(String expression, SpelExpressionMode mode) {
        if (mode == SpelExpressionMode.TEMPLATE) {
            // For templates, just test the whole expression
            return validateSingleExpression(expression, mode);
        }

        // Parse to get AST
        Expression parsed;
        try {
            parsed = PARSER.parseExpression(expression);
        } catch (ParseException e) {
            return SpelTestResult.newBuilder().valid(false).errorMessage(e.getMessage()).errorPosition(e.getPosition()).build();
        }

        // If not a SpelExpression (unlikely with SpelExpressionParser), fall back to simple validation
        if (!(parsed instanceof SpelExpression spelExpression)) {
            return validateSingleExpression(expression, mode);
        }

        // Extract all leaf expressions from AST (avoiding short-circuit operators)
        List<String> leafExpressions = new ArrayList<>();
        extractLeafExpressions(spelExpression.getAST(), leafExpressions);

        // Validate each leaf expression independently
        for (String leaf : leafExpressions) {
            SpelTestResult result = validateSingleExpression(leaf, mode);
            if (!result.getValid()) {
                return result;
            }
        }

        return SpelTestResult.newBuilder().valid(true).build();
    }

    /**
     * Recursively extract leaf expressions from the AST, bypassing && and || nodes.
     * This allows us to validate each part independently without short-circuit issues.
     */
    private void extractLeafExpressions(SpelNode node, List<String> leaves) {
        if (node instanceof OpAnd || node instanceof OpOr) {
            // Recurse into children of && and || operators
            for (int i = 0; i < node.getChildCount(); i++) {
                extractLeafExpressions(node.getChild(i), leaves);
            }
        } else if (node instanceof OperatorNot) {
            // For ! operator, recurse into its child
            extractLeafExpressions(node.getChild(0), leaves);
        } else {
            // Leaf expression - convert back to string
            leaves.add(node.toStringAST());
        }
    }

    /**
     * Validate a single expression (no && or ||) by evaluating it against test data.
     * For validation, we evaluate without requiring Boolean conversion since
     * sub-expressions may return non-boolean types (e.g., indexOf returns int).
     */
    private SpelTestResult validateSingleExpression(String expression, SpelExpressionMode mode) {
        // Extract metadata keys and create test data
        Set<String> metadataKeys = new HashSet<>();
        metadataKeys.addAll(extractMetadataKeys(expression));
        metadataKeys.addAll(extractContainsKeyKeys(expression));

        List<KeyValue> testMetadata = metadataKeys.stream().map(key -> new KeyValue(key, "0")).toList();

        Set<String> mediaTypes = extractMediaTypes(expression);
        List<SpelContentInput> testContent = new ArrayList<>();
        testContent.add(DEFAULT_CONTENT);
        for (String mediaType : mediaTypes) {
            testContent.add(SpelContentInput.newBuilder().name("test.dat").mediaType(mediaType).size(100L).build());
        }

        SpelTestDataInput testData = SpelTestDataInput.newBuilder().metadata(testMetadata).content(testContent).build();

        // For validation, evaluate without type coercion to catch method errors
        // without failing on valid expressions that return non-boolean types
        SpelTestResult result = validateExpressionEvaluation(expression, testData);

        return SpelTestResult.newBuilder().valid(result.getValid()).errorMessage(cleanErrorMessage(result.getErrorMessage())).errorPosition(result.getErrorPosition()).build();
    }

    /**
     * Clean up SpEL error messages to be more user-friendly.
     */
    private String cleanErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.replace("org.deltafi.common.rules.CoercibleString", "metadata value");
    }

    /**
     * Extract metadata keys referenced in a SpEL expression.
     * Finds patterns like metadata['key'] or metadata["key"].
     */
    private Set<String> extractMetadataKeys(String expression) {
        Set<String> keys = new HashSet<>();
        Matcher matcher = METADATA_KEY_PATTERN.matcher(expression);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /**
     * Extract metadata keys from containsKey() calls in a SpEL expression.
     */
    private Set<String> extractContainsKeyKeys(String expression) {
        Set<String> keys = new HashSet<>();
        Matcher matcher = CONTAINS_KEY_PATTERN.matcher(expression);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /**
     * Extract media types from hasMediaType() calls in a SpEL expression.
     */
    private Set<String> extractMediaTypes(String expression) {
        Set<String> mediaTypes = new HashSet<>();
        Matcher matcher = MEDIA_TYPE_PATTERN.matcher(expression);
        while (matcher.find()) {
            mediaTypes.add(matcher.group(1));
        }
        return mediaTypes;
    }

    /**
     * Test a SpEL expression against provided test data.
     *
     * @param expression the SpEL expression to test
     * @param mode       whether this is a CONDITION or TEMPLATE expression
     * @param testData   optional test data to evaluate against
     * @return test result with evaluated value or error details
     */
    public SpelTestResult testExpression(String expression, SpelExpressionMode mode, SpelTestDataInput testData) {
        try {
            if (mode == SpelExpressionMode.CONDITION) {
                return testCondition(expression, testData);
            } else {
                return testTemplate(expression, testData);
            }
        } catch (ParseException e) {
            return SpelTestResult.newBuilder().valid(false).errorMessage(e.getMessage()).errorPosition(e.getPosition()).build();
        } catch (Exception e) {
            return SpelTestResult.newBuilder().valid(false).errorMessage(e.getMessage()).build();
        }
    }

    /**
     * Validate an expression by evaluating it without type coercion.
     * This catches method errors while allowing non-boolean return types.
     */
    private SpelTestResult validateExpressionEvaluation(String expression, SpelTestDataInput testData) {
        try {
            Map<String, CoercibleString> metadata = toCoercibleMetadataMap(testData);
            List<Content> content = toContentList(testData);

            ConditionContext context = new ConditionContext(Collections.unmodifiableMap(metadata), content.stream().map(Content::copy).toList());

            SimpleEvaluationContext evalContext = SimpleEvaluationContext.forReadOnlyDataBinding().withRootObject(context).withInstanceMethods().build();

            Expression exp = PARSER.parseExpression(expression);
            // Evaluate without type coercion - we just want to verify the expression is valid
            exp.getValue(evalContext);

            return SpelTestResult.newBuilder().valid(true).build();
        } catch (ParseException e) {
            return SpelTestResult.newBuilder().valid(false).errorMessage(e.getMessage()).errorPosition(e.getPosition()).build();
        } catch (Exception e) {
            return SpelTestResult.newBuilder().valid(false).errorMessage(e.getMessage()).build();
        }
    }

    private SpelTestResult testCondition(String expression, SpelTestDataInput testData) {
        Map<String, CoercibleString> metadata = toCoercibleMetadataMap(testData);
        List<Content> content = toContentList(testData);

        ConditionContext context = new ConditionContext(Collections.unmodifiableMap(metadata), content.stream().map(Content::copy).toList());

        // Use SimpleEvaluationContext to match RuleEvaluator behavior
        // CoercibleString handles numeric comparisons in its equals() method
        SimpleEvaluationContext evalContext = SimpleEvaluationContext.forReadOnlyDataBinding().withRootObject(context).withInstanceMethods().build();

        Expression exp = PARSER.parseExpression(expression);
        Boolean result = exp.getValue(evalContext, Boolean.class);

        return SpelTestResult.newBuilder().valid(true).result(String.valueOf(result)).build();
    }

    private SpelTestResult testTemplate(String expression, SpelTestDataInput testData) {
        Map<String, CoercibleString> metadata = toCoercibleMetadataMap(testData);
        List<Content> content = toContentList(testData);

        TemplateContext context = TemplateContext.builder().did(testData != null && testData.getDid() != null ? UUID.fromString(testData.getDid()) : UUID.randomUUID()).deltaFileName(testData != null && testData.getDeltaFileName() != null ? testData.getDeltaFileName() : "test-file.dat").metadata(metadata).content(content).build();

        SimpleEvaluationContext evalContext = SimpleEvaluationContext.forReadOnlyDataBinding().withRootObject(context).withInstanceMethods().build();

        Expression exp = ParameterUtil.newExpression(expression);
        String result = exp.getValue(evalContext, String.class);

        return SpelTestResult.newBuilder().valid(true).result(result).build();
    }

    private Map<String, CoercibleString> toCoercibleMetadataMap(SpelTestDataInput testData) {
        if (testData == null || testData.getMetadata() == null) {
            return new HashMap<>();
        }
        Map<String, CoercibleString> result = new HashMap<>();
        for (var kv : testData.getMetadata()) {
            result.put(kv.getKey(), new CoercibleString(kv.getValue()));
        }
        return result;
    }

    private List<Content> toContentList(SpelTestDataInput testData) {
        if (testData == null || testData.getContent() == null) {
            return new ArrayList<>();
        }
        List<Content> result = new ArrayList<>();
        for (SpelContentInput c : testData.getContent()) {
            // Create a mock segment with the specified size
            Segment segment = new Segment(UUID.randomUUID(), 0, c.getSize(), UUID.randomUUID());
            result.add(new Content(c.getName(), c.getMediaType(), segment));
        }
        return result;
    }

    /**
     * Context for condition expressions (matches ImmutableDeltaFileFlow in RuleEvaluator).
     * Metadata values are CoercibleString to enable numeric comparisons.
     */
    @SuppressWarnings("unused") // Methods are called via SpEL reflection
    public record ConditionContext(Map<String, CoercibleString> metadata, List<Content> content) {
        public boolean hasMediaType(String mediaType) {
            return content.stream().anyMatch(c -> Objects.equals(mediaType, c.getMediaType()));
        }
    }

    /**
     * Context for template expressions (matches ExpressionInput in ParameterResolver). Helper methods
     * here must stay in sync with ParameterResolver.ExpressionInput so validation through the UI's SpEL
     * test panel resolves the same methods that resolve at runtime.
     */
    @Builder
    @SuppressWarnings("unused")
    public record TemplateContext(UUID did, String deltaFileName, Map<String, CoercibleString> metadata,
                                  List<Content> content) {
        public String now() {
            return OffsetDateTime.now().toString();
        }

        // ---- Encoding / decoding ----
        public String base64Encode(CharSequence value) {
            return ExpressionHelpers.base64Encode(value);
        }

        public String base64Decode(CharSequence value) {
            return ExpressionHelpers.base64Decode(value);
        }

        public String base64UrlEncode(CharSequence value) {
            return ExpressionHelpers.base64UrlEncode(value);
        }

        public String base64UrlDecode(CharSequence value) {
            return ExpressionHelpers.base64UrlDecode(value);
        }

        public String hexEncode(CharSequence value) {
            return ExpressionHelpers.hexEncode(value);
        }

        public String hexDecode(CharSequence value) {
            return ExpressionHelpers.hexDecode(value);
        }

        public String urlEncode(CharSequence value) {
            return ExpressionHelpers.urlEncode(value);
        }

        public String urlDecode(CharSequence value) {
            return ExpressionHelpers.urlDecode(value);
        }

        // ---- Hashing (hex output) ----
        public String sha256(CharSequence value) {
            return ExpressionHelpers.sha256(value);
        }

        public String sha512(CharSequence value) {
            return ExpressionHelpers.sha512(value);
        }

        public String sha1(CharSequence value) {
            return ExpressionHelpers.sha1(value);
        }

        public String md5(CharSequence value) {
            return ExpressionHelpers.md5(value);
        }

        // ---- Escaping ----
        public String escapeJson(CharSequence value) {
            return ExpressionHelpers.escapeJson(value);
        }

        public String escapeXml(CharSequence value) {
            return ExpressionHelpers.escapeXml(value);
        }

        public String escapeHtml(CharSequence value) {
            return ExpressionHelpers.escapeHtml(value);
        }

        // ---- Utility ----
        public String uuid() {
            return ExpressionHelpers.uuid();
        }

        public String timestamp() {
            return ExpressionHelpers.timestamp();
        }

        public String coalesce(CharSequence... values) {
            return ExpressionHelpers.coalesce(values);
        }

        public String defaultIfBlank(CharSequence v, CharSequence fallback) {
            return ExpressionHelpers.defaultIfBlank(v, fallback);
        }
    }
}
