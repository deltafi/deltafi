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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.ActionInput;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.DeltaFileMessage;
import org.deltafi.common.util.ParameterTemplateException;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class ParameterResolverTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    ParameterResolver resolver = new ParameterResolver();


    @Test
    @SneakyThrows
    void testResolve() {
        Map<String, Object> params = new HashMap<>();
        params.put("dataSink", "{{metadata['dataSink']}}");
        params.put("metadataKey", "{{metadata['keyholder'] + '-' + metadata['dataSink']}}");
        params.put("url", "https://some.service/{{deltaFileName.toUpperCase()}}");
        params.put("retryCount", "{{metadata['retryCount']}}");
        params.put("enableFeature", "{{did.toString().startsWith('0')}}");
        params.put("mapField", Map.of("fileName", "{{deltaFileName.toUpperCase()}}", "firstFile", "{{ content[0].name }}"));
        params.put("someOptionalList", List.of("a", "b", "{{metadata['dataSink']}}"));
        ActionInput input = actionInput(params);

        resolver.resolve(input);

        Map<String, Object> after = input.getActionParams();
        Assertions.assertThat(after)
                .containsEntry("dataSink", "my-data-sink")
                .containsEntry("url", "https://some.service/INPUT.TXT")
                .containsEntry("metadataKey", "extracted-key-name-my-data-sink")
                .containsEntry("retryCount", "3")
                .containsEntry("enableFeature", "true")
                .containsEntry("someOptionalList", List.of("a", "b", "my-data-sink"))
                .containsEntry("mapField", Map.of("fileName", "INPUT.TXT", "firstFile", "first.txt"));
    }

    @Test
    void testResolve_badType() {
        Map<String, Object> params = new HashMap<>();
        params.put("dataSink", "{{metadata['dataSink']}}");
        params.put("metadataKey", "{{metadata['keyholder'] + '-' + metadata['dataSink']}}");
        params.put("url", "https://some.service/{{deltaFileName.toUpperCase()}}");
        params.put("retryCount", "three");
        ActionInput input = actionInput(params);

        Assertions.assertThatThrownBy(() -> resolver.resolve(input))
                .isInstanceOf(ParameterTemplateException.class)
                .hasMessage("Error in my-action: $.retryCount: string found, integer expected");
    }

    @Test
    void tesResolve_missingRequired() {
        Map<String, Object> params = new HashMap<>();
        params.put("metadataKey", "my-key");
        ActionInput input = actionInput(params);

        Assertions.assertThatThrownBy(() -> resolver.resolve(input))
                .isInstanceOf(ParameterTemplateException.class)
                .hasMessage("Error in my-action: $: required property 'url' not found");
    }

    @Test
    void testResolve_fieldNotInSchema() {
        Map<String, Object> params = new HashMap<>();
        params.put("metadataKey", "{{metadata['keyholder'] + '-' + metadata['dataSink']}}");
        params.put("url", "https://some.service/{{deltaFileName.toUpperCase()}}");
        params.put("extra", "value");
        ActionInput input = actionInput(params);

        Assertions.assertThatThrownBy(() -> resolver.resolve(input))
                .isInstanceOf(ParameterTemplateException.class)
                .hasMessage("Error in my-action: $: property 'extra' is not defined in the schema and the schema does not allow additional properties");
    }

    @Test
    void testResolve_unknownSpelProperty() {
        Map<String, Object> params = new HashMap<>();
        params.put("metadataKey", "{{metadata['keyholder'] + '-' + metadata['dataSink']}}");
        params.put("url", "https://some.service/{{notARealProp.toUpperCase()}}");
        ActionInput input = actionInput(params);

        Assertions.assertThatThrownBy(() -> resolver.resolve(input))
                .isInstanceOf(ParameterTemplateException.class)
                .hasMessageContaining("Property or field 'notARealProp' cannot be found on object of type 'org.deltafi.core.util.ParameterResolver$ExpressionInput'");
    }

    private ActionInput actionInput(Map<String, Object> params) {
        DeltaFileMessage message = new DeltaFileMessage();
        message.setMetadata(Map.of("dataSink", "my-data-sink", "keyholder", "extracted-key-name", "retryCount", "3"));
        Content content = new Content();
        content.setName("first.txt");
        content.setSize(20);
        message.setContentList(List.of(content));
        ActionInput input = new ActionInput();
        input.setActionContext(ActionContext.builder().actionName("my-action").deltaFileName("input.txt").did(new UUID(0,0)).build());
        input.setTemplated(true);
        input.setDeltaFileMessages(List.of(message));
        input.setParameterSchema(JSON_SCHEMA);
        input.setActionParams(params);
        return input;
    }

    @Language("json")
    private static final String SCHEMA = """
            {
                "type" : "object",
                "properties" : {
                  "dataSink" : {
                    "type" : "string",
                    "description" : "Name of the dataSink the DeltaFile is flowing through"
                  },
                  "metadataKey" : {
                    "type" : "string",
                    "description" : "Send metadata as JSON in this HTTP header field"
                  },
                  "retryCount" : {
                    "type" : "integer",
                    "description" : "Number of times to retry a failing HTTP request"
                  },
                  "retryDelayMs" : {
                    "type" : "integer",
                    "description" : "Number milliseconds to wait for an HTTP retry"
                  },
                  "url" : {
                    "type" : "string",
                    "description" : "The URL to post the DeltaFile to"
                  },
                  "enableFeature": {
                    "type" : "boolean",
                    "description" : "Turn feature on or off"
                  },
                  "someOptionalList" : {
                    "description" : "Some optional list parameter",
                    "type" : "array",
                    "items" : {
                      "type" : "string"
                    }
                  },
                  "someRequiredList" : {
                    "description" : "Required list parameter",
                    "type" : "array",
                    "items" : {
                      "type" : "string"
                    }
                  },
                  "mapField" : {
                    "description" : "Map fields",
                    "type": "object"
                  },
                  "complex" : {
                    "type": "object",
                    "description": "Sample embedded param",
                    "properties": {
                      "subKey": {
                        "type": "string",
                        "description": "sample nested field"
                      },
                      "subList": {
                        "type": "array",
                        "description": "sample nested list"
                      }
                    }
                  },
                  "complexList": {
                    "type" : "array",
                    "items" : {
                      "type" : "object"
                    }
                  }
                },
                "required" : [
                  "metadataKey",
                  "url"
                ],
                "additionalProperties" : false
              }
            """;


    private static final JsonNode JSON_SCHEMA = getJsonSchema();

    @SneakyThrows
    private static JsonNode getJsonSchema() {
        return OBJECT_MAPPER.readValue(SCHEMA, JsonNode.class);
    }

}