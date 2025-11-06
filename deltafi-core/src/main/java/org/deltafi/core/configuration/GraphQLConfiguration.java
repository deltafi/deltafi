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
package org.deltafi.core.configuration;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsRuntimeWiring;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.RuntimeWiring;
import org.deltafi.core.graphql.MetadataScalar;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphQLConfiguration {
    @DgsComponent
    public static class MetadataScalarRegistration {
        @DgsRuntimeWiring
        public RuntimeWiring.Builder addScalar(RuntimeWiring.Builder builder) {
            return builder.scalar(GraphQLScalarType.newScalar()
                    .name("Metadata")
                    .description("A custom scalar that represents Metadata")
                    .coercing(new MetadataScalar())
                    .build()
            );
        }
    }
}
