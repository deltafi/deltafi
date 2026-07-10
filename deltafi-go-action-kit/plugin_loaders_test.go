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
// ABOUTME: Tests for plugin.go directory loaders and plugin identity resolution.
// ABOUTME: Covers flow/variable/integration-test loading and link-time vs plugin.yaml identity.

package actionkit

import (
	"os"
	"path/filepath"
	"testing"
)

func writeFile(t *testing.T, dir, name, content string) {
	t.Helper()
	if err := os.WriteFile(filepath.Join(dir, name), []byte(content), 0644); err != nil {
		t.Fatalf("write %s: %v", name, err)
	}
}

func TestLoadFlowsFromDirectory(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "flow1.json", `{"name":"flow1","type":"TRANSFORM"}`)
	writeFile(t, dir, "flow2.json", `{"name":"flow2","type":"EGRESS"}`)
	writeFile(t, dir, "variables.json", `[{"name":"v","dataType":"STRING"}]`)

	flows, err := loadFlowsFromDirectory(dir)
	if err != nil {
		t.Fatalf("loadFlowsFromDirectory: %v", err)
	}
	if len(flows) != 2 {
		t.Errorf("flow count = %d, want 2 (variables.json must be skipped)", len(flows))
	}
}

func TestLoadFlowsFromDirectory_Missing(t *testing.T) {
	flows, err := loadFlowsFromDirectory(filepath.Join(t.TempDir(), "nope"))
	if err != nil {
		t.Fatalf("missing dir should not error: %v", err)
	}
	if flows != nil {
		t.Errorf("flows = %v, want nil for missing dir", flows)
	}
}

func TestLoadVariables_YAMLPreferred(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "variables.yaml", "- name: var1\n  dataType: STRING\n- name: var2\n  dataType: BOOLEAN\n")
	writeFile(t, dir, "variables.json", `[{"name":"ignored","dataType":"STRING"}]`)

	vars, err := loadVariables(dir)
	if err != nil {
		t.Fatalf("loadVariables: %v", err)
	}
	if len(vars) != 2 || vars[0].Name != "var1" {
		t.Errorf("vars = %+v, want the two from variables.yaml", vars)
	}
}

func TestLoadVariables_JSONFallback(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "variables.json", `[{"name":"only","dataType":"STRING"}]`)

	vars, err := loadVariables(dir)
	if err != nil {
		t.Fatalf("loadVariables: %v", err)
	}
	if len(vars) != 1 || vars[0].Name != "only" {
		t.Errorf("vars = %+v, want the single JSON variable", vars)
	}
}

func TestLoadVariables_None(t *testing.T) {
	vars, err := loadVariables(t.TempDir())
	if err != nil {
		t.Fatalf("loadVariables: %v", err)
	}
	if vars != nil {
		t.Errorf("vars = %v, want nil when no variables file present", vars)
	}
}

func TestLoadIntegrationTests(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "it1.json", `{"name":"test1"}`)

	tests, err := loadIntegrationTests(dir)
	if err != nil {
		t.Fatalf("loadIntegrationTests: %v", err)
	}
	if len(tests) != 1 {
		t.Errorf("test count = %d, want 1", len(tests))
	}
}

func TestLoadIntegrationTests_InvalidJSON(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, dir, "bad.json", `{not valid`)

	if _, err := loadIntegrationTests(dir); err == nil {
		t.Error("expected error for invalid integration test JSON")
	}
}

func TestLoadIntegrationTests_Missing(t *testing.T) {
	tests, err := loadIntegrationTests(filepath.Join(t.TempDir(), "nope"))
	if err != nil {
		t.Fatalf("missing dir should not error: %v", err)
	}
	if len(tests) != 0 {
		t.Errorf("tests = %v, want empty for missing dir", tests)
	}
}

func TestResolvePluginIdentity_LinkTime(t *testing.T) {
	prevG, prevA, prevD := PluginGroupID, PluginArtifactID, PluginDomain
	t.Cleanup(func() { PluginGroupID, PluginArtifactID, PluginDomain = prevG, prevA, prevD })
	PluginGroupID, PluginArtifactID, PluginDomain = "org.x", "my-plugin", "org.x.my"

	meta, err := resolvePluginIdentity()
	if err != nil {
		t.Fatalf("resolvePluginIdentity: %v", err)
	}
	if meta.GroupID != "org.x" || meta.ArtifactID != "my-plugin" || meta.Domain != "org.x.my" {
		t.Errorf("meta = %+v, want link-time values", meta)
	}
}

func TestResolvePluginIdentity_YAMLFallback(t *testing.T) {
	prevG, prevA := PluginGroupID, PluginArtifactID
	t.Cleanup(func() { PluginGroupID, PluginArtifactID = prevG, prevA })
	PluginGroupID, PluginArtifactID = "", ""

	dir := t.TempDir()
	writeFile(t, dir, "plugin.yaml", "groupId: org.file\nartifactId: filed\n")
	t.Chdir(dir)

	meta, err := resolvePluginIdentity()
	if err != nil {
		t.Fatalf("resolvePluginIdentity: %v", err)
	}
	if meta.GroupID != "org.file" || meta.ArtifactID != "filed" {
		t.Errorf("meta = %+v, want values parsed from plugin.yaml", meta)
	}
}

func TestResolvePluginIdentity_NoYAML(t *testing.T) {
	prevG, prevA := PluginGroupID, PluginArtifactID
	t.Cleanup(func() { PluginGroupID, PluginArtifactID = prevG, prevA })
	PluginGroupID, PluginArtifactID = "", ""

	t.Chdir(t.TempDir())

	if _, err := resolvePluginIdentity(); err == nil {
		t.Error("expected error when identity is unset and plugin.yaml is absent")
	}
}
