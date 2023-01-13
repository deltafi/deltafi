/**
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
package org.deltafi.core.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.*;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.util.HashMap;
import java.util.Map;

import static graphql.scalars.util.Kit.typeName;

@DgsScalar(name = "Metadata")
public class MetadataScalar implements Coercing<Map<String, String>, Object> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof Map) {
            return dataFetcherResult;
        } else {
            throw new CoercingSerializeException("Not a valid Map<String, String>");
        }
    }

    @Override
    public Map<String, String> parseValue(Object input) throws CoercingParseValueException {
        return OBJECT_MAPPER.convertValue(input, new TypeReference<>() {});
    }

    @Override
    public Map<String, String> parseLiteral(Object input) throws CoercingParseLiteralException {
        if (!(input instanceof Value)) {
            throw new CoercingParseLiteralException("Expected AST type 'Value' but was '" + typeName(input) + "'.");
        }

        if (input instanceof NullValue) {
            return null;
        } else if (input instanceof StringValue) {
            try {
                return OBJECT_MAPPER.readValue(((StringValue) input).getValue(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                throw new CoercingParseLiteralException("Value is not a Map<String, String>");
            }
        } else if (input instanceof ObjectValue){
            ObjectValue objectInput = (ObjectValue) input;
            Map<String, String> mapped = new HashMap<>();
            objectInput.getObjectFields().stream().forEach(field -> addObjectField(field, mapped));
            return mapped;
        }
        throw new CoercingParseLiteralException("Value is not a Map<String, String>");
    }

    private void addObjectField(ObjectField objectField, Map<String, String> resultMap) {
        if (objectField.getValue() instanceof NullValue) {
            return;
        }

        if (!(objectField.getValue() instanceof StringValue)) {
            throw new CoercingParseLiteralException("Value is not a Map<String, String>");
        }

        resultMap.put(objectField.getName(), ((StringValue) objectField.getValue()).getValue());
    }
}
