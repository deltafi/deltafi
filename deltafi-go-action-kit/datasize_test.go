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
// ABOUTME: Tests for the DataSize parameter type.
// ABOUTME: Covers parsing, string formatting, JSON serialization, and schema generation.

package actionkit

import (
	"encoding/json"
	"testing"
)

func TestParseDataSize_AllUnits(t *testing.T) {
	tests := []struct {
		input string
		want  int64
	}{
		{"0B", 0},
		{"1B", 1},
		{"100B", 100},
		{"1KB", 1024},
		{"512KB", 512 * 1024},
		{"1MB", 1024 * 1024},
		{"10MB", 10 * 1024 * 1024},
		{"1GB", 1024 * 1024 * 1024},
		{"2GB", 2 * 1024 * 1024 * 1024},
		{"1TB", 1024 * 1024 * 1024 * 1024},
	}

	for _, tt := range tests {
		d, err := ParseDataSize(tt.input)
		if err != nil {
			t.Errorf("ParseDataSize(%q) error: %v", tt.input, err)
			continue
		}
		if d.Bytes() != tt.want {
			t.Errorf("ParseDataSize(%q) = %d bytes, want %d", tt.input, d.Bytes(), tt.want)
		}
	}
}

func TestParseDataSize_CaseInsensitive(t *testing.T) {
	tests := []struct {
		input string
		want  int64
	}{
		{"10mb", 10 * 1024 * 1024},
		{"1kb", 1024},
		{"1gb", 1024 * 1024 * 1024},
		{"1tb", 1024 * 1024 * 1024 * 1024},
		{"100b", 100},
	}

	for _, tt := range tests {
		d, err := ParseDataSize(tt.input)
		if err != nil {
			t.Errorf("ParseDataSize(%q) error: %v", tt.input, err)
			continue
		}
		if d.Bytes() != tt.want {
			t.Errorf("ParseDataSize(%q) = %d bytes, want %d", tt.input, d.Bytes(), tt.want)
		}
	}
}

func TestParseDataSize_Invalid(t *testing.T) {
	invalid := []string{
		"",
		"MB",
		"10",
		"10PB",
		"10XB",
		"abc",
		"-1MB",
	}

	for _, input := range invalid {
		_, err := ParseDataSize(input)
		if err == nil {
			t.Errorf("ParseDataSize(%q) should have returned error", input)
		}
	}
}

func TestDataSize_String(t *testing.T) {
	tests := []struct {
		size DataSize
		want string
	}{
		{0, "0B"},
		{1, "1B"},
		{100, "100B"},
		{1024, "1KB"},
		{512 * 1024, "512KB"},
		{1024 * 1024, "1MB"},
		{10 * 1024 * 1024, "10MB"},
		{1024 * 1024 * 1024, "1GB"},
		{1024 * 1024 * 1024 * 1024, "1TB"},
		// Non-even division stays at smaller unit
		{1024 + 1, "1025B"},
		{1024*1024 + 1024, "1025KB"},
	}

	for _, tt := range tests {
		if got := tt.size.String(); got != tt.want {
			t.Errorf("DataSize(%d).String() = %q, want %q", tt.size, got, tt.want)
		}
	}
}

func TestDataSize_Bytes(t *testing.T) {
	d := DataSize(1024 * 1024)
	if d.Bytes() != 1048576 {
		t.Errorf("Bytes() = %d, want 1048576", d.Bytes())
	}
}

func TestDataSize_Constants(t *testing.T) {
	if Byte != 1 {
		t.Errorf("Byte = %d, want 1", Byte)
	}
	if Kilobyte != 1024 {
		t.Errorf("Kilobyte = %d, want 1024", Kilobyte)
	}
	if Megabyte != 1024*1024 {
		t.Errorf("Megabyte = %d, want %d", Megabyte, 1024*1024)
	}
	if Gigabyte != 1024*1024*1024 {
		t.Errorf("Gigabyte = %d, want %d", Gigabyte, 1024*1024*1024)
	}
	if Terabyte != 1024*1024*1024*1024 {
		t.Errorf("Terabyte = %d, want %d", Terabyte, 1024*1024*1024*1024)
	}
}

