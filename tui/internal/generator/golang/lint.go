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
	"gopkg.in/yaml.v3"
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

	declaredVars, varIssues := loadFlowVariables(pluginDir)
	issues = append(issues, varIssues...)
	issues = append(issues, lintFlows(pluginDir, registeredActions, declaredVars)...)

	issues = append(issues, lintDockerfileGoVersion(pluginDir)...)
	issues = append(issues, lintGoWorkGitignore(pluginDir)...)

	issues = append(issues, lintIntegrationTests(pluginDir)...)

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
	registerCallRe     = regexp.MustCompile(`actionkit\.Register(Transform|JoinTransform|Egress|Ingress)\(\s*"([^"]+)"`)
	registerSupplierRe = regexp.MustCompile(`actionkit\.RegisterSupplier\(`)
	initFuncRe         = regexp.MustCompile(`func\s+init\s*\(\s*\)\s*\{`)
	withParamsRe       = regexp.MustCompile(`WithParams\(`)
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
			importLine := "\t_ \"" + want + "\""
			filePath := mainPath
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     "main.go",
				Message:  fmt.Sprintf("missing blank import %q (required to wire %s/init() registrations)", "_ \""+want+"\"", sub),
				Fix: func() error {
					data, err := os.ReadFile(filePath)
					if err != nil {
						return err
					}
					src := string(data)
					// Find the import block's closing paren and insert before it.
					loc := importBlockRe.FindStringIndex(src)
					if loc == nil {
						return fmt.Errorf("no import block found in main.go")
					}
					// loc[1]-1 is the closing ')'. Insert the line before it.
					insertAt := loc[1] - 1
					fixed := src[:insertAt] + importLine + "\n" + src[insertAt:]
					return os.WriteFile(filePath, []byte(fixed), 0644)
				},
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

// registerMethodPatterns maps each Register* variant to a regex that must match
// somewhere in the same file, verifying the action struct has the expected method.
var registerMethodPatterns = map[string]*regexp.Regexp{
	"Transform":     regexp.MustCompile(`func\s+\([^)]+\)\s+Transform\(\s*\w+\s+\*actionkit\.Deltafile`),
	"JoinTransform": regexp.MustCompile(`func\s+\([^)]+\)\s+Join\(\s*\w+\s+\[\]\*actionkit\.Deltafile`),
	"Egress":        regexp.MustCompile(`func\s+\([^)]+\)\s+Egress\(\s*\w+\s+\*actionkit\.Deltafile`),
	"Ingress":       regexp.MustCompile(`func\s+\([^)]+\)\s+Ingress\(\s*\w+\s+\*actionkit\.IngressInput`),
}

// registeredActionInfo holds metadata about a registered action for cross-referencing with flows.
type registeredActionInfo struct {
	variant string // "Transform", "JoinTransform", "Egress", "Ingress"
}

// lintActions inspects every actions/*.go file and returns the set of registered
// action names (with metadata) plus any issues found.
func lintActions(pluginDir string) (map[string]registeredActionInfo, []generator.LintIssue) {
	registered := map[string]registeredActionInfo{}
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
				Message:  "no actionkit.Register<Type>(...) call found - action will not be registered",
			})
			continue
		}
		if !initFuncRe.MatchString(src) {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintWarning,
				File:     relPath,
				Message:  "actionkit.Register<Type> found but no init() function - registration may not run on plugin startup",
			})
		}
		for _, m := range registers {
			regVariant := m[1] // "Transform", "JoinTransform", etc.
			actionName := m[2]
			if prior, ok := registeredBy[actionName]; ok && prior != relPath {
				issues = append(issues, generator.LintIssue{
					Severity: generator.LintError,
					File:     relPath,
					Message:  fmt.Sprintf("action name %q is also registered in %s - duplicate registrations conflict at runtime", actionName, prior),
				})
				continue
			}
			registeredBy[actionName] = relPath
			registered[actionName] = registeredActionInfo{variant: regVariant}

			// Verify the file contains the expected method for the registration variant.
			if pat, ok := registerMethodPatterns[regVariant]; ok {
				if !pat.MatchString(src) {
					issues = append(issues, generator.LintIssue{
						Severity: generator.LintError,
						File:     relPath,
						Message:  fmt.Sprintf("Register%s used but no matching method signature found in this file", regVariant),
					})
				}
			}
		}

		if len(registers) > 1 {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintInfo,
				File:     relPath,
				Message:  fmt.Sprintf("file registers %d actions - convention is one action per file", len(registers)),
			})
		}

		// Warn when WithParams is chained but the param struct appears to have no exported fields,
		// or when a param struct has fields but WithParams is not called (defaults won't be set).
		issues = append(issues, lintWithParams(src, relPath)...)
	}
	return registered, issues
}

