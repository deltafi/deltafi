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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvVarTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testValidEnvVarNames() {
        // All valid UPPER_SNAKE_CASE names
        assertThat(new EnvVar("PASSWORD").getName()).isEqualTo("PASSWORD");
        assertThat(new EnvVar("SFTP_PASSWORD").getName()).isEqualTo("SFTP_PASSWORD");
        assertThat(new EnvVar("MY_SECRET_KEY_123").getName()).isEqualTo("MY_SECRET_KEY_123");
        assertThat(new EnvVar("A").getName()).isEqualTo("A");
        assertThat(new EnvVar("DB2_PASSWORD").getName()).isEqualTo("DB2_PASSWORD");
    }

    @Test
    void testInvalidEnvVarNames() {
        // lowercase
        assertThatThrownBy(() -> new EnvVar("password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UPPER_SNAKE_CASE");

        // mixed case
        assertThatThrownBy(() -> new EnvVar("MyPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UPPER_SNAKE_CASE");

        // starts with number
        assertThatThrownBy(() -> new EnvVar("123_PASSWORD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UPPER_SNAKE_CASE");

        // contains special characters (likely an actual password)
        assertThatThrownBy(() -> new EnvVar("MyP@ssword123!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UPPER_SNAKE_CASE");

        // spaces
        assertThatThrownBy(() -> new EnvVar("MY PASSWORD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UPPER_SNAKE_CASE");

        // hyphens
        assertThatThrownBy(() -> new EnvVar("MY-PASSWORD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UPPER_SNAKE_CASE");
    }

    @Test
    void testNullAndEmptyAllowed() {
        // null and empty are allowed (means not configured)
        EnvVar envVar = new EnvVar();
        envVar.setName(null);
        assertThat(envVar.getName()).isNull();

        envVar.setName("");
        assertThat(envVar.getName()).isEmpty();
    }

    @Test
    void testIsSet() {
        assertThat(new EnvVar("PASSWORD").isSet()).isTrue();
        assertThat(new EnvVar(null).isSet()).isFalse();
        assertThat(new EnvVar("").isSet()).isFalse();

        EnvVar envVar = new EnvVar();
        assertThat(envVar.isSet()).isFalse();
    }

    @Test
    void testResolveWithExistingEnvVar() {
        // PATH should exist on all systems
        EnvVar envVar = new EnvVar("PATH");
        String resolved = envVar.resolve();
        assertThat(resolved).isNotNull().isNotEmpty();
    }

    @Test
    void testResolveWithMissingEnvVar() {
        EnvVar envVar = new EnvVar("THIS_ENV_VAR_SHOULD_NOT_EXIST_ANYWHERE_12345");
        assertThatThrownBy(envVar::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Environment variable not set");
    }

    @Test
    void testResolveOrDefault() {
        // Existing env var
        EnvVar pathVar = new EnvVar("PATH");
        assertThat(pathVar.resolveOrDefault("default")).isNotEqualTo("default");

        // Non-existing env var
        EnvVar missingVar = new EnvVar("THIS_ENV_VAR_SHOULD_NOT_EXIST_ANYWHERE_12345");
        assertThat(missingVar.resolveOrDefault("default")).isEqualTo("default");

        // Not set
        EnvVar notSet = new EnvVar();
        assertThat(notSet.resolveOrDefault("default")).isEqualTo("default");
    }

    @Test
    void testResolveNullOrEmpty() {
        EnvVar nullName = new EnvVar();
        nullName.setName(null);
        assertThat(nullName.resolve()).isNull();

        EnvVar emptyName = new EnvVar();
        emptyName.setName("");
        assertThat(emptyName.resolve()).isNull();
    }

    @Test
    void testJsonSerialization() throws Exception {
        // EnvVar serializes as a simple string (just the env var name)
        EnvVar envVar = new EnvVar("SFTP_PASSWORD");
        String json = objectMapper.writeValueAsString(envVar);
        assertThat(json).isEqualTo("\"SFTP_PASSWORD\"");
    }

    @Test
    void testJsonDeserialization() throws Exception {
        // EnvVar deserializes from a simple string
        String json = "\"DATABASE_PASSWORD\"";
        EnvVar envVar = objectMapper.readValue(json, EnvVar.class);
        assertThat(envVar.getName()).isEqualTo("DATABASE_PASSWORD");
    }

    @Test
    void testJsonDeserializationWithInvalidName() {
        String json = "\"invalid_lowercase\"";
        assertThatThrownBy(() -> objectMapper.readValue(json, EnvVar.class))
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
