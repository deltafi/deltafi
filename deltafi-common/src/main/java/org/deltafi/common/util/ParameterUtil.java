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
package org.deltafi.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.expression.Expression;
import org.springframework.expression.common.LiteralExpression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class ParameterUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String PLACEHOLDER_PREFIX = "{{";
    private static final String PLACEHOLDER_SUFFIX = "}}";

    public static final SpelExpressionParser SPEL_EXPRESSION_PARSER = new SpelExpressionParser();
    private static final TemplateParserContext EXP_PARSER_CONTEXT = new TemplateParserContext(PLACEHOLDER_PREFIX, PLACEHOLDER_SUFFIX);

    private ParameterUtil() {}

    /**
     * Use the given text to create a new Expression
     * @param text to parse into an Expression
     * @return the Expression created from the text
     */
    public static Expression newExpression(String text) {
        return SPEL_EXPRESSION_PARSER.parseExpression(text, EXP_PARSER_CONTEXT);
    }

    /**
     * Check if any string values in the action parameters contain a template.
     * @param actionParams map of parameters that may include a template
     * @return true if there are valid templates
     * @throws ParameterTemplateException if there is an error parsing an expression such as mismatched brackets
     */
    public static boolean hasTemplates(Map<String, Object> actionParams) throws ParameterTemplateException {
        if (actionParams == null || actionParams.isEmpty()) {
            return false;
        }
        return hasTemplate(toJsonNode(actionParams));
    }

    private static boolean hasTemplate(JsonNode node) throws ParameterTemplateException {
        boolean hasTemplate = false;
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                hasTemplate |= hasTemplate(field.getValue());
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                hasTemplate |= hasTemplate(node.get(i));
            }
        } else if (node.isTextual()) {
            hasTemplate |= hasTemplate(node.asText());
        }
        return hasTemplate;
    }

    static boolean hasTemplate(String text) throws ParameterTemplateException {
        try {
            Expression expression = newExpression(text);
            return !(expression instanceof LiteralExpression);
        } catch (Exception e) {
            throw new ParameterTemplateException(text, e);
        }
    }

    /**
     * Returns the exception message and the parameters as a json string concatenated together
     * @param exception the exception holding the message to use
     * @param parameters the parameters that caused the exception
     * @return the error context string
     */
    public static String toErrorContext(ParameterTemplateException exception, Map<String, Object> parameters) {
        return exception.getMessage() + ":\n" + toJsonNode(parameters).toPrettyString();
    }

    /**
     * Convert the given map to a JsonNode
     * @param map to convert
     * @return JsonNode representation of the map
     */
    public static JsonNode toJsonNode(Map<String, ?> map) {
        return OBJECT_MAPPER.convertValue(map, JsonNode.class);
    }

    /**
     * Convert the given JsonNode to a Map
     * @param jsonNode to convert
     * @return the map representation of the JsonNode
     */
    public static Map<String, Object> toMap(JsonNode jsonNode) {
        return OBJECT_MAPPER.convertValue(jsonNode, new TypeReference<>() {});
    }
}
