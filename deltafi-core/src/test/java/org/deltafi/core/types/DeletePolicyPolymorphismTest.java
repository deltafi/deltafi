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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Polymorphic serialization roundtrip tests for the {@link DeletePolicy} hierarchy,
 * guarding against any Jackson 3 polymorphic-encoding regression.
 */
class DeletePolicyPolymorphismTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("TimedDeletePolicy roundtrips through abstract DeletePolicy type")
    void timedDeletePolicyRoundtripViaAbstract() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000001234");
        TimedDeletePolicy original = TimedDeletePolicy.builder()
                .id(id)
                .name("retain-30d")
                .enabled(true)
                .flow("transform-flow")
                .afterCreate("P30D")
                .afterComplete("P7D")
                .minBytes(1024L)
                .deleteMetadata(true)
                .build();

        String json = mapper.writeValueAsString((DeletePolicy) original);
        DeletePolicy deserialized = mapper.readValue(json, DeletePolicy.class);

        assertThat(deserialized).isInstanceOf(TimedDeletePolicy.class).isEqualTo(original);
        TimedDeletePolicy actual = (TimedDeletePolicy) deserialized;
        assertThat(actual.getName()).isEqualTo("retain-30d");
        assertThat(actual.getAfterCreate()).isEqualTo("P30D");
        assertThat(actual.getAfterComplete()).isEqualTo("P7D");
        assertThat(actual.getMinBytes()).isEqualTo(1024L);
        assertThat(actual.isDeleteMetadata()).isTrue();
        assertThat(actual.isEnabled()).isTrue();
        assertThat(actual.getFlow()).isEqualTo("transform-flow");
    }

    @Test
    @DisplayName("polymorphic JSON encodes the __typename discriminator")
    void polymorphicJsonHasDiscriminator() {
        TimedDeletePolicy policy = TimedDeletePolicy.builder()
                .name("p")
                .enabled(false)
                .afterCreate("P1D")
                .build();

        String json = mapper.writeValueAsString((DeletePolicy) policy);

        assertThat(json).contains("\"__typename\":\"TimedDeletePolicy\"");
    }

    @Test
    @DisplayName("concrete TimedDeletePolicy roundtrips directly via concrete type")
    void timedDeletePolicyConcreteRoundtrip() {
        TimedDeletePolicy original = TimedDeletePolicy.builder()
                .name("retain-7d")
                .enabled(false)
                .afterCreate("P7D")
                .minBytes(2048L)
                .deleteMetadata(false)
                .build();

        String json = mapper.writeValueAsString(original);
        TimedDeletePolicy deserialized = mapper.readValue(json, TimedDeletePolicy.class);

        assertThat(deserialized).isEqualTo(original);
    }
}
