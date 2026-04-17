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
// ABOUTME: Tests for plugin registration payload building and HTTP posting.
// ABOUTME: Verifies buildRegistration output and postRegistration with httptest server.

package actionkit

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

type regTestTransformAction struct{}

func (a *regTestTransformAction) Transform(df *Deltafile, params noParams) *Deltafile {
	return df
}

type regTestEgressAction struct{}

func (a *regTestEgressAction) Egress(df *Deltafile, params noParams) *Deltafile {
	return df
}

func registerAction(t *testing.T, p *plugin, reg *Registration) {
	t.Helper()
	if err := p.RegisterAction(reg); err != nil {
		t.Fatalf("RegisterAction: %v", err)
	}
}

func TestBuildRegistration(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	transformReg := RegisterTransform("Transform1", &regTestTransformAction{})
	transformReg.Describe("test transform").WithParams(simpleSchemaParams{})

	egressReg := RegisterEgress("Egress1", &regTestEgressAction{})

	p := &plugin{
		config: PluginConfig{
			Image:       "my-image:latest",
			DisplayName: "Test Plugin",
			Description: "A test",
		},
		coordinates: PluginCoordinates{
			GroupID:    "org.test",
			ArtifactID: "test-plugin",
			Version:    "2.0.0",
		},
		flows: []FlowPlan{
			{Name: "flow1", Type: "TRANSFORM", Raw: json.RawMessage(`{"name":"flow1","type":"TRANSFORM"}`)},
		},
		variables: []Variable{
			{Name: "var1", DataType: "STRING"},
		},
	}
	registerAction(t, p, transformReg)
	registerAction(t, p, egressReg)

	reg := buildRegistration(p)

	if reg.Image != "my-image:latest" {
		t.Errorf("Image = %q", reg.Image)
	}
	if reg.DisplayName != "Test Plugin" {
		t.Errorf("DisplayName = %q", reg.DisplayName)
	}
	if reg.ActionKitVersion != Version {
		t.Errorf("ActionKitVersion = %q, want %q", reg.ActionKitVersion, Version)
	}
	if reg.PluginCoordinates.GroupID != "org.test" {
		t.Errorf("GroupID = %q", reg.PluginCoordinates.GroupID)
	}
	if reg.PluginCoordinates.ArtifactID != "test-plugin" {
		t.Errorf("ArtifactID = %q", reg.PluginCoordinates.ArtifactID)
	}
	if reg.PluginCoordinates.Version != "2.0.0" {
		t.Errorf("Version = %q", reg.PluginCoordinates.Version)
	}

	// Actions
	if len(reg.Actions) != 2 {
		t.Fatalf("Actions count = %d, want 2", len(reg.Actions))
	}

	a0 := reg.Actions[0]
	if a0.Name != "Transform1" {
		t.Errorf("action[0] Name = %q", a0.Name)
	}
	if a0.Type != ActionTypeTransform {
		t.Errorf("action[0] Type = %q", a0.Type)
	}
	if a0.Description != "test transform" {
		t.Errorf("action[0] Description = %q", a0.Description)
	}
	// Schema should have been generated from simpleSchemaParams
	if a0.Schema["type"] != "object" {
		t.Errorf("action[0] schema type = %v", a0.Schema["type"])
	}
	if _, ok := a0.Schema["properties"]; !ok {
		t.Error("action[0] schema should have properties")
	}

	a1 := reg.Actions[1]
	if a1.Name != "Egress1" {
		t.Errorf("action[1] Name = %q", a1.Name)
	}
	if a1.Description != "No description provided" {
		t.Errorf("action[1] Description = %q, want default", a1.Description)
	}
	// No Parameterized, so schema should be empty
	if len(a1.Schema) != 0 {
		t.Errorf("action[1] schema should be empty, got %v", a1.Schema)
	}

	// Flows
	if len(reg.FlowPlans) != 1 {
		t.Fatalf("FlowPlans count = %d, want 1", len(reg.FlowPlans))
	}
	if string(reg.FlowPlans[0]) != `{"name":"flow1","type":"TRANSFORM"}` {
		t.Errorf("FlowPlans[0] = %s", string(reg.FlowPlans[0]))
	}

	// Variables
	if len(reg.Variables) != 1 {
		t.Fatalf("Variables count = %d, want 1", len(reg.Variables))
	}
	if reg.Variables[0].Name != "var1" {
		t.Errorf("Variables[0].Name = %q", reg.Variables[0].Name)
	}

	// Dependencies should be empty, not nil
	if reg.Dependencies == nil {
		t.Error("Dependencies should not be nil")
	}
	if len(reg.Dependencies) != 0 {
		t.Errorf("Dependencies count = %d, want 0", len(reg.Dependencies))
	}
}

