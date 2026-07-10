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
// ABOUTME: Tests for Plugin creation, action registration, and configuration.
// ABOUTME: Verifies plugin creation, env handling, RegisterAction type detection, and RegisterFlow/Variable.

package actionkit

import (
	"encoding/json"
	"os"
	"testing"
)

func TestParsePluginYAML(t *testing.T) {
	meta, err := parsePluginYAML([]byte("groupId: org.example\nartifactId: my-plugin\ndomain: org.example.my-plugin\ndescription: A plugin\n"))
	if err != nil {
		t.Fatalf("parsePluginYAML: %v", err)
	}
	if meta.GroupID != "org.example" || meta.ArtifactID != "my-plugin" {
		t.Errorf("meta = %+v", meta)
	}
	if meta.Domain != "org.example.my-plugin" || meta.Description != "A plugin" {
		t.Errorf("meta = %+v", meta)
	}
}

func TestParsePluginYAML_Errors(t *testing.T) {
	if _, err := parsePluginYAML([]byte("artifactId: a\n")); err == nil {
		t.Error("expected error when groupId is missing")
	}
	if _, err := parsePluginYAML([]byte("groupId: g\n")); err == nil {
		t.Error("expected error when artifactId is missing")
	}
	if _, err := parsePluginYAML([]byte("groupId: [unterminated\n")); err == nil {
		t.Error("expected error for invalid YAML")
	}
}

func TestNewPlugin_Defaults(t *testing.T) {
	// Clear env vars that would affect defaults
	for _, key := range []string{"CORE_URL", "VALKEY_URL", "MINIO_URL", "PROJECT_GROUP", "PROJECT_NAME", "PROJECT_VERSION"} {
		os.Unsetenv(key)
	}

	p, err := newPlugin(nil)
	if err != nil {
		t.Fatalf("newPlugin: %v", err)
	}

	if p.config.CoreURL != "http://deltafi-core-service" {
		t.Errorf("CoreURL = %q", p.config.CoreURL)
	}
	if p.config.ValkeyURL != "deltafi-valkey-master:6379" {
		t.Errorf("ValkeyURL = %q", p.config.ValkeyURL)
	}
	if p.config.MinioURL != "deltafi-minio:9000" {
		t.Errorf("MinioURL = %q", p.config.MinioURL)
	}
	if p.config.MinioAccessKey != "deltafi" {
		t.Errorf("MinioAccessKey = %q", p.config.MinioAccessKey)
	}
	if p.config.MinioSecretKey != "password" {
		t.Errorf("MinioSecretKey = %q", p.config.MinioSecretKey)
	}
	if p.config.MinioBucket != "storage" {
		t.Errorf("MinioBucket = %q", p.config.MinioBucket)
	}
	if p.flows == nil {
		t.Error("flows should be initialized, not nil")
	}
	if p.variables == nil {
		t.Error("variables should be initialized, not nil")
	}
}

func TestNewPlugin_EnvOverrides(t *testing.T) {
	os.Setenv("CORE_URL", "http://custom-core")
	os.Setenv("PROJECT_GROUP", "org.custom")
	os.Setenv("PROJECT_NAME", "custom-plugin")
	os.Setenv("PROJECT_VERSION", "3.0.0")
	defer func() {
		os.Unsetenv("CORE_URL")
		os.Unsetenv("PROJECT_GROUP")
		os.Unsetenv("PROJECT_NAME")
		os.Unsetenv("PROJECT_VERSION")
	}()

	p, err := newPlugin(nil)
	if err != nil {
		t.Fatalf("newPlugin: %v", err)
	}

	if p.config.CoreURL != "http://custom-core" {
		t.Errorf("CoreURL = %q", p.config.CoreURL)
	}
	if p.coordinates.GroupID != "org.custom" {
		t.Errorf("GroupID = %q", p.coordinates.GroupID)
	}
	if p.coordinates.ArtifactID != "custom-plugin" {
		t.Errorf("ArtifactID = %q", p.coordinates.ArtifactID)
	}
	if p.coordinates.Version != "3.0.0" {
		t.Errorf("Version = %q", p.coordinates.Version)
	}
}

