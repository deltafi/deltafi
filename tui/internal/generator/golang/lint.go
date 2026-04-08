// ABOUTME: Static analysis checks for Go DeltaFi plugins.
// ABOUTME: Verifies scaffolding, registration, and flow wiring without compiling code.

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
package golang

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"

	"github.com/deltafi/tui/internal/generator"
)

// LintPlugin runs static checks against an existing Go plugin and returns
// a list of issues sorted by severity then file. A non-nil error indicates
// the lint run itself could not be performed (e.g. directory missing).
func (g *GoGenerator) LintPlugin(pluginName string, options *generator.LintOptions) ([]generator.LintIssue, error) {
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
	issues = append(issues, lintRequiredFiles(pluginDir)...)

	modulePath, modIssues := lintGoMod(pluginDir)
	issues = append(issues, modIssues...)

	issues = append(issues, lintMainGoBlankImports(pluginDir, modulePath)...)

	registeredActions, actionIssues := lintActions(pluginDir)
	issues = append(issues, actionIssues...)

	issues = append(issues, lintSuppliers(pluginDir)...)

	issues = append(issues, lintFlows(pluginDir, registeredActions)...)

	sort.SliceStable(issues, func(i, j int) bool {
		if issues[i].Severity != issues[j].Severity {
			return issues[i].Severity > issues[j].Severity
		}
		return issues[i].File < issues[j].File
	})
	return issues, nil
}

// requiredFiles is the list of scaffolding files every Go plugin must have.
// Files marked optional emit a warning when missing instead of an error.
var requiredFiles = []struct {
	path     string
	optional bool
}{
	{path: "main.go"},
	{path: "go.mod"},
	{path: "Makefile"},
	{path: "Dockerfile"},
	{path: ".gitignore"},
	{path: ".dockerignore"},
	{path: "actions/doc.go"},
	{path: "suppliers/doc.go"},
	{path: "README.md", optional: true},
	{path: "HEADER", optional: true},
	{path: "LICENSE", optional: true},
}

func lintRequiredFiles(pluginDir string) []generator.LintIssue {
	var issues []generator.LintIssue
	for _, f := range requiredFiles {
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
	return issues
}

var (
	moduleRe     = regexp.MustCompile(`(?m)^module\s+(\S+)`)
	replaceRe    = regexp.MustCompile(`(?m)^replace\s+(\S+)\s+\S+\s+=>\s+(\S+)`)
	actionKitMod = "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

// lintGoMod parses go.mod and returns the module path plus any issues.
func lintGoMod(pluginDir string) (string, []generator.LintIssue) {
	var issues []generator.LintIssue
	goModPath := filepath.Join(pluginDir, "go.mod")
	content, err := os.ReadFile(goModPath)
	if err != nil {
		// Required-files check already reports missing go.mod; skip silently here.
		return "", nil
	}
	src := string(content)

	moduleMatch := moduleRe.FindStringSubmatch(src)
	modulePath := ""
	if len(moduleMatch) >= 2 {
		modulePath = moduleMatch[1]
	} else {
		issues = append(issues, generator.LintIssue{
			Severity: generator.LintError,
			File:     "go.mod",
			Message:  "module directive not found",
		})
	}

	// Check for the action kit dependency.
	hasActionKit := false
	if strings.Contains(src, actionKitMod) {
		hasActionKit = true
	}
	if !hasActionKit {
		issues = append(issues, generator.LintIssue{
			Severity: generator.LintError,
			File:     "go.mod",
			Message:  "missing required dependency " + actionKitMod,
		})
	}

	// Warn on active (non-commented) replace directives that point at nonexistent paths.
	for _, m := range replaceRe.FindAllStringSubmatchIndex(src, -1) {
		fullMatch := src[m[0]:m[1]]
		// Skip if the line is commented out.
		lineStart := strings.LastIndex(src[:m[0]], "\n") + 1
		linePrefix := strings.TrimSpace(src[lineStart:m[0]])
		if strings.HasPrefix(linePrefix, "//") {
			continue
		}
		target := src[m[4]:m[5]]
		if strings.HasPrefix(target, ".") || strings.HasPrefix(target, "/") {
			absTarget := target
			if !filepath.IsAbs(target) {
				absTarget = filepath.Join(pluginDir, target)
			}
			if _, err := os.Stat(absTarget); err != nil {
				issues = append(issues, generator.LintIssue{
					Severity: generator.LintWarning,
					File:     "go.mod",
					Message:  fmt.Sprintf("replace directive points at nonexistent path: %s", fullMatch),
				})
			}
		}
	}

	return modulePath, issues
}

var (
	importBlockRe      = regexp.MustCompile(`(?s)import\s*\((.*?)\)`)
	blankImportLineRe  = regexp.MustCompile(`(?m)^\s*_\s+"([^"]+)"`)
	registerCallRe     = regexp.MustCompile(`actionkit\.Register\(\s*"([^"]+)"`)
	registerSupplierRe = regexp.MustCompile(`actionkit\.RegisterSupplier\(`)
	initFuncRe         = regexp.MustCompile(`func\s+init\s*\(\s*\)\s*\{`)
)

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

// lintMainGoBlankImports checks main.go imports the actions and suppliers packages.
// Only flags missing imports when the corresponding package directory has source files.
func lintMainGoBlankImports(pluginDir, modulePath string) []generator.LintIssue {
	var issues []generator.LintIssue
	mainPath := filepath.Join(pluginDir, "main.go")
	content, err := os.ReadFile(mainPath)
	if err != nil {
		return nil
	}
	if modulePath == "" {
		return nil
	}

	importBlock := importBlockRe.FindString(string(content))
	blanks := map[string]bool{}
	for _, m := range blankImportLineRe.FindAllStringSubmatch(importBlock, -1) {
		blanks[m[1]] = true
	}

	for _, sub := range []string{"actions", "suppliers"} {
		if !packageHasSources(filepath.Join(pluginDir, sub)) {
			continue
		}
		want := modulePath + "/" + sub
		if !blanks[want] {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     "main.go",
				Message:  fmt.Sprintf("missing blank import %q (required to wire %s/init() registrations)", "_ \""+want+"\"", sub),
			})
		}
	}
	return issues
}