// paramStructRe matches a type declaration for a params struct and captures its body.
var paramStructRe = regexp.MustCompile(`type\s+\w+Params\s+struct\s*\{([^}]*)\}`)

// exportedFieldRe matches an exported (capitalized) field in a struct body.
var exportedFieldRe = regexp.MustCompile(`(?m)^\s*[A-Z]\w*\s+`)

// lintWithParams checks consistency between WithParams usage and param struct fields.
func lintWithParams(src, relPath string) []generator.LintIssue {
	var issues []generator.LintIssue
	hasWithParams := withParamsRe.MatchString(src)

	for _, m := range paramStructRe.FindAllStringSubmatch(src, -1) {
		body := m[1]
		hasFields := exportedFieldRe.MatchString(body)
		if hasWithParams && !hasFields {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintWarning,
				File:     relPath,
				Message:  "WithParams is called but the params struct has no exported fields",
			})
		}
		if hasFields && !hasWithParams {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintInfo,
				File:     relPath,
				Message:  "params struct has exported fields but WithParams is not called - default values will be zero-valued",
			})
		}
	}
	return issues
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

// loadFlowVariables returns the set of variable names declared in
// flows/variables.yaml and any issues found (e.g. duplicates). A nil names
// result means the file is missing or unreadable; callers should treat that
// as "skip the variable-reference check" rather than "no variables declared".
func loadFlowVariables(pluginDir string) (map[string]bool, []generator.LintIssue) {
	path := filepath.Join(pluginDir, "flows", "variables.yaml")
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, nil
	}
	var vars []struct {
		Name string `yaml:"name"`
	}
	if err := yaml.Unmarshal(content, &vars); err != nil {
		return nil, nil
	}
	names := make(map[string]bool, len(vars))
	var issues []generator.LintIssue
	for _, v := range vars {
		if v.Name == "" {
			continue
		}
		if names[v.Name] {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintWarning,
				File:     filepath.Join("flows", "variables.yaml"),
				Message:  fmt.Sprintf("variable %q is declared more than once - later declaration silently overrides", v.Name),
			})
		}
		names[v.Name] = true
	}
	return names, issues
}

// lintFlows scans flows/*.json files for action references, required fields,
// and variable placeholders, verifying that each resolves to something
// declared in this plugin. Unresolved action references emit a warning since
// they could legitimately be provided by an upstream plugin we depend on.
// If declaredVars is nil the variable-reference check is skipped.
func lintFlows(pluginDir string, registeredActions map[string]registeredActionInfo, declaredVars map[string]bool) []generator.LintIssue {
	var issues []generator.LintIssue
	dir := filepath.Join(pluginDir, "flows")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil
	}
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".json") {
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
			actionEntries := extractActionEntries(doc[field])
			for _, entry := range actionEntries {
				class := entry.typeRef
				if i := strings.LastIndex(entry.typeRef, "."); i >= 0 {
					class = entry.typeRef[i+1:]
				}
				info, found := registeredActions[class]
				if !found {
					info, found = registeredActions[entry.typeRef]
				}
				if !found {
					issues = append(issues, generator.LintIssue{
						Severity: generator.LintWarning,
						File:     relPath,
						Message:  fmt.Sprintf("references action %q which is not registered in this plugin (may be provided by another plugin)", entry.typeRef),
					})
					continue
				}
				if info.variant == "JoinTransform" && !entry.hasJoin {
					issues = append(issues, generator.LintIssue{
						Severity: generator.LintWarning,
						File:     relPath,
						Message:  fmt.Sprintf("action %q is registered with RegisterJoinTransform but this flow entry has no join configuration", entry.typeRef),
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
					Message:  fmt.Sprintf("references variable %q which is not declared in flows/variables.yaml", varName),
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
			filePath := docPath
			wrongPkg := m[1]
			correctPkg := sub
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     filepath.Join(sub, "doc.go"),
				Message:  fmt.Sprintf("package %q does not match directory name %q - blank import from main.go will not wire init() registrations", wrongPkg, correctPkg),
				Fix: func() error {
					data, err := os.ReadFile(filePath)
					if err != nil {
						return err
					}
					fixed := packageDeclRe.ReplaceAllString(string(data), "package "+correctPkg)
					return os.WriteFile(filePath, []byte(fixed), 0644)
				},
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

// lintIntegrationTests validates that integration/*.json files contain valid JSON.
func lintIntegrationTests(pluginDir string) []generator.LintIssue {
	var issues []generator.LintIssue
	dir := filepath.Join(pluginDir, "integration")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil
	}
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".json") {
			continue
		}
		relPath := filepath.Join("integration", e.Name())
		content, err := os.ReadFile(filepath.Join(dir, e.Name()))
		if err != nil {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     relPath,
				Message:  fmt.Sprintf("failed to read file: %v", err),
			})
			continue
		}
		if !json.Valid(content) {
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintError,
				File:     relPath,
				Message:  "invalid JSON",
			})
		}
	}
	return issues
}

