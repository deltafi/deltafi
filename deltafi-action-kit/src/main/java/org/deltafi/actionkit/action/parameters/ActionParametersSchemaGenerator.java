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
package org.deltafi.actionkit.action.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import org.deltafi.actionkit.action.parameters.annotation.Size;

import java.util.Map;

/**
 * Helper class that provides a generic schema generation function for arbitrary parameter classes that are used to
 * configure actions
 *
 * @see ActionParameters
 */
public class ActionParametersSchemaGenerator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final SchemaGenerator SCHEMA_GENERATOR;

    static {
        SchemaGeneratorConfigBuilder configBuilder =
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                        .without(Option.SCHEMA_VERSION_INDICATOR)
                        .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                        .with(Option.INLINE_ALL_SCHEMAS)
                        .with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                                JacksonOption.IGNORE_TYPE_INFO_TRANSFORM,
                                JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY));

        configBuilder.forFields()
                .withStringMinLengthResolver(fieldScope -> {
                    Size size = fieldScope.getAnnotationConsideringFieldAndGetter(Size.class);
                    return size == null ? null : size.minLength();
                })
                .withStringMaxLengthResolver(fieldScope -> {
                    Size size = fieldScope.getAnnotationConsideringFieldAndGetter(Size.class);
                    return size == null ? null : size.maxLength();
                })
                .withDefaultResolver(fieldScope -> {
                    JsonProperty jsonProperty = fieldScope.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
                    if ((jsonProperty == null) || jsonProperty.defaultValue().isEmpty()) {
                        return null;
                    }
                    try {
                        return OBJECT_MAPPER.readValue(jsonProperty.defaultValue(), new TypeReference<>() {});
                    } catch (JsonProcessingException e) {
                        return jsonProperty.defaultValue();
                    }
                });

        configBuilder.forTypesInGeneral()
                .withAdditionalPropertiesResolver(scope ->
                        scope.getType().isInstanceOf(Map.class) ? scope.getTypeParameterFor(Map.class, 1) : null);

        SCHEMA_GENERATOR = new SchemaGenerator(configBuilder.build());
    }

    private ActionParametersSchemaGenerator() {
    }

    public static JsonNode generateSchema(Class<?> clazz) {
        return SCHEMA_GENERATOR.generateSchema(clazz);
    }

}
