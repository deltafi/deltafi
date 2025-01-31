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
package org.deltafi.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionInput;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.DeltaFileMessage;
import org.deltafi.common.util.ParameterTemplateException;
import org.deltafi.common.util.ParameterUtil;
import org.deltafi.core.generated.types.FlowConfigError;
import org.deltafi.core.validation.SchemaComplianceUtil;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ParameterResolver {

    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public void resolve(ActionInput actionInput) throws ParameterTemplateException {
        if (!actionInput.needsResolved()) {
            return;
        }

        try {
            JsonNode paramJson = ParameterUtil.toJsonNode(actionInput.getActionParams());
            JsonNode updatedJson = replacePlaceholders(paramJson, actionInput);
            actionInput.setActionParams(ParameterUtil.toMap(updatedJson));

            // after running the substitutions make sure the parameters comply with the action parameter schema
            FlowConfigError error = SchemaComplianceUtil.validateParameters(actionInput.getActionContext().getActionName(), actionInput.getParameterSchema(), actionInput.getActionParams()).orElse(null);
            if (error != null) {
                throw new ParameterTemplateException("Error in " + error.getConfigName() + ": " + error.getMessage());
            }
        } catch (ParameterTemplateException e) {
            throw e;
        } catch (Exception e) {
            throw new ParameterTemplateException(e);
        }
    }

    private JsonNode replacePlaceholders(JsonNode node, ActionInput actionInput) throws ParameterTemplateException {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                objectNode.set(field.getKey(), replacePlaceholders(field.getValue(), actionInput));
            }
            return objectNode;
        } else if (node.isArray() && node instanceof ArrayNode arrayNode) {
            for (int i = 0; i < node.size(); i++) {
                arrayNode.set(i, replacePlaceholders(node.get(i), actionInput));
            }
            return node;
        } else if (node.isTextual()) {
            return new TextNode(evaluateExpression(node.asText(), actionInput));
        }
        return node;
    }

    public String evaluateExpression(String text, ActionInput actionInput) throws ParameterTemplateException {
        Expression expression = expressionCache.computeIfAbsent(text, ParameterUtil::newExpression);
        ActionContext actionContext = actionInput.getActionContext();
        DeltaFileMessage message = actionInput.getDeltaFileMessages().getFirst();
        ExpressionInput expressionInput = ExpressionInput.builder()
                .did(actionContext.getDid())
                .deltaFileName(actionContext.getDeltaFileName())
                .metadata(message.getMetadata())
                .content(message.getContentList())
                .actionContext(actionContext)
                .deltaFileMessages(actionInput.getDeltaFileMessages())
                .build();

        EvaluationContext context = SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .withRootObject(expressionInput)
                .withInstanceMethods()
                .build();


        try {
            return expression.getValue(context, String.class);
        } catch (Exception e) {
            throw new ParameterTemplateException(expression.getExpressionString(), e);
        }
    }

    @Builder
    @SuppressWarnings("unused")
    private record ExpressionInput(UUID did, String deltaFileName, Map<String, String> metadata, List<Content> content, ActionContext actionContext, List<DeltaFileMessage> deltaFileMessages) {

        /**
         * Helper method to get the current timestamp as a string
         * @return current time as a string
         */
        public String now() {
            return OffsetDateTime.now().toString();
        }
    }
}
