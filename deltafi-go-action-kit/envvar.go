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
// ABOUTME: EnvVar parameter type that stores environment variable names.
// ABOUTME: Validates UPPER_SNAKE_CASE naming and resolves values at runtime.

package actionkit

import (
	"encoding/json"
	"fmt"
	"os"
	"regexp"

	"github.com/invopop/jsonschema"
)

var envVarPattern = regexp.MustCompile(`^[A-Z][A-Z0-9_]*$`)

// EnvVar stores a reference to an environment variable by name.
// Only the variable name (not the secret value) is stored and transmitted.
// The name must be UPPER_SNAKE_CASE (e.g. "DB_PASSWORD", "API_KEY").
// An empty string means no variable is configured.
type EnvVar string

// Resolve returns the environment variable's value.
// Returns an error if the variable name is not set or the environment variable is not defined.
func (e EnvVar) Resolve() (string, error) {
	if e == "" {
		return "", fmt.Errorf("no environment variable name configured")
	}
	val, ok := os.LookupEnv(string(e))
	if !ok {
		return "", fmt.Errorf("environment variable %q is not set", string(e))
	}
	return val, nil
}

// ResolveOrDefault returns the environment variable's value, or the default if
// the variable name is empty or the environment variable is not defined.
func (e EnvVar) ResolveOrDefault(defaultValue string) string {
	if e == "" {
		return defaultValue
	}
	if val, ok := os.LookupEnv(string(e)); ok {
		return val
	}
	return defaultValue
}

// IsSet returns true if an environment variable name has been configured (non-empty).
func (e EnvVar) IsSet() bool {
	return e != ""
}

// UnmarshalJSON validates that the value is a valid UPPER_SNAKE_CASE name.
func (e *EnvVar) UnmarshalJSON(data []byte) error {
	var s string
	if err := json.Unmarshal(data, &s); err != nil {
		return err
	}
	if s != "" && !envVarPattern.MatchString(s) {
		return fmt.Errorf("invalid environment variable name %q: must be UPPER_SNAKE_CASE (e.g. DB_PASSWORD)", s)
	}
	*e = EnvVar(s)
	return nil
}

// MarshalJSON serializes the EnvVar as a plain JSON string.
func (e EnvVar) MarshalJSON() ([]byte, error) {
	return json.Marshal(string(e))
}

// JSONSchema provides custom schema generation for EnvVar fields.
func (EnvVar) JSONSchema() *jsonschema.Schema {
	return &jsonschema.Schema{
		Type:    "string",
		Pattern: `^[A-Z][A-Z0-9_]*$`,
		Extras: map[string]any{
			"x-deltafi-type": "EnvVar",
		},
	}
}