// packageHasSources returns true if dir contains at least one Go source file
// other than doc.go and *_test.go.
func packageHasSources(dir string) bool {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return false
	}
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		name := e.Name()
		if !strings.HasSuffix(name, ".go") || strings.HasSuffix(name, "_test.go") || name == "doc.go" {
			continue
		}
		return true
	}
	return false
}

// lintActions inspects every actions/*.go file and returns the set of registered
// action names plus any issues found.
func lintActions(pluginDir string) (map[string]bool, []generator.LintIssue) {
	registered := map[string]bool{}
	var issues []generator.LintIssue

	dir := filepath.Join(pluginDir, "actions")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return registered, nil
	}

	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		name := e.Name()
		if !strings.HasSuffix(name, ".go") || strings.HasSuffix(name, "_test.go") || name == "doc.go" {
			continue
		}
		relPath := filepath.Join("actions", name)
		content, err := os.ReadFile(filepath.Join(dir, name))
		if err != nil {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     relPath,
				Message:  fmt.Sprintf("failed to read file: %v", err),
			})
			continue
		}
		src := string(content)
		registers := registerCallRe.FindAllStringSubmatch(src, -1)
		if len(registers) == 0 {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     relPath,
				Message:  "no actionkit.Register(...) call found - action will not be registered",
			})
			continue
		}
		if !initFuncRe.MatchString(src) {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintWarning,
				File:     relPath,
				Message:  "actionkit.Register found but no init() function - registration may not run on plugin startup",
			})
		}
		for _, m := range registers {
			registered[m[1]] = true
		}
	}
	return registered, issues
}

// lintSuppliers checks every suppliers/*.go file calls actionkit.RegisterSupplier in init().
func lintSuppliers(pluginDir string) []generator.LintIssue {
	var issues []generator.LintIssue
	dir := filepath.Join(pluginDir, "suppliers")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil
	}
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		name := e.Name()
		if !strings.HasSuffix(name, ".go") || strings.HasSuffix(name, "_test.go") || name == "doc.go" {
			continue
		}
		relPath := filepath.Join("suppliers", name)
		content, err := os.ReadFile(filepath.Join(dir, name))
		if err != nil {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     relPath,
				Message:  fmt.Sprintf("failed to read file: %v", err),
			})
			continue
		}
		src := string(content)
		if !registerSupplierRe.MatchString(src) {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     relPath,
				Message:  "no actionkit.RegisterSupplier(...) call found - supplier will not be registered",
			})
			continue
		}
		if !initFuncRe.MatchString(src) {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintWarning,
				File:     relPath,
				Message:  "actionkit.RegisterSupplier found but no init() function - registration may not run on plugin startup",
			})
		}
	}
	return issues
}

// lintFlows scans flows/*.json files for action references and verifies that
// each one resolves to an action registered in this plugin. Unresolved
// references emit a warning since they could legitimately be provided by an
// upstream plugin we depend on.
func lintFlows(pluginDir string, registeredActions map[string]bool) []generator.LintIssue {
	var issues []generator.LintIssue
	dir := filepath.Join(pluginDir, "flows")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil
	}
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".json") || e.Name() == "variables.json" {
			continue
		}
		relPath := filepath.Join("flows", e.Name())
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
				if !registeredActions[class] && !registeredActions[ref] {
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
