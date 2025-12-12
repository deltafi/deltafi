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

// ABOUTME: A parameter type that references an environment variable by name.
// ABOUTME: The actual secret value is resolved at runtime, never stored in DeltaFi.

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * References a secret value stored in an environment variable.
 * <p>
 * This type ensures that sensitive values like passwords and API keys are never
 * stored in DeltaFi's configuration or transmitted through message queues. Only
 * the environment variable NAME is stored; the actual value is resolved at
 * runtime on the worker where the action executes.
 * <p>
 * Environment variable names must follow the standard UPPER_SNAKE_CASE convention
 * (e.g., SFTP_PASSWORD, DATABASE_API_KEY) to prevent accidental entry of actual
 * secret values.
 * <p>
 * Serializes as a simple string (the env var name), not as an object.
 */
@Data
@NoArgsConstructor
public class EnvVar {
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private String name;

    @JsonCreator
    public EnvVar(String name) {
        setName(name);
    }

    /**
     * Returns the env var name for JSON serialization.
     */
    @JsonValue
    public String getName() {
        return name;
    }

    /**
     * Sets the environment variable name with validation.
     * @param name the environment variable name (must be UPPER_SNAKE_CASE)
     * @throws IllegalArgumentException if name doesn't match UPPER_SNAKE_CASE pattern
     */
    public void setName(String name) {
        if (name != null && !name.isEmpty() && !ENV_VAR_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Environment variable name must be UPPER_SNAKE_CASE (e.g., MY_SECRET_KEY), got: " + name +
                    ". This field expects the NAME of an environment variable, not the actual secret value.");
        }
        this.name = name;
    }

    /**
     * Resolves the environment variable to its actual value.
     * @return the value of the environment variable
     * @throws IllegalStateException if the environment variable is not set
     */
    public String resolve() {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException("Environment variable not set: " + name);
        }
        return value;
    }

    /**
     * Resolves the environment variable, returning a default value if not set.
     * @param defaultValue value to return if the environment variable is not set
     * @return the value of the environment variable, or defaultValue if not set
     */
    public String resolveOrDefault(String defaultValue) {
        if (name == null || name.isEmpty()) {
            return defaultValue;
        }
        String value = System.getenv(name);
        return value != null ? value : defaultValue;
    }

    /**
     * Checks if this EnvVar reference is configured (has a name set).
     * @return true if a name is set, false otherwise
     */
    @JsonIgnore
    public boolean isSet() {
        return name != null && !name.isEmpty();
    }
}