func TestNewPlugin_ConfigOverrides(t *testing.T) {
	os.Unsetenv("CORE_URL")

	p, err := newPlugin(&pluginConfig{
		CoreURL:     "http://override-core",
		MinioBucket: "custom-bucket",
		Coordinates: &PluginCoordinates{
			GroupID:    "org.override",
			ArtifactID: "override-plugin",
			Version:    "5.0.0",
		},
	})
	if err != nil {
		t.Fatalf("newPlugin: %v", err)
	}

	if p.config.CoreURL != "http://override-core" {
		t.Errorf("CoreURL = %q", p.config.CoreURL)
	}
	if p.config.MinioBucket != "custom-bucket" {
		t.Errorf("MinioBucket = %q", p.config.MinioBucket)
	}
	if p.coordinates.GroupID != "org.override" {
		t.Errorf("GroupID = %q", p.coordinates.GroupID)
	}
	if p.coordinates.ArtifactID != "override-plugin" {
		t.Errorf("ArtifactID = %q", p.coordinates.ArtifactID)
	}
}

func TestPlugin_RegisterAction_Transform(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	p := &plugin{}
	reg := RegisterTransform("MyTransform", &testTransformAction{})
	err := p.RegisterAction(reg)
	if err != nil {
		t.Fatalf("RegisterAction: %v", err)
	}

	if len(p.actions) != 1 {
		t.Fatalf("actions count = %d, want 1", len(p.actions))
	}
	if p.actions[0].name != "MyTransform" {
		t.Errorf("name = %q, want %q", p.actions[0].name, "MyTransform")
	}
	if p.actions[0].actionType != ActionTypeTransform {
		t.Errorf("actionType = %q", p.actions[0].actionType)
	}
}

func TestPlugin_RegisterAction_WithDomain(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	p := &plugin{domain: "org.deltafi.go-hello-world"}
	reg := RegisterTransform("MyTransform", &testTransformAction{})
	err := p.RegisterAction(reg)
	if err != nil {
		t.Fatalf("RegisterAction: %v", err)
	}

	want := "org.deltafi.go-hello-world.MyTransform"
	if p.actions[0].name != want {
		t.Errorf("name = %q, want %q", p.actions[0].name, want)
	}
}

func TestPlugin_RegisterAction_NoDomain(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	p := &plugin{}
	reg := RegisterTransform("MyTransform", &testTransformAction{})
	registerAction(t, p, reg)

	if p.actions[0].name != "MyTransform" {
		t.Errorf("name = %q, want %q", p.actions[0].name, "MyTransform")
	}
}

func TestPlugin_RegisterAction_Egress(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	p := &plugin{}
	reg := RegisterEgress("MyEgress", &testEgressAction{})
	registerAction(t, p, reg)

	if p.actions[0].actionType != ActionTypeEgress {
		t.Errorf("actionType = %q", p.actions[0].actionType)
	}
}

func TestPlugin_RegisterAction_TimedIngress(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	p := &plugin{}
	reg := RegisterIngress("MyIngress", &testTimedIngressAction{})
	registerAction(t, p, reg)

	if p.actions[0].actionType != ActionTypeTimedIngress {
		t.Errorf("actionType = %q", p.actions[0].actionType)
	}
}

func TestPlugin_RegisterAction_Multiple(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	p := &plugin{}
	for _, reg := range []*Registration{
		RegisterTransform("A1", &testTransformAction{}),
		RegisterEgress("A2", &testEgressAction{}),
		RegisterIngress("A3", &testTimedIngressAction{}),
	} {
		registerAction(t, p, reg)
	}

	if len(p.actions) != 3 {
		t.Fatalf("actions count = %d, want 3", len(p.actions))
	}
}

func TestPlugin_RegisterFlow(t *testing.T) {
	p := &plugin{}

	flow := FlowPlan{
		Name: "test-flow",
		Type: "TRANSFORM",
		Raw:  json.RawMessage(`{"name":"test-flow","type":"TRANSFORM"}`),
	}
	p.RegisterFlow(flow)

	if len(p.flows) != 1 {
		t.Fatalf("flows count = %d, want 1", len(p.flows))
	}
	if p.flows[0].Name != "test-flow" {
		t.Errorf("flow name = %q", p.flows[0].Name)
	}
}

func TestPlugin_RegisterVariable(t *testing.T) {
	p := &plugin{}

	v := Variable{Name: "myVar", DataType: "STRING", Required: true}
	p.RegisterVariable(v)

	if len(p.variables) != 1 {
		t.Fatalf("variables count = %d, want 1", len(p.variables))
	}
	if p.variables[0].Name != "myVar" {
		t.Errorf("variable name = %q", p.variables[0].Name)
	}
}