func TestBuildRegistration_EmptyPlugin(t *testing.T) {
	p := &plugin{
		coordinates: PluginCoordinates{GroupID: "g", ArtifactID: "a", Version: "v"},
	}

	reg := buildRegistration(p)

	if len(reg.Actions) != 0 {
		t.Errorf("Actions count = %d, want 0", len(reg.Actions))
	}
	if len(reg.FlowPlans) != 0 {
		t.Errorf("FlowPlans count = %d, want 0", len(reg.FlowPlans))
	}
	if reg.Variables != nil {
		t.Errorf("Variables should be nil, got %v", reg.Variables)
	}
}

func TestBuildRegistration_JSONSerializable(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	reg := RegisterTransform("Act", &regTestTransformAction{})
	reg.Describe("test").WithParams(simpleSchemaParams{})

	p := &plugin{
		coordinates: PluginCoordinates{GroupID: "g", ArtifactID: "a", Version: "1.0"},
		flows: []FlowPlan{
			{Name: "f", Type: "TRANSFORM", Raw: json.RawMessage(`{"name":"f","type":"TRANSFORM"}`)},
		},
	}
	registerAction(t, p, reg)

	pluginReg := buildRegistration(p)

	data, err := json.Marshal(pluginReg)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	// Verify it's valid JSON
	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	// Check key fields exist
	if parsed["pluginCoordinates"] == nil {
		t.Error("pluginCoordinates missing")
	}
	if parsed["actions"] == nil {
		t.Error("actions missing")
	}
	if parsed["actionKitVersion"] != Version {
		t.Errorf("actionKitVersion = %v, want %v", parsed["actionKitVersion"], Version)
	}
}

type regTestJoinTransformAction struct{}

func (a *regTestJoinTransformAction) Transform(df *Deltafile, params noParams) *Deltafile {
	return df
}

func (a *regTestJoinTransformAction) Join(inputs []*Deltafile, joined *Deltafile, params noParams) *Deltafile {
	for _, input := range inputs {
		joined.AddContentSlice(input.GetContentSlice())
		joined.AddMetadataMap(input.GetMetadataMap())
	}
	return joined
}

func TestBuildRegistration_WithActionOptions(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	withOptsReg := RegisterTransform("WithOpts", &regTestTransformAction{})
	withOptsReg.Describe("Transform with options").WithOptions(ActionOptions{
		Description: "Transform with options",
		InputSpec:   &InputSpec{ContentSummary: "Single text input"},
		OutputSpec:  &OutputSpec{ContentSummary: "Single text output", Passthrough: true},
		Filters:     SimpleFilters("Prefix match"),
		Errors:      SimpleErrors("No content", "Parse failure"),
		Notes:       []string{"Reference implementation"},
	})

	noOptsReg := RegisterEgress("NoOpts", &regTestEgressAction{})
	noOptsReg.Describe("plain")

	p := &plugin{
		coordinates: PluginCoordinates{GroupID: "g", ArtifactID: "a", Version: "v"},
	}
	registerAction(t, p, withOptsReg)
	registerAction(t, p, noOptsReg)

	reg := buildRegistration(p)

	// Action with options
	a0 := reg.Actions[0]
	if a0.Options == nil {
		t.Fatal("action[0] Options should not be nil")
	}
	if a0.Options.Description != "Transform with options" {
		t.Errorf("Options.Description = %q", a0.Options.Description)
	}
	if a0.Options.InputSpec == nil || a0.Options.InputSpec.ContentSummary != "Single text input" {
		t.Error("Options.InputSpec not set correctly")
	}
	if a0.Options.OutputSpec == nil || !a0.Options.OutputSpec.Passthrough {
		t.Error("Options.OutputSpec.Passthrough should be true")
	}
	if len(a0.Options.Filters) != 1 {
		t.Errorf("Options.Filters len = %d, want 1", len(a0.Options.Filters))
	}
	if len(a0.Options.Errors) != 2 {
		t.Errorf("Options.Errors len = %d, want 2", len(a0.Options.Errors))
	}
	if len(a0.Options.Notes) != 1 {
		t.Errorf("Options.Notes len = %d, want 1", len(a0.Options.Notes))
	}

	// Action without options
	a1 := reg.Actions[1]
	if a1.Options != nil {
		t.Error("action[1] Options should be nil")
	}

	// Verify JSON serialization includes actionOptions
	data, err := json.Marshal(reg)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	var parsed map[string]any
	json.Unmarshal(data, &parsed)
	actions := parsed["actions"].([]any)
	act0 := actions[0].(map[string]any)
	if act0["actionOptions"] == nil {
		t.Error("JSON should include actionOptions for action[0]")
	}
	act1 := actions[1].(map[string]any)
	if _, exists := act1["actionOptions"]; exists {
		t.Error("JSON should omit actionOptions for action[1]")
	}
}