var (
	goModVersionRe        = regexp.MustCompile(`(?m)^go\s+(\d+\.\d+)`)
	dockerfileGolangImgRe = regexp.MustCompile(`(?m)^FROM\s+.*golang:(\d+\.\d+)`)
)

// lintDockerfileGoVersion checks that the Go version in the Dockerfile matches go.mod.
func lintDockerfileGoVersion(pluginDir string) []generator.LintIssue {
	modContent, err := os.ReadFile(filepath.Join(pluginDir, "go.mod"))
	if err != nil {
		return nil
	}
	modMatch := goModVersionRe.FindStringSubmatch(string(modContent))
	if len(modMatch) < 2 {
		return nil
	}
	modVersion := modMatch[1]

	dockerContent, err := os.ReadFile(filepath.Join(pluginDir, "Dockerfile"))
	if err != nil {
		return nil
	}

	var issues []generator.LintIssue
	for _, m := range dockerfileGolangImgRe.FindAllStringSubmatch(string(dockerContent), -1) {
		imgVersion := m[1]
		if imgVersion != modVersion {
			dockerfilePath := filepath.Join(pluginDir, "Dockerfile")
			staleVersion := imgVersion
			correctVersion := modVersion
			issues = append(issues, generator.LintIssue{
				Severity: generator.LintWarning,
				File:     "Dockerfile",
				Message:  fmt.Sprintf("golang:%s image does not match go.mod version %s", staleVersion, correctVersion),
				Fix: func() error {
					data, err := os.ReadFile(dockerfilePath)
					if err != nil {
						return err
					}
					fixed := strings.ReplaceAll(string(data), "golang:"+staleVersion, "golang:"+correctVersion)
					return os.WriteFile(dockerfilePath, []byte(fixed), 0644)
				},
			})
		}
	}
	return issues
}

// lintGoWorkGitignore warns when go.work exists but is not in .gitignore.
func lintGoWorkGitignore(pluginDir string) []generator.LintIssue {
	if _, err := os.Stat(filepath.Join(pluginDir, "go.work")); err != nil {
		return nil
	}
	gitignoreContent, err := os.ReadFile(filepath.Join(pluginDir, ".gitignore"))
	if err != nil {
		return []generator.LintIssue{{
			Severity: generator.LintWarning,
			File:     "go.work",
			Message:  "go.work exists but .gitignore is missing - go.work should not be committed",
		}}
	}
	for line := range strings.SplitSeq(string(gitignoreContent), "\n") {
		if strings.TrimSpace(line) == "go.work" || strings.TrimSpace(line) == "go.work*" {
			return nil
		}
	}
	gitignorePath := filepath.Join(pluginDir, ".gitignore")
	return []generator.LintIssue{{
		Severity: generator.LintWarning,
		File:     "go.work",
		Message:  "go.work exists but is not listed in .gitignore - go.work should not be committed",
		Fix: func() error {
			f, err := os.OpenFile(gitignorePath, os.O_APPEND|os.O_WRONLY, 0644)
			if err != nil {
				return err
			}
			defer f.Close()
			_, err = f.WriteString("\ngo.work\ngo.work.sum\n")
			return err
		},
	}}
}