func TestParseYAMLVariables_List(t *testing.T) {
	data := []byte(`---
- name: alpha
  dataType: STRING
  required: true
- name: beta
  dataType: NUMBER
`)
	vars, err := parseYAMLVariables(data, "test.yaml")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(vars) != 2 {
		t.Fatalf("got %d variables, want 2", len(vars))
	}
	if vars[0].Name != "alpha" || vars[1].Name != "beta" {
		t.Errorf("names = [%q, %q], want [alpha, beta]", vars[0].Name, vars[1].Name)
	}
}

func TestParseYAMLVariables_SingleMap(t *testing.T) {
	data := []byte(`---
name: solo
dataType: BOOLEAN
required: false
`)
	vars, err := parseYAMLVariables(data, "test.yaml")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(vars) != 1 {
		t.Fatalf("got %d variables, want 1", len(vars))
	}
	if vars[0].Name != "solo" {
		t.Errorf("name = %q, want solo", vars[0].Name)
	}
}

func TestParseYAMLVariables_MultiDocument(t *testing.T) {
	data := []byte(`---
name: first
dataType: STRING
required: true
---
name: second
dataType: NUMBER
required: false
defaultValue: "42"
---
- name: third
  dataType: BOOLEAN
`)
	vars, err := parseYAMLVariables(data, "test.yaml")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(vars) != 3 {
		t.Fatalf("got %d variables, want 3", len(vars))
	}
	if vars[0].Name != "first" || vars[1].Name != "second" || vars[2].Name != "third" {
		t.Errorf("names = [%q, %q, %q]", vars[0].Name, vars[1].Name, vars[2].Name)
	}
}

func TestParseYAMLVariables_EmptyFile(t *testing.T) {
	vars, err := parseYAMLVariables([]byte(""), "test.yaml")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(vars) != 0 {
		t.Fatalf("got %d variables, want 0", len(vars))
	}
}

func TestPlugin_SetWorkerCount(t *testing.T) {
	old := registered
	registered = nil
	defer func() { registered = old }()

	p := &plugin{}
	registerAction(t, p, RegisterTransform("ActionA", &testTransformAction{}))
	registerAction(t, p, RegisterEgress("ActionB", &testEgressAction{}))

	p.SetWorkerCount("ActionA", 3)

	if count := p.workerCount("ActionA"); count != 3 {
		t.Errorf("workerCount(ActionA) = %d, want 3", count)
	}
	// ActionB not explicitly set — should default to 1
	if count := p.workerCount("ActionB"); count != 1 {
		t.Errorf("workerCount(ActionB) = %d, want 1", count)
	}
}

func TestPlugin_SetWorkerCount_Override(t *testing.T) {
	p := &plugin{}
	p.SetWorkerCount("X", 5)
	p.SetWorkerCount("X", 10)

	if count := p.workerCount("X"); count != 10 {
		t.Errorf("workerCount(X) = %d, want 10 (last set wins)", count)
	}
}

func TestPlugin_WorkerCount_EnvVar(t *testing.T) {
	os.Setenv("ACTION_THREADS", "4")
	defer os.Unsetenv("ACTION_THREADS")

	p := &plugin{}

	// No explicit SetWorkerCount — should use env var
	if count := p.workerCount("AnyAction"); count != 4 {
		t.Errorf("workerCount(AnyAction) = %d, want 4 from ACTION_THREADS", count)
	}
}

func TestPlugin_WorkerCount_PerActionEnvVar(t *testing.T) {
	os.Setenv("ACTIONS_ACTIONTHREADS_MYACTION", "6")
	defer os.Unsetenv("ACTIONS_ACTIONTHREADS_MYACTION")

	p := &plugin{}

	if count := p.workerCount("MyAction"); count != 6 {
		t.Errorf("workerCount(MyAction) = %d, want 6 from per-action env var", count)
	}
	// Other actions not affected
	if count := p.workerCount("OtherAction"); count != 1 {
		t.Errorf("workerCount(OtherAction) = %d, want 1", count)
	}
}

func TestPlugin_WorkerCount_QualifiedNameEnvVar(t *testing.T) {
	// Dots and hyphens in the qualified name become underscores in the env var
	os.Setenv("ACTIONS_ACTIONTHREADS_ORG_DELTAFI_GO_HELLO_WORLD_MYTRANSFORM", "5")
	defer os.Unsetenv("ACTIONS_ACTIONTHREADS_ORG_DELTAFI_GO_HELLO_WORLD_MYTRANSFORM")

	p := &plugin{}

	if count := p.workerCount("org.deltafi.go-hello-world.MyTransform"); count != 5 {
		t.Errorf("workerCount = %d, want 5 from qualified env var", count)
	}
}

