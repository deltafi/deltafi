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
package org.deltafi.core.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Polymorphic serialization roundtrip tests for the {@link DataSource} hierarchy,
 * guarding against any Jackson 3 polymorphic-encoding regression on the
 * {@code @JsonTypeInfo} + {@code @JsonSubTypes} pair declared on the abstract base.
 */
class DataSourcePolymorphismTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("RestDataSource polymorphic discriminator is encoded in JSON")
    void restDataSourceDiscriminator() {
        RestDataSource source = new RestDataSource();
        source.setTopic("ingress-topic");
        source.setMaxErrors(5);
        source.setMetadata(Map.of("key", "value"));

        String json = mapper.writeValueAsString((DataSource) source);

        assertThat(json).contains("\"type\":\"REST_DATA_SOURCE\"");
    }

    @Test
    @DisplayName("RestDataSource roundtrips through abstract DataSource type")
    void restDataSourceRoundtripViaAbstract() {
        RestDataSource source = new RestDataSource();
        source.setName("my-rest-source");
        source.setTopic("ingress-topic");
        source.setMaxErrors(5);
        source.setMetadata(Map.of("k", "v"));

        String json = mapper.writeValueAsString((DataSource) source);
        DataSource deserialized = mapper.readValue(json, DataSource.class);

        assertThat(deserialized).isInstanceOf(RestDataSource.class);
        RestDataSource actual = (RestDataSource) deserialized;
        assertThat(actual.getName()).isEqualTo("my-rest-source");
        assertThat(actual.getTopic()).isEqualTo("ingress-topic");
        assertThat(actual.getMaxErrors()).isEqualTo(5);
        assertThat(actual.getMetadata()).containsEntry("k", "v");
    }
}
