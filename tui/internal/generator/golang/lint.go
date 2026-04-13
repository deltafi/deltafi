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

	issues = append(issues, lintDocGoPackages(pluginDir)...)

	registeredActions, actionIssues := lintActions(pluginDir)
	issues = append(issues, actionIssues...)

	issues = append(issues, lintActionsHaveTests(pluginDir)...)

	issues = append(issues, lintSuppliers(pluginDir)...)

	declaredVars := loadFlowVariableNames(pluginDir)
	issues = append(issues, lintFlows(pluginDir, registeredActions, declaredVars)...)

	issues = append(issues, lintLicenseHeaders(pluginDir)...)

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

	// Track where each action name was first registered so we can point at
	// both sites when a duplicate appears.
	registeredBy := map[string]string{}

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
			actionName := m[1]
			if prior, ok := registeredBy[actionName]; ok && prior != relPath {
				issues = append(issues, generator.LintIssue{
					Severity: generator.LintError,
					File:     relPath,
					Message:  fmt.Sprintf("action name %q is also registered in %s - duplicate registrations conflict at runtime", actionName, prior),
				})
				continue
			}
			registeredBy[actionName] = relPath
			registered[actionName] = true
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

// flowVarRefRe matches ${variableName} placeholders inside flow JSON payloads.
var flowVarRefRe = regexp.MustCompile(`\$\{([A-Za-z_][A-Za-z0-9_]*)\}`)

// loadFlowVariableNames returns the set of variable names declared in
// flows/variables.json. A nil result means the file is missing or unreadable;
// callers should treat that as "skip the variable-reference check" rather
// than "no variables declared", since a missing file would otherwise flag
// every template placeholder as unresolved.
func loadFlowVariableNames(pluginDir string) map[string]bool {
	path := filepath.Join(pluginDir, "flows", "variables.json")
	content, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	var vars []struct {
		Name string `json:"name"`
	}
	if err := json.Unmarshal(content, &vars); err != nil {
		return nil
	}
	names := make(map[string]bool, len(vars))
	for _, v := range vars {
		if v.Name != "" {
			names[v.Name] = true
		}
	}
	return names
}

// lintFlows scans flows/*.json files for action references, required fields,
// and variable placeholders, verifying that each resolves to something
// declared in this plugin. Unresolved action references emit a warning since
// they could legitimately be provided by an upstream plugin we depend on.
// If declaredVars is nil the variable-reference check is skipped.
func lintFlows(pluginDir string, registeredActions map[string]bool, declaredVars map[string]bool) []generator.LintIssue {
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

		if name, _ := doc["name"].(string); name == "" {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     relPath,
				Message:  "missing required \"name\" field",
			})
		}
		if typ, _ := doc["type"].(string); typ == "" {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     relPath,
				Message:  "missing required \"type\" field",
			})
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

		if declaredVars != nil {
			seen := map[string]bool{}
			for _, m := range flowVarRefRe.FindAllStringSubmatch(string(content), -1) {
				varName := m[1]
				if seen[varName] || declaredVars[varName] {
					continue
				}
				seen[varName] = true
				issues = append(issues, generator.LintIssue{
					Severity: generator.LintWarning,
					File:     relPath,
					Message:  fmt.Sprintf("references variable %q which is not declared in flows/variables.json", varName),
				})
			}
		}
	}
	return issues
}

// packageDeclRe matches a Go package declaration.
var packageDeclRe = regexp.MustCompile(`(?m)^package\s+(\w+)`)