func TestDataSize_JSONRoundTrip(t *testing.T) {
	tests := []struct {
		input    string
		wantJSON string
	}{
		{"10MB", `"10MB"`},
		{"512KB", `"512KB"`},
		{"1GB", `"1GB"`},
		{"100B", `"100B"`},
	}

	for _, tt := range tests {
		d, _ := ParseDataSize(tt.input)

		data, err := json.Marshal(d)
		if err != nil {
			t.Errorf("Marshal(%q) error: %v", tt.input, err)
			continue
		}
		if string(data) != tt.wantJSON {
			t.Errorf("Marshal(%q) = %s, want %s", tt.input, data, tt.wantJSON)
		}

		var d2 DataSize
		if err := json.Unmarshal(data, &d2); err != nil {
			t.Errorf("Unmarshal(%s) error: %v", data, err)
			continue
		}
		if d != d2 {
			t.Errorf("round-trip: got %d, want %d", d2, d)
		}
	}
}

func TestDataSize_UnmarshalJSON(t *testing.T) {
	type config struct {
		MaxSize DataSize `json:"maxSize"`
	}

	var c config
	if err := json.Unmarshal([]byte(`{"maxSize":"10MB"}`), &c); err != nil {
		t.Fatalf("unmarshal error: %v", err)
	}
	if c.MaxSize.Bytes() != 10*1024*1024 {
		t.Errorf("MaxSize = %d, want %d", c.MaxSize.Bytes(), 10*1024*1024)
	}
}

func TestDataSize_UnmarshalJSON_Invalid(t *testing.T) {
	var d DataSize
	if err := json.Unmarshal([]byte(`"10PB"`), &d); err == nil {
		t.Error("expected error for invalid unit")
	}
	if err := json.Unmarshal([]byte(`""`), &d); err == nil {
		t.Error("expected error for empty string")
	}
}

func TestDataSize_Schema(t *testing.T) {
	type params struct {
		MaxSize DataSize `json:"maxSize"`
	}
	schema := GenerateSchema(params{})
	props := schema["properties"].(map[string]any)
	sizeProp := props["maxSize"].(map[string]any)

	if sizeProp["type"] != "string" {
		t.Errorf("type = %v, want string", sizeProp["type"])
	}
	if sizeProp["pattern"] != `^[0-9]+(B|KB|MB|GB|TB)$` {
		t.Errorf("pattern = %v, want ^[0-9]+(B|KB|MB|GB|TB)$", sizeProp["pattern"])
	}
	if sizeProp["x-deltafi-type"] != "DataSize" {
		t.Errorf("x-deltafi-type = %v, want DataSize", sizeProp["x-deltafi-type"])
	}
}

func TestDataSize_SchemaWithDescription(t *testing.T) {
	type params struct {
		Limit DataSize `json:"limit" jsonschema:"description=Maximum file size"`
	}
	schema := GenerateSchema(params{})
	props := schema["properties"].(map[string]any)
	limitProp := props["limit"].(map[string]any)

	if limitProp["x-deltafi-type"] != "DataSize" {
		t.Errorf("x-deltafi-type = %v, want DataSize", limitProp["x-deltafi-type"])
	}
}

func TestDataSize_ParseParameters(t *testing.T) {
	type params struct {
		MaxSize DataSize `json:"maxSize"`
		Name    string   `json:"name"`
	}

	raw := map[string]any{
		"maxSize": "10MB",
		"name":    "test",
	}

	var p params
	if err := parseRawParams(raw, &p); err != nil {
		t.Fatalf("parseRawParams error: %v", err)
	}
	if p.MaxSize.Bytes() != 10*1024*1024 {
		t.Errorf("MaxSize = %d, want %d", p.MaxSize.Bytes(), 10*1024*1024)
	}
	if p.Name != "test" {
		t.Errorf("Name = %q, want %q", p.Name, "test")
	}
}

func TestEnvVar_ParseParameters(t *testing.T) {
	type params struct {
		ApiKey EnvVar `json:"apiKey"`
		Name   string `json:"name"`
	}

	raw := map[string]any{
		"apiKey": "MY_API_KEY",
		"name":   "test",
	}

	var p params
	if err := parseRawParams(raw, &p); err != nil {
		t.Fatalf("parseRawParams error: %v", err)
	}
	if string(p.ApiKey) != "MY_API_KEY" {
		t.Errorf("ApiKey = %q, want %q", p.ApiKey, "MY_API_KEY")
	}
}

func TestEnvVar_ParseParameters_InvalidName(t *testing.T) {
	type params struct {
		ApiKey EnvVar `json:"apiKey"`
	}

	raw := map[string]any{
		"apiKey": "invalid_name",
	}

	var p params
	if err := parseRawParams(raw, &p); err == nil {
		t.Error("expected error for invalid EnvVar name in parseRawParams")
	}
}
