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
package python

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/deltafi/tui/internal/generator"
)

func TestGenerateActionWritesToConfiguredOutput(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "pyproject.toml"), []byte("[project]\nname = 'plugin'\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	packageDir := filepath.Join(dir, "src", PluginNameToPackageName(filepath.Base(dir)))
	if err := os.MkdirAll(packageDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(packageDir, "plugin.py"), []byte("# plugin\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	var output bytes.Buffer
	err := NewPythonGenerator().GenerateAction("plugin", "Normalize", "TransformAction", &generator.GenerateActionOptions{PluginDir: dir, Output: &output})
	if err != nil {
		t.Fatalf("GenerateAction failed: %v", err)
	}
	if !strings.Contains(output.String(), "Wrote") {
		t.Fatalf("configured output did not receive progress: %q", output.String())
	}
}