func TestBuildRegistration_SupportsJoin(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	noJoinReg := RegisterTransform("NoJoin", &regTestTransformAction{})
	noJoinReg.Describe("test")

	withJoinReg := RegisterJoinTransform("WithJoin", &regTestJoinTransformAction{})
	withJoinReg.Describe("test")

	p := &plugin{
		coordinates: PluginCoordinates{GroupID: "g", ArtifactID: "a", Version: "v"},
	}
	registerAction(t, p, noJoinReg)
	registerAction(t, p, withJoinReg)

	reg := buildRegistration(p)

	if reg.Actions[0].SupportsJoin {
		t.Error("NoJoin action should have SupportsJoin=false")
	}
	if !reg.Actions[1].SupportsJoin {
		t.Error("WithJoin action should have SupportsJoin=true")
	}
}

func TestPostRegistration_Success(t *testing.T) {
	var receivedBody []byte
	var receivedContentType string

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		receivedContentType = r.Header.Get("Content-Type")
		receivedBody, _ = io.ReadAll(r.Body)
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"ok"}`))
	}))
	defer server.Close()

	reg := pluginRegistration{
		PluginCoordinates: PluginCoordinates{GroupID: "g", ArtifactID: "a", Version: "v"},
		Actions:           []actionDescriptor{},
		Dependencies:      []PluginCoordinates{},
		FlowPlans:         []json.RawMessage{},
	}

	err := postRegistration(context.Background(), server.URL, reg)
	if err != nil {
		t.Fatalf("postRegistration: %v", err)
	}

	if receivedContentType != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", receivedContentType)
	}

	// Verify body is valid JSON
	var parsed map[string]any
	if err := json.Unmarshal(receivedBody, &parsed); err != nil {
		t.Fatalf("body is not valid JSON: %v", err)
	}

	coords := parsed["pluginCoordinates"].(map[string]any)
	if coords["groupId"] != "g" {
		t.Errorf("body groupId = %v", coords["groupId"])
	}
}

func TestPostRegistration_ServerError_Retries(t *testing.T) {
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		attempts++
		if attempts < 3 {
			w.WriteHeader(http.StatusInternalServerError)
			w.Write([]byte(`{"error":"not ready"}`))
			return
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	reg := pluginRegistration{
		Actions:      []actionDescriptor{},
		Dependencies: []PluginCoordinates{},
		FlowPlans:    []json.RawMessage{},
	}

	err := postRegistration(context.Background(), server.URL, reg)
	if err != nil {
		t.Fatalf("postRegistration should succeed after retries: %v", err)
	}

	if attempts < 3 {
		t.Errorf("expected at least 3 attempts, got %d", attempts)
	}
}

func TestPostRegistration_AllRetriesFail(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"error":"permanently broken"}`))
	}))
	defer server.Close()

	reg := pluginRegistration{
		Actions:      []actionDescriptor{},
		Dependencies: []PluginCoordinates{},
		FlowPlans:    []json.RawMessage{},
	}

	err := postRegistration(context.Background(), server.URL, reg)
	if err == nil {
		t.Fatal("expected error when all retries fail")
	}
}

func TestBuildRegistration_WithLookupTables(t *testing.T) {
	p := &plugin{
		coordinates: PluginCoordinates{GroupID: "g", ArtifactID: "a", Version: "v"},
		suppliers: map[string]LookupTableSupplier{
			"test_colors": &testLookupTableSupplier{
				table: LookupTableDefinition{
					Name:                 "test_colors",
					Columns:              []string{"color", "hex"},
					KeyColumns:           []string{"color"},
					ServiceBacked:        true,
					BackingServiceActive: true,
				},
			},
		},
	}

	reg := buildRegistration(p)

	if len(reg.LookupTables) != 1 {
		t.Fatalf("LookupTables count = %d, want 1", len(reg.LookupTables))
	}
	if reg.LookupTables[0].Name != "test_colors" {
		t.Errorf("LookupTables[0].Name = %q, want %q", reg.LookupTables[0].Name, "test_colors")
	}
	if !reg.LookupTables[0].ServiceBacked {
		t.Error("LookupTables[0].ServiceBacked should be true")
	}

	// Verify JSON serialization
	data, err := json.Marshal(reg)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}
	var parsed map[string]any
	json.Unmarshal(data, &parsed)
	tables := parsed["lookupTables"].([]any)
	if len(tables) != 1 {
		t.Fatalf("JSON lookupTables count = %d, want 1", len(tables))
	}
	table := tables[0].(map[string]any)
	if table["name"] != "test_colors" {
		t.Errorf("JSON table name = %v, want %q", table["name"], "test_colors")
	}
}

func TestPostRegistration_ContextCancelled(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	ctx, cancel := context.WithCancel(context.Background())
	cancel() // Cancel immediately

	reg := pluginRegistration{
		Actions:      []actionDescriptor{},
		Dependencies: []PluginCoordinates{},
		FlowPlans:    []json.RawMessage{},
	}

	err := postRegistration(ctx, server.URL, reg)
	if err == nil {
		t.Fatal("expected error when context is cancelled")
	}
}
