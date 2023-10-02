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
package org.deltafi.core.converters;

import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.*;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

import static graphql.scalars.util.Kit.typeName;

@DgsScalar(name = "Duration")
public class DurationScalar implements Coercing<Duration, String> {

    @Override
    public String serialize(@NotNull Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof Duration) {
            return ((Duration) dataFetcherResult).toString();
        } else {
            throw new CoercingSerializeException("Not a valid Duration instance.");
        }
    }

    @NotNull
    @Override
    public Duration parseValue(@NotNull Object input) throws CoercingParseValueException {
        try {
            return Duration.parse((String) input);
        } catch (Exception e) {
            throw new CoercingParseValueException("Invalid duration format.");
        }
    }

    @NotNull
    @Override
    public Duration parseLiteral(@NotNull Object input) throws CoercingParseLiteralException {
        if (!(input instanceof StringValue)) {
            throw new CoercingParseLiteralException("Expected AST type 'StringValue' but was '" + typeName(input) + "'.");
        }
        try {
            return Duration.parse(((StringValue) input).getValue());
        } catch (Exception e) {
            throw new CoercingParseLiteralException("Invalid duration format in AST.");
        }
    }

    @NotNull
    @Override
    public Value valueToLiteral(@NotNull Object input) {
        if (input instanceof Duration) {
            return StringValue.of(((Duration) input).toString());
        } else {
            throw new CoercingSerializeException("Not a valid Duration instance.");
        }
    }
}
