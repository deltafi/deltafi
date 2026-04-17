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
// ABOUTME: Tests for the EnvVar parameter type.
// ABOUTME: Covers validation, resolution, JSON serialization, and schema generation.

package actionkit

import (
	"encoding/json"
	"os"
	"testing"
)

func TestEnvVar_ValidNames(t *testing.T) {
	valid := []string{
		"PASSWORD",
		"SFTP_PASSWORD",
		"DB2_PASSWORD",
		"A",
		"ABC",
		"A1",
		"A_B_C",
		"MY_LONG_ENV_VAR_123",
	}
	for _, name := range valid {
		var e EnvVar
		data, _ := json.Marshal(name)
		if err := json.Unmarshal(data, &e); err != nil {
			t.Errorf("expected %q to be valid, got error: %v", name, err)
		}
		if string(e) != name {
			t.Errorf("got %q, want %q", string(e), name)
		}
	}
}

func TestEnvVar_InvalidNames(t *testing.T) {
	invalid := []string{
		"password",   // lowercase
		"myVar",      // mixed case
		"2PASSWORD",  // starts with number
		"MY-VAR",     // contains hyphen
		"MY VAR",     // contains space
		"my_var",     // lowercase with underscore
		"_MY_VAR",    // starts with underscore
		"MY.VAR",     // contains dot
		"MY@VAR",     // contains special char
		"MixedCase",  // mixed case
		"camelCase",  // camel case
		"UPPER-CASE", // hyphen
		"UPPER CASE", // space
	}
	for _, name := range invalid {
		var e EnvVar
		data, _ := json.Marshal(name)
		if err := json.Unmarshal(data, &e); err == nil {
			t.Errorf("expected %q to be invalid, but got no error", name)
		}
	}
}

func TestEnvVar_EmptyIsAllowed(t *testing.T) {
	var e EnvVar
	if err := json.Unmarshal([]byte(`""`), &e); err != nil {
		t.Errorf("empty string should be allowed: %v", err)
	}
	if e != "" {
		t.Errorf("got %q, want empty", string(e))
	}
}

func TestEnvVar_IsSet(t *testing.T) {
	e := EnvVar("")
	if e.IsSet() {
		t.Error("empty EnvVar should not be set")
	}

	e = EnvVar("MY_VAR")
	if !e.IsSet() {
		t.Error("non-empty EnvVar should be set")
	}
}

func TestEnvVar_Resolve(t *testing.T) {
	// Empty name
	e := EnvVar("")
	_, err := e.Resolve()
	if err == nil {
		t.Error("expected error for empty EnvVar name")
	}

	// Set an env var and resolve it
	os.Setenv("ACTIONKIT_TEST_VAR", "secret123")
	defer os.Unsetenv("ACTIONKIT_TEST_VAR")

	e = EnvVar("ACTIONKIT_TEST_VAR")
	val, err := e.Resolve()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if val != "secret123" {
		t.Errorf("got %q, want %q", val, "secret123")
	}

	// Unset var
	e = EnvVar("ACTIONKIT_MISSING_VAR")
	_, err = e.Resolve()
	if err == nil {
		t.Error("expected error for missing env var")
	}
}

func TestEnvVar_ResolveOrDefault(t *testing.T) {
	// Empty name returns default
	e := EnvVar("")
	if got := e.ResolveOrDefault("fallback"); got != "fallback" {
		t.Errorf("got %q, want %q", got, "fallback")
	}

	// Set var returns value
	os.Setenv("ACTIONKIT_TEST_VAR2", "real_value")
	defer os.Unsetenv("ACTIONKIT_TEST_VAR2")

	e = EnvVar("ACTIONKIT_TEST_VAR2")
	if got := e.ResolveOrDefault("fallback"); got != "real_value" {
		t.Errorf("got %q, want %q", got, "real_value")
	}

	// Missing var returns default
	e = EnvVar("ACTIONKIT_MISSING_VAR2")
	if got := e.ResolveOrDefault("fallback"); got != "fallback" {
		t.Errorf("got %q, want %q", got, "fallback")
	}
}

func TestEnvVar_JSONRoundTrip(t *testing.T) {
	e := EnvVar("MY_SECRET")
	data, err := json.Marshal(e)
	if err != nil {
		t.Fatalf("marshal error: %v", err)
	}
	if string(data) != `"MY_SECRET"` {
		t.Errorf("marshaled = %s, want %q", data, `"MY_SECRET"`)
	}

	var e2 EnvVar
	if err := json.Unmarshal(data, &e2); err != nil {
		t.Fatalf("unmarshal error: %v", err)
	}
	if e != e2 {
		t.Errorf("round-trip: got %q, want %q", e2, e)
	}
}

func TestEnvVar_Schema(t *testing.T) {
	type params struct {
		Secret EnvVar `json:"secret"`
	}
	schema := GenerateSchema(params{})
	props := schema["properties"].(map[string]any)
	secretProp := props["secret"].(map[string]any)

	if secretProp["type"] != "string" {
		t.Errorf("type = %v, want string", secretProp["type"])
	}
	if secretProp["pattern"] != `^[A-Z][A-Z0-9_]*$` {
		t.Errorf("pattern = %v, want ^[A-Z][A-Z0-9_]*$", secretProp["pattern"])
	}
	if secretProp["x-deltafi-type"] != "EnvVar" {
		t.Errorf("x-deltafi-type = %v, want EnvVar", secretProp["x-deltafi-type"])
	}
}

func TestEnvVar_SchemaInStruct(t *testing.T) {
	type params struct {
		Name   string `json:"name"`
		ApiKey EnvVar `json:"apiKey" description:"API key env var"`
	}
	schema := GenerateSchema(params{})
	props := schema["properties"].(map[string]any)

	// Regular string field
	nameProp := props["name"].(map[string]any)
	if nameProp["type"] != "string" {
		t.Errorf("name type = %v, want string", nameProp["type"])
	}
	if _, ok := nameProp["x-deltafi-type"]; ok {
		t.Error("name should not have x-deltafi-type")
	}

	// EnvVar field
	apiProp := props["apiKey"].(map[string]any)
	if apiProp["x-deltafi-type"] != "EnvVar" {
		t.Errorf("apiKey x-deltafi-type = %v, want EnvVar", apiProp["x-deltafi-type"])
	}
}