// licenseMarker is a distinctive phrase from the Apache 2.0 license block that
// the plugin HEADER file contains. We use it to detect whether a .go file
// already carries the license.
const licenseMarker = "Licensed under the Apache License"

// copyrightYearRe matches "Copyright YYYY-YYYY" or "Copyright YYYY" in a comment.
var copyrightYearRe = regexp.MustCompile(`Copyright\s+(\d{4}(?:-\d{4})?)`)

// lintLicenseHeaders walks the plugin's top-level Go sources and warns on any
// file whose leading comment block does not include the Apache license text
// or has a stale copyright year range. The check only runs when a HEADER file
// exists at the plugin root.
func lintLicenseHeaders(pluginDir string) []generator.LintIssue {
	headerPath := filepath.Join(pluginDir, "HEADER")
	headerContent, err := os.ReadFile(headerPath)
	if err != nil {
		return nil
	}
	headerComment := wrapGoComment(string(headerContent))

	// Extract the canonical copyright year range from the HEADER file.
	var headerYears string
	if m := copyrightYearRe.FindStringSubmatch(string(headerContent)); len(m) >= 2 {
		headerYears = m[1]
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
			absPath := filepath.Join(dir, e.Name())
			content, err := os.ReadFile(absPath)
			if err != nil {
				continue
			}
			src := string(content)
			head := leadingComments(src)
			rest := src[len(head):]

			if !strings.Contains(head, licenseMarker) {
				// No license at all — fix by prepending.
				filePath := absPath
				issues = append(issues, generator.LintIssue{
					Severity: generator.LintWarning,
					File:     rel,
					Message:  "missing Apache license header block",
					Fix: func() error {
						return os.WriteFile(filePath, []byte(headerComment+rest), 0644)
					},
				})
				continue
			}

			// License present — check for stale copyright years.
			if headerYears != "" {
				if m := copyrightYearRe.FindStringSubmatch(head); len(m) >= 2 && m[1] != headerYears {
					filePath := absPath
					staleYears := m[1]
					issues = append(issues, generator.LintIssue{
						Severity: generator.LintWarning,
						File:     rel,
						Message:  fmt.Sprintf("copyright years %s do not match HEADER (%s)", staleYears, headerYears),
						Fix: func() error {
							// Replace the existing license block with the canonical one.
							return os.WriteFile(filePath, []byte(headerComment+rest), 0644)
						},
					})
				}
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

// wrapGoComment wraps raw header text in a Go block comment: /* ... */
// Each non-empty line is prefixed with " *    " to match the project convention.
func wrapGoComment(raw string) string {
	var b strings.Builder
	b.WriteString("/*\n")
	for line := range strings.SplitSeq(strings.TrimRight(raw, "\n"), "\n") {
		if strings.TrimSpace(line) == "" {
			b.WriteString(" *\n")
		} else {
			b.WriteString(" *")
			b.WriteString(line)
			b.WriteByte('\n')
		}
	}
	b.WriteString(" */\n")
	return b.String()
}

// flowActionEntry holds info about an action reference inside a flow JSON document.
type flowActionEntry struct {
	typeRef string
	hasJoin bool
}

// extractActionEntries walks an action-bearing field (an object or array of
// objects) and returns info about every action reference it finds.
func extractActionEntries(node any) []flowActionEntry {
	if node == nil {
		return nil
	}
	var entries []flowActionEntry
	switch v := node.(type) {
	case map[string]any:
		if t, ok := v["type"].(string); ok && t != "" {
			_, hasJoin := v["join"]
			entries = append(entries, flowActionEntry{typeRef: t, hasJoin: hasJoin})
		}
	case []any:
		for _, item := range v {
			entries = append(entries, extractActionEntries(item)...)
		}
	}
	return entries
}
