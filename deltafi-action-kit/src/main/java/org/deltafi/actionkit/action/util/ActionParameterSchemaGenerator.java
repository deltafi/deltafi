package org.deltafi.actionkit.action.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import java.util.Map;

public class ActionParameterSchemaGenerator {

    private static final SchemaGenerator SCHEMA_GENERATOR;

    static {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2019_09, OptionPreset.PLAIN_JSON)
                .without(Option.SCHEMA_VERSION_INDICATOR)
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                .with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED, JacksonOption.IGNORE_TYPE_INFO_TRANSFORM, JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY));

        configBuilder.forTypesInGeneral()
                .withAdditionalPropertiesResolver(scope -> {
                    if (scope.getType().isInstanceOf(Map.class)) {
                        return scope.getTypeParameterFor(Map.class, 1);
                    }
                    return null;
                });

        SCHEMA_GENERATOR = new SchemaGenerator(configBuilder.build());
    }

    private ActionParameterSchemaGenerator() {

    }

    public static JsonNode generateSchema(Class<?> clazz) {
        return SCHEMA_GENERATOR.generateSchema(clazz);
    }

}
