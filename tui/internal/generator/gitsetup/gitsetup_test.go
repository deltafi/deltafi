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
// ABOUTME: Tests for shared plugin git setup: own-root generation and enclosing-repo updates
// ABOUTME: Exercises real git so parent repositories are left staged/untouched as expected

package gitsetup

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

func TestEnsureRepoRoot_CreatesOwnRootInsideParent(t *testing.T) {
	gitPath, err := exec.LookPath("git")
	if err != nil {
		t.Skip("git is not installed")
	}

	parent := t.TempDir()
	runGit(t, gitPath, parent, "init", "--quiet")
	pluginDir := filepath.Join(parent, "generated-plugin")
	if err := os.Mkdir(pluginDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(pluginDir, "README.md"), []byte("plugin"), 0o644); err != nil {
		t.Fatal(err)
	}

	initialized, err := EnsureRepoRoot(pluginDir)
	if err != nil {
		t.Fatalf("EnsureRepoRoot failed: %v", err)
	}
	if !initialized {
		t.Fatal("expected EnsureRepoRoot to initialize a new repository")
	}
	if !isRepoRoot(gitPath, pluginDir) {
		t.Fatal("generated plugin was not initialized as its own Git repository")
	}

	if err := Stage(pluginDir); err != nil {
		t.Fatalf("Stage failed: %v", err)
	}
	parentStaged := runGit(t, gitPath, parent, "diff", "--cached", "--name-only")
	if strings.TrimSpace(parentStaged) != "" {
		t.Fatalf("generated files were staged in parent repository: %s", parentStaged)
	}
	pluginStaged := runGit(t, gitPath, pluginDir, "diff", "--cached", "--name-only")
	if strings.TrimSpace(pluginStaged) != "README.md" {
		t.Fatalf("expected README.md staged in plugin repository, got %q", pluginStaged)
	}
}

func TestEnsureRepo_PreservesParentRepo(t *testing.T) {
	gitPath, err := exec.LookPath("git")
	if err != nil {
		t.Skip("git is not installed")
	}

	parent := t.TempDir()
	runGit(t, gitPath, parent, "init", "--quiet")
	pluginDir := filepath.Join(parent, "existing-plugin")
	if err := os.Mkdir(pluginDir, 0o755); err != nil {
		t.Fatal(err)
	}

	initialized, err := EnsureRepo(pluginDir)
	if err != nil {
		t.Fatalf("EnsureRepo failed: %v", err)
	}
	if initialized {
		t.Fatal("EnsureRepo should not initialize a repository inside an existing parent")
	}
	if _, err := os.Stat(filepath.Join(pluginDir, ".git")); !os.IsNotExist(err) {
		t.Fatalf("update created a nested Git repository: %v", err)
	}
	if root, ok := repoRoot(gitPath, pluginDir); !ok || root == "" {
		t.Fatal("plugin should remain part of the parent repository")
	}
}

func runGit(t *testing.T, gitPath, dir string, args ...string) string {
	t.Helper()
	cmd := exec.Command(gitPath, args...)
	cmd.Dir = dir
	output, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("git %s failed: %v\n%s", strings.Join(args, " "), err, output)
	}
	return string(output)
}
