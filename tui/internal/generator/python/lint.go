// ABOUTME: Static analysis checks for Python DeltaFi plugins.
// ABOUTME: Verifies scaffolding, pyproject.toml, plugin.py, action wiring, and flow JSON references.

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
	"encoding/json"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"github.com/deltafi/tui/internal/generator"
)

// LintPlugin runs static checks against an existing Python plugin and returns
// a list of issues sorted by severity then file. A non-nil error indicates
// the lint run itself could not be performed (e.g. directory missing).
func (g *PythonGenerator) LintPlugin(pluginName string, options *generator.LintOptions) ([]generator.LintIssue, error) {
	dirOverride := ""
	if options != nil {
		dirOverride = options.PluginDir
	}
	pluginDir, err := resolvePluginDir(pluginName, dirOverride)
	if err != nil {
		return nil, err
	}
	if _, err := os.Stat(pluginDir); os.IsNotExist(err) {
		return nil, fmt.Errorf("plugin directory does not exist: %s", pluginDir)
	}

	var issues []generator.LintIssue

	packageName, pkgIssues := resolvePythonPackageName(pluginDir, pluginName)
	issues = append(issues, pkgIssues...)

	issues = append(issues, lintPythonRequiredFiles(pluginDir, packageName)...)
	issues = append(issues, lintPyProject(pluginDir)...)

	registeredActions, actionIssues := lintPythonPlugin(pluginDir, packageName)
	issues = append(issues, actionIssues...)

	issues = append(issues, lintPythonFlows(pluginDir, packageName, registeredActions)...)

	sort.SliceStable(issues, func(i, j int) bool {
		if issues[i].Severity != issues[j].Severity {
			return issues[i].Severity > issues[j].Severity
		}
		return issues[i].File < issues[j].File
	})
	return issues, nil
}

// resolvePythonPackageName determines the snake_case package directory under
// src/. If a pluginName was provided we derive it directly; otherwise we scan
// src/*/plugin.py and pick the single match.
func resolvePythonPackageName(pluginDir, pluginName string) (string, []generator.LintIssue) {
	if pluginName != "" {
		return PluginNameToPackageName(pluginName), nil
	}
	srcDir := filepath.Join(pluginDir, "src")
	entries, err := os.ReadDir(srcDir)
	if err != nil {
		return "", []generator.LintIssue{{
			Severity: generator.LintError,
			File:     "src",
			Message:  fmt.Sprintf("cannot read src directory: %v", err),
		}}
	}
	var candidates []string
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		if _, err := os.Stat(filepath.Join(srcDir, e.Name(), "plugin.py")); err == nil {
			candidates = append(candidates, e.Name())
		}
	}
	switch len(candidates) {
	case 0:
		return "", []generator.LintIssue{{
			Severity: generator.LintError,
			File:     "src",
			Message:  "no package directory containing plugin.py found under src/",
		}}
	case 1:
		return candidates[0], nil
	default:
		return candidates[0], []generator.LintIssue{{
			Severity: generator.LintWarning,
			File:     "src",
			Message:  fmt.Sprintf("multiple package directories under src/ contain plugin.py (%s); linting %q", strings.Join(candidates, ", "), candidates[0]),
		}}
	}
}

// pythonRequiredFiles is the scaffolding every Python plugin must have.
var pythonRequiredFiles = []struct {
	path     string
	optional bool
}{
	{path: "pyproject.toml"},
	{path: "Dockerfile"},
	{path: "Makefile"},
	{path: ".gitignore"},
	{path: "README.md", optional: true},
	{path: "HEADER", optional: true},
	{path: "LICENSE", optional: true},
}

func lintPythonRequiredFiles(pluginDir, packageName string) []generator.LintIssue {
	var issues []generator.LintIssue
	for _, f := range pythonRequiredFiles {
		if _, err := os.Stat(filepath.Join(pluginDir, f.path)); os.IsNotExist(err) {
			sev := generator.LintError
			if f.optional {
				sev = generator.LintWarning
			}
			issues = append(issues, generator.LintIssue{
				Severity: sev,
				File:     f.path,
				Message:  "required file missing",
			})
		}
	}
	if packageName == "" {
		return issues
	}
	packageRequired := []string{
		filepath.Join("src", packageName, "__init__.py"),
		filepath.Join("src", packageName, "plugin.py"),
		filepath.Join("src", packageName, "actions", "__init__.py"),
	}
	for _, p := range packageRequired {
		if _, err := os.Stat(filepath.Join(pluginDir, p)); os.IsNotExist(err) {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     p,
				Message:  "required file missing",
			})
		}
	}
	return issues
}