// lintDocGoPackages verifies that actions/doc.go and suppliers/doc.go declare
// the expected package names. A mismatched package name silently breaks the
// blank-import wiring because the import path resolves but init() runs in a
// different package than main.go expects.
func lintDocGoPackages(pluginDir string) []generator.LintIssue {
	var issues []generator.LintIssue
	for _, sub := range []string{"actions", "suppliers"} {
		docPath := filepath.Join(pluginDir, sub, "doc.go")
		content, err := os.ReadFile(docPath)
		if err != nil {
			// lintRequiredFiles already reports a missing doc.go.
			continue
		}
		m := packageDeclRe.FindStringSubmatch(string(content))
		if len(m) < 2 {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     filepath.Join(sub, "doc.go"),
				Message:  "missing package declaration",
			})
			continue
		}
		if m[1] != sub {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     filepath.Join(sub, "doc.go"),
				Message:  fmt.Sprintf("package %q does not match directory name %q - blank import from main.go will not wire init() registrations", m[1], sub),
			})
		}
	}
	return issues
}

// lintActionsHaveTests emits an info-level reminder when the actions package
// contains source files but no _test.go files alongside them.
func lintActionsHaveTests(pluginDir string) []generator.LintIssue {
	dir := filepath.Join(pluginDir, "actions")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil
	}
	hasSource := false
	hasTest := false
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		name := e.Name()
		if !strings.HasSuffix(name, ".go") || name == "doc.go" {
			continue
		}
		if strings.HasSuffix(name, "_test.go") {
			hasTest = true
		} else {
			hasSource = true
		}
	}
	if hasSource && !hasTest {
		return []generator.LintIssue{{
			Severity: generator.LintInfo,
			File:     "actions",
			Message:  "no _test.go files found alongside action sources - consider adding tests",
		}}
	}
	return nil
}

// licenseMarker is a distinctive phrase from the Apache 2.0 license block that
// the plugin HEADER file contains. We use it to detect whether a .go file
// already carries the license.
const licenseMarker = "Licensed under the Apache License"

// lintLicenseHeaders walks the plugin's top-level Go sources and warns on any
// file whose leading comment block does not include the Apache license text.
// The check only runs when a HEADER file exists at the plugin root.
func lintLicenseHeaders(pluginDir string) []generator.LintIssue {
	if _, err := os.Stat(filepath.Join(pluginDir, "HEADER")); err != nil {
		return nil
	}
	var issues []generator.LintIssue
	dirs := []string{".", "actions", "suppliers"}
	for _, sub := range dirs {
		dir := filepath.Join(pluginDir, sub)
		entries, err := os.ReadDir(dir)
		if err != nil {
			continue
		}
		for _, e := range entries {
			if e.IsDir() || !strings.HasSuffix(e.Name(), ".go") {
				continue
			}
			rel := e.Name()
			if sub != "." {
				rel = filepath.Join(sub, e.Name())
			}
			content, err := os.ReadFile(filepath.Join(dir, e.Name()))
			if err != nil {
				continue
			}
			// Scan only the leading comment block (everything up to the first
			// non-comment, non-blank line). This avoids false positives when
			// the license marker happens to appear in a string literal later.
			head := leadingComments(string(content))
			if !strings.Contains(head, licenseMarker) {
				issues = append(issues, generator.LintIssue{
					Severity: generator.LintWarning,
					File:     rel,
					Message:  "missing Apache license header block (HEADER file exists but the file does not carry the license)",
				})
			}
		}
	}
	return issues
}

// leadingComments returns the contiguous run of comment lines and blank lines
// at the start of a Go source file, stopping at the first code line.
func leadingComments(src string) string {
	var b strings.Builder
	inBlock := false
	for line := range strings.SplitSeq(src, "\n") {
		trimmed := strings.TrimSpace(line)
		switch {
		case inBlock:
			b.WriteString(line)
			b.WriteByte('\n')
			if strings.Contains(trimmed, "*/") {
				inBlock = false
			}
		case trimmed == "":
			b.WriteByte('\n')
		case strings.HasPrefix(trimmed, "//"):
			b.WriteString(line)
			b.WriteByte('\n')
		case strings.HasPrefix(trimmed, "/*"):
			b.WriteString(line)
			b.WriteByte('\n')
			if !strings.Contains(trimmed, "*/") {
				inBlock = true
			}
		default:
			return b.String()
		}
	}
	return b.String()
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