func TestPlugin_WorkerCount_PerActionEnvOverridesGlobalEnv(t *testing.T) {
	os.Setenv("ACTION_THREADS", "4")
	os.Setenv("ACTIONS_ACTIONTHREADS_MYACTION", "8")
	defer os.Unsetenv("ACTION_THREADS")
	defer os.Unsetenv("ACTIONS_ACTIONTHREADS_MYACTION")

	p := &plugin{}

	// Per-action env takes priority over global env
	if count := p.workerCount("MyAction"); count != 8 {
		t.Errorf("workerCount(MyAction) = %d, want 8 from per-action env var", count)
	}
	// Other actions use global env
	if count := p.workerCount("OtherAction"); count != 4 {
		t.Errorf("workerCount(OtherAction) = %d, want 4 from ACTION_THREADS", count)
	}
}

func TestPlugin_WorkerCount_SetOverridesAllEnv(t *testing.T) {
	os.Setenv("ACTION_THREADS", "4")
	os.Setenv("ACTIONS_ACTIONTHREADS_SPECIFICACTION", "6")
	defer os.Unsetenv("ACTION_THREADS")
	defer os.Unsetenv("ACTIONS_ACTIONTHREADS_SPECIFICACTION")

	p := &plugin{}
	p.SetWorkerCount("SpecificAction", 7)

	// Explicit count takes priority over both env vars
	if count := p.workerCount("SpecificAction"); count != 7 {
		t.Errorf("workerCount(SpecificAction) = %d, want 7 (explicit overrides env)", count)
	}
	// Other actions use global env
	if count := p.workerCount("OtherAction"); count != 4 {
		t.Errorf("workerCount(OtherAction) = %d, want 4 from ACTION_THREADS", count)
	}
}

func TestPlugin_WorkerCount_InvalidEnv(t *testing.T) {
	os.Setenv("ACTION_THREADS", "not-a-number")
	defer os.Unsetenv("ACTION_THREADS")

	p := &plugin{}

	if count := p.workerCount("AnyAction"); count != 1 {
		t.Errorf("workerCount = %d, want 1 (invalid env should fallback to default)", count)
	}
}

func TestPlugin_WorkerCount_ZeroEnv(t *testing.T) {
	os.Setenv("ACTION_THREADS", "0")
	defer os.Unsetenv("ACTION_THREADS")

	p := &plugin{}

	if count := p.workerCount("AnyAction"); count != 1 {
		t.Errorf("workerCount = %d, want 1 (zero should fallback to default)", count)
	}
}

func TestPlugin_WatchArtifact(t *testing.T) {
	p := &plugin{}

	handler := func(fetched *FetchedArtifact, err error) {}
	p.WatchArtifact("config.yaml", &ArtifactDefault{
		Data:        []byte("config: []"),
		ContentType: "application/yaml",
	}, handler)

	if len(p.artifactWatches) != 1 {
		t.Fatalf("artifactWatches count = %d, want 1", len(p.artifactWatches))
	}
	if p.artifactWatches[0].name != "config.yaml" {
		t.Errorf("name = %q", p.artifactWatches[0].name)
	}
	if p.artifactWatches[0].shared {
		t.Error("should not be shared")
	}
	if p.artifactWatches[0].defaultArtifact == nil {
		t.Error("defaultArtifact should not be nil")
	}
}

func TestPlugin_WatchSharedArtifact(t *testing.T) {
	p := &plugin{}

	handler := func(fetched *FetchedArtifact, err error) {}
	p.WatchSharedArtifact("shared-config", nil, handler)

	if len(p.artifactWatches) != 1 {
		t.Fatalf("artifactWatches count = %d, want 1", len(p.artifactWatches))
	}
	if !p.artifactWatches[0].shared {
		t.Error("should be shared")
	}
	if p.artifactWatches[0].defaultArtifact != nil {
		t.Error("defaultArtifact should be nil")
	}
}

func TestPlugin_RegisterMultipleFlowsAndVariables(t *testing.T) {
	p := &plugin{}

	p.RegisterFlow(FlowPlan{Name: "f1", Raw: json.RawMessage(`{}`)})
	p.RegisterFlow(FlowPlan{Name: "f2", Raw: json.RawMessage(`{}`)})
	p.RegisterVariable(Variable{Name: "v1"})
	p.RegisterVariable(Variable{Name: "v2"})
	p.RegisterVariable(Variable{Name: "v3"})

	if len(p.flows) != 2 {
		t.Errorf("flows count = %d, want 2", len(p.flows))
	}
	if len(p.variables) != 3 {
		t.Errorf("variables count = %d, want 3", len(p.variables))
	}
}
