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
// ABOUTME: Shared git setup for plugin scaffolding used by the CLI and MCP tools
// ABOUTME: Initializes plugin repositories and stages generated files

package gitsetup

import (
	"errors"
	"fmt"
	"os/exec"
	"path/filepath"
	"strings"
)

// ErrGitNotFound reports that the git executable is not on PATH. Callers that
// treat Git setup as optional can detect it with errors.Is.
var ErrGitNotFound = errors.New("git executable not found")

// EnsureRepoRoot makes pluginDir the root of its own git repository, running
// `git init` when pluginDir is not already a repository root — even when it is
// nested inside a parent repository. It returns true when it ran `git init`.
func EnsureRepoRoot(pluginDir string) (bool, error) {
	gitPath, err := locateGit()
	if err != nil {
		return false, err
	}
	if isRepoRoot(gitPath, pluginDir) {
		return false, nil
	}
	return initRepoRoot(gitPath, pluginDir)
}

// EnsureRepo makes pluginDir part of a git repository while leaving an enclosing
// repository intact. It runs `git init` only when pluginDir is not tracked by
// any repository. It returns true when it ran `git init`.
func EnsureRepo(pluginDir string) (bool, error) {
	gitPath, err := locateGit()
	if err != nil {
		return false, err
	}
	if _, ok := repoRoot(gitPath, pluginDir); ok {
		return false, nil
	}
	return initRepoRoot(gitPath, pluginDir)
}

// Stage runs `git add .` in pluginDir.
func Stage(pluginDir string) error {
	gitPath, err := locateGit()
	if err != nil {
		return err
	}
	add := exec.Command(gitPath, "add", ".")
	add.Dir = pluginDir
	if output, err := add.CombinedOutput(); err != nil {
		return fmt.Errorf("git add failed: %w: %s", err, strings.TrimSpace(string(output)))
	}
	return nil
}

func locateGit() (string, error) {
	gitPath, err := exec.LookPath("git")
	if err != nil {
		return "", fmt.Errorf("%w: %v", ErrGitNotFound, err)
	}
	return gitPath, nil
}

func initRepoRoot(gitPath, pluginDir string) (bool, error) {
	initCmd := exec.Command(gitPath, "init", "--quiet")
	initCmd.Dir = pluginDir
	if output, err := initCmd.CombinedOutput(); err != nil {
		return false, fmt.Errorf("git init failed: %w: %s", err, strings.TrimSpace(string(output)))
	}
	if !isRepoRoot(gitPath, pluginDir) {
		return false, fmt.Errorf("git init did not create a repository rooted at %s", pluginDir)
	}
	return true, nil
}

func isRepoRoot(gitPath, pluginDir string) bool {
	root, ok := repoRoot(gitPath, pluginDir)
	if !ok {
		return false
	}
	resolved, err := canonicalPath(pluginDir)
	return err == nil && root == resolved
}

func repoRoot(gitPath, pluginDir string) (string, bool) {
	rootCmd := exec.Command(gitPath, "rev-parse", "--show-toplevel")
	rootCmd.Dir = pluginDir
	root, err := rootCmd.Output()
	if err != nil {
		return "", false
	}
	resolved, err := canonicalPath(strings.TrimSpace(string(root)))
	if err != nil {
		return "", false
	}
	return resolved, true
}

func canonicalPath(path string) (string, error) {
	abs, err := filepath.Abs(path)
	if err != nil {
		return "", err
	}
	resolved, err := filepath.EvalSymlinks(abs)
	if err != nil {
		return "", err
	}
	return filepath.Clean(resolved), nil
}