var (
	pyProjectNameRe    = regexp.MustCompile(`(?m)^\s*name\s*=\s*['"]([^'"]+)['"]`)
	pyProjectDeltafiRe = regexp.MustCompile(`['"]deltafi\s*(==|>=|~=|>|<)`)
)

// lintPyProject parses pyproject.toml to verify the project has a name and
// declares a dependency on the deltafi action kit.
func lintPyProject(pluginDir string) []generator.LintIssue {
	var issues []generator.LintIssue
	path := filepath.Join(pluginDir, "pyproject.toml")
	content, err := os.ReadFile(path)
	if err != nil {
		// Required-files check already reports missing pyproject.toml.
		return nil
	}
	src := string(content)

	if !pyProjectNameRe.MatchString(src) {
		issues = append(issues, generator.LintIssue{
			Severity: generator.LintError,
			File:     "pyproject.toml",
			Message:  "missing or unparseable [project] name (e.g. name = \"my-plugin\")",
		})
	}

	if !pyProjectDeltafiRe.MatchString(src) {
		issues = append(issues, generator.LintIssue{
			Severity: generator.LintError,
			File:     "pyproject.toml",
			Message:  "missing dependency on the deltafi action kit (expected e.g. 'deltafi==<version>')",
		})
	}

	return issues
}

var (
	pythonClassRe           = regexp.MustCompile(`(?m)^class\s+(\w+)\s*\(([^)]*)\)\s*:`)
	pythonPluginImportRe    = regexp.MustCompile(`from\s+deltafi\.plugin\s+import\s+[^\n]*Plugin`)
	pythonPluginConstructRe = regexp.MustCompile(`Plugin\s*\(`)
	pythonActionPackageKwRe = regexp.MustCompile(`action_package\s*=\s*['"]([^'"]+)['"]`)
)

// pythonActionBaseClasses is the set of deltafi action-kit base classes that
// mark a Python class as a registerable action.
var pythonActionBaseClasses = map[string]bool{
	"TransformAction":    true,
	"EgressAction":       true,
	"TimedIngressAction": true,
}

// lintPythonPlugin checks the plugin.py entrypoint and walks the actions
// package for classes extending a known action base class. Returns the set
// of registered action class names (used by the flow JSON check).
func lintPythonPlugin(pluginDir, packageName string) (map[string]bool, []generator.LintIssue) {
	registered := map[string]bool{}
	var issues []generator.LintIssue

	if packageName == "" {
		return registered, issues
	}

	pluginPath := filepath.Join(pluginDir, "src", packageName, "plugin.py")
	actionPackage := "actions"
	if content, err := os.ReadFile(pluginPath); err == nil {
		src := string(content)
		if !pythonPluginImportRe.MatchString(src) {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     filepath.Join("src", packageName, "plugin.py"),
				Message:  "missing 'from deltafi.plugin import Plugin' import",
			})
		}
		if !pythonPluginConstructRe.MatchString(src) {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     filepath.Join("src", packageName, "plugin.py"),
				Message:  "plugin.py does not instantiate a Plugin(...)",
			})
		}
		if m := pythonActionPackageKwRe.FindStringSubmatch(src); len(m) >= 2 {
			actionPackage = m[1]
		}
	}

	actionsDir := filepath.Join(pluginDir, "src", packageName, actionPackage)
	if _, err := os.Stat(actionsDir); os.IsNotExist(err) {
		issues = append(issues, generator.LintIssue{
			Severity: generator.LintError,
			File:     filepath.Join("src", packageName, actionPackage),
			Message:  fmt.Sprintf("action package %q declared in plugin.py does not exist", actionPackage),
		})
		return registered, issues
	}

	err := filepath.WalkDir(actionsDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() || !strings.HasSuffix(d.Name(), ".py") || d.Name() == "__init__.py" {
			return nil
		}
		rel, _ := filepath.Rel(pluginDir, path)
		content, readErr := os.ReadFile(path)
		if readErr != nil {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     rel,
				Message:  fmt.Sprintf("failed to read file: %v", readErr),
			})
			return nil
		}
		for _, m := range pythonClassRe.FindAllStringSubmatch(string(content), -1) {
			className := m[1]
			bases := m[2]
			for base := range pythonActionBaseClasses {
				if containsBase(bases, base) {
					registered[className] = true
					break
				}
			}
		}
		return nil
	})
	if err != nil {
		issues = append(issues, generator.LintIssue{
			Severity: generator.LintError,
			File:     filepath.Join("src", packageName, actionPackage),
			Message:  fmt.Sprintf("failed to walk action package: %v", err),
		})
	}

	if len(registered) == 0 {
		issues = append(issues, generator.LintIssue{
			Severity: generator.LintWarning,
			File:     filepath.Join("src", packageName, actionPackage),
			Message:  "no action classes found (expected subclasses of TransformAction, EgressAction, or TimedIngressAction)",
		})
	}

	return registered, issues
}

