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
package org.deltafi.common.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import java.util.Collection;
import java.util.Map;

public class SchemaGeneratorFactory {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // fallback resolvers read the deprecated org.deltafi.actionkit.action.parameters.annotation.Size
    // annotation so plugins built against it still emit minLength/maxLength; remove in 3.0 with the annotation.
    @SuppressWarnings("removal")
    public static SchemaGenerator buildSchemaGenerator(SchemaGeneratorConfigCustomizer schemaGeneratorCustomizer,
            CustomDefinitionProviderV2... customDefinitionProviders) {
        SchemaGeneratorConfigBuilder configBuilder =
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                        .without(Option.SCHEMA_VERSION_INDICATOR)
                        .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                        .with(Option.INLINE_ALL_SCHEMAS)
                        .with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                                JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY));

        configBuilder.forFields()
                .withStringMinLengthResolver(fieldScope -> {
                    Size size = fieldScope.getAnnotationConsideringFieldAndGetter(Size.class);
                    if (size != null) {
                        return size.minLength();
                    }
                    org.deltafi.actionkit.action.parameters.annotation.Size deprecatedSize =
                            fieldScope.getAnnotationConsideringFieldAndGetter(org.deltafi.actionkit.action.parameters.annotation.Size.class);
                    return deprecatedSize == null ? null : deprecatedSize.minLength();
                })
                .withStringMaxLengthResolver(fieldScope -> {
                    Size size = fieldScope.getAnnotationConsideringFieldAndGetter(Size.class);
                    if (size != null) {
                        return size.maxLength();
                    }
                    org.deltafi.actionkit.action.parameters.annotation.Size deprecatedSize =
                            fieldScope.getAnnotationConsideringFieldAndGetter(org.deltafi.actionkit.action.parameters.annotation.Size.class);
                    return deprecatedSize == null ? null : deprecatedSize.maxLength();
                })
                .withRequiredCheck(fieldScope -> {
                    JsonProperty jsonProperty = fieldScope.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
                    return jsonProperty != null && jsonProperty.required();
                })
                .withReadOnlyCheck(fieldScope -> {
                    JsonProperty jsonProperty = fieldScope.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
                    return jsonProperty != null && jsonProperty.access() == JsonProperty.Access.READ_ONLY;
                })
                .withDefaultResolver(fieldScope -> {
                    JsonProperty jsonProperty = fieldScope.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
                    if ((jsonProperty == null) || jsonProperty.defaultValue().isEmpty()) {
                        return null;
                    }
                    try {
                        Class<?> type = fieldScope.getDeclaredType().getErasedType();
                        // limit the default types that are mapped, sub-objects should have independent defaults defined as needed
                        if (type.isPrimitive() || Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
                            return OBJECT_MAPPER.readValue(jsonProperty.defaultValue(), new TypeReference<>() {});
                        } else {
                            return jsonProperty.defaultValue();
                        }
                    } catch (JacksonException e) {
                        return jsonProperty.defaultValue();
                    }
                });

        if (schemaGeneratorCustomizer != null) {
            schemaGeneratorCustomizer.customize(configBuilder);
        }

        SchemaGeneratorGeneralConfigPart configPart = configBuilder.forTypesInGeneral();

        configPart = configPart.withAdditionalPropertiesResolver(scope ->
                scope.getType().isInstanceOf(Map.class) ? scope.getTypeParameterFor(Map.class, 1) : null);

        for (CustomDefinitionProviderV2 customDefinitionProviderV2 : customDefinitionProviders) {
            configPart = configPart.withCustomDefinitionProvider(customDefinitionProviderV2);
        }

        return new SchemaGenerator(configBuilder.build());
    }
}