// containsBase checks whether `bases` (the parenthesized superclass list of a
// class declaration) references the given base class name as a whole word.
func containsBase(bases, base string) bool {
	for _, part := range strings.Split(bases, ",") {
		name := strings.TrimSpace(part)
		// Strip any generic parameters or trailing whitespace.
		if idx := strings.IndexAny(name, "[ "); idx >= 0 {
			name = name[:idx]
		}
		if name == base {
			return true
		}
	}
	return false
}

// flowActionFields lists the keys in a flow JSON document whose values
// (objects or arrays of objects) describe action references via a "type" field.
var flowActionFields = []string{
	"transformActions",
	"egressAction",
	"timedIngressAction",
	"ingressAction",
	"loadActions",
	"formatActions",
	"validateActions",
	"enrichActions",
	"domainActions",
	"errorAction",
}

// lintPythonFlows scans src/<package>/flows/*.json for action references and
// verifies each one resolves to a class registered in this plugin.
func lintPythonFlows(pluginDir, packageName string, registered map[string]bool) []generator.LintIssue {
	if packageName == "" {
		return nil
	}
	var issues []generator.LintIssue
	dir := filepath.Join(pluginDir, "src", packageName, "flows")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil
	}
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".json") || e.Name() == "variables.json" {
			continue
		}
		relPath := filepath.Join("src", packageName, "flows", e.Name())
		content, err := os.ReadFile(filepath.Join(dir, e.Name()))
		if err != nil {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     relPath,
				Message:  fmt.Sprintf("failed to read file: %v", err),
			})
			continue
		}
		var doc map[string]any
		if err := json.Unmarshal(content, &doc); err != nil {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     relPath,
				Message:  fmt.Sprintf("invalid JSON: %v", err),
			})
			continue
		}
		for _, field := range flowActionFields {
			refs := extractActionTypeRefs(doc[field])
			for _, ref := range refs {
				class := ref
				if i := strings.LastIndex(ref, "."); i >= 0 {
					class = ref[i+1:]
				}
				if !registered[class] && !registered[ref] {
					issues = append(issues, generator.LintIssue{
						Severity: generator.LintWarning,
						File:     relPath,
						Message:  fmt.Sprintf("references action %q which is not registered in this plugin (may be provided by another plugin)", ref),
					})
				}
			}
		}
	}
	return issues
}

// extractActionTypeRefs walks an action-bearing field (an object or array of
// objects) and returns the values of every "type" key it finds.
func extractActionTypeRefs(node any) []string {
	if node == nil {
		return nil
	}
	var refs []string
	switch v := node.(type) {
	case map[string]any:
		if t, ok := v["type"].(string); ok && t != "" {
			refs = append(refs, t)
		}
	case []any:
		for _, item := range v {
			refs = append(refs, extractActionTypeRefs(item)...)
		}
	}
	return refs
}
