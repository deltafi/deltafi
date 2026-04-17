// ABOUTME: Go plugin generator implementing the Generator interface.
// ABOUTME: Handles plugin creation, action/supplier generation, and plugin updates.

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
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/generator"
)

// GoGenerator implements the Generator interface for Go plugins
type GoGenerator struct{}

// NewGoGenerator creates a new Go generator
func NewGoGenerator() generator.Generator {
	return &GoGenerator{}
}

// getTemplateFiles returns the map of template files to output paths for a new plugin
func getTemplateFiles() map[string]string {
	return map[string]string{
		"main.go.tmpl":                "main.go",
		"go.mod.tmpl":                 "go.mod",
		"Makefile.tmpl":               "Makefile",
		"Dockerfile.tmpl":             "Dockerfile",
		"README.md.tmpl":              "README.md",
		"HEADER.tmpl":                 "HEADER",
		".gitignore.tmpl":             ".gitignore",
		".dockerignore.tmpl":          ".dockerignore",
		"flows/data_source.json.tmpl": filepath.Join("flows", "data-source.json"),
		"flows/variables.yaml.tmpl":   filepath.Join("flows", "variables.yaml"),
		"actions/doc.go.tmpl":         filepath.Join("actions", "doc.go"),
		"suppliers/doc.go.tmpl":       filepath.Join("suppliers", "doc.go"),
	}
}

// GeneratePlugin generates a new Go plugin project
func (g *GoGenerator) GeneratePlugin(pluginName string, options *generator.PluginOptions) error {
	if err := ValidatePluginName(pluginName); err != nil {
		return err
	}

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return fmt.Errorf("repoPath not configured in development config")
	}

	pluginDir := filepath.Join(repoPath, pluginName)

	if _, err := os.Stat(pluginDir); err == nil {
		return fmt.Errorf("plugin directory already exists: %s", pluginDir)
	}

	groupID := "org.deltafi"
	if options != nil && options.GroupID != "" {
		groupID = options.GroupID
	}
	description := fmt.Sprintf("Go plugin for DeltaFi: %s", pluginName)
	if options != nil && options.Description != "" {
		description = options.Description
	}
	modulePath := ""
	if options != nil {
		modulePath = options.ModulePath
	}
	if modulePath == "" {
		return fmt.Errorf("--module flag is required for Go plugins (e.g., github.com/org/%s)", pluginName)
	}

	deltaFiVersion := generator.AdjustVersionForSnapshot(app.GetVersion())

	data := &TemplateData{
		PluginName:     pluginName,
		ModulePath:     modulePath,
		GroupID:        groupID,
		Domain:         groupID + "." + pluginName,
		Description:    description,
		DeltaFiVersion: deltaFiVersion,
	}

	files := getTemplateFiles()

	if err := os.MkdirAll(pluginDir, 0755); err != nil {
		return fmt.Errorf("failed to create plugin directory: %w", err)
	}

	for templatePath, outputPath := range files {
		content, err := RenderTemplate(templatePath, data)
		if err != nil {
			return fmt.Errorf("failed to render template %s: %w", templatePath, err)
		}

		fullPath := filepath.Join(pluginDir, outputPath)
		if err := os.MkdirAll(filepath.Dir(fullPath), 0755); err != nil {
			return fmt.Errorf("failed to create directory for %s: %w", outputPath, err)
		}

		if err := os.WriteFile(fullPath, content, 0644); err != nil {
			return fmt.Errorf("failed to write file %s: %w", outputPath, err)
		}
	}

	// Create integration directory with .gitignore to preserve it in git
	integrationDir := filepath.Join(pluginDir, "integration")
	if err := os.MkdirAll(integrationDir, 0755); err != nil {
		return fmt.Errorf("failed to create integration directory: %w", err)
	}
	if err := os.WriteFile(filepath.Join(integrationDir, ".gitignore"), []byte(""), 0644); err != nil {
		return fmt.Errorf("failed to write integration/.gitignore: %w", err)
	}

	// Write LICENSE file
	licensePath := filepath.Join(pluginDir, "LICENSE")
	if err := os.WriteFile(licensePath, []byte(getLicenseContent()), 0644); err != nil {
		return fmt.Errorf("failed to write LICENSE file: %w", err)
	}

	return nil
}

// GenerateAction generates a new action in an existing Go plugin
func (g *GoGenerator) GenerateAction(pluginName string, actionName string, actionType string, options *generator.GenerateActionOptions) error {
	if err := ValidateActionName(actionName); err != nil {
		return err
	}

	dirOverride := ""
	if options != nil {
		dirOverride = options.PluginDir
	}
	pluginDir, err := resolvePluginDir(pluginName, dirOverride)
	if err != nil {
		return err
	}
	pluginName = filepath.Base(pluginDir)

	if _, err := os.Stat(pluginDir); os.IsNotExist(err) {
		return fmt.Errorf("plugin directory does not exist: %s", pluginDir)
	}

	// Read module path from go.mod
	modulePath, err := readModulePath(filepath.Join(pluginDir, "go.mod"))
	if err != nil {
		return fmt.Errorf("failed to read module path from go.mod: %w", err)
	}

	// Read domain from main.go for flow type references
	domain, err := readDomainFromMain(filepath.Join(pluginDir, "main.go"))
	if err != nil {
		// Fall back to default domain
		domain = "org.deltafi." + pluginName
	}

	// Strip "-action" suffix if present to avoid duplication
	baseActionName := actionName
	if strings.HasSuffix(actionName, "-action") {
		baseActionName = actionName[:len(actionName)-len("-action")]
	}

	className := ActionNameToClassName(baseActionName)
	if !strings.HasSuffix(className, "Action") {
		className = className + "Action"
	}
	actionFileName := ActionNameToFileName(baseActionName)

	actionType = normalizeActionType(actionType)

	data := &TemplateData{
		PluginName:      pluginName,
		ModulePath:      modulePath,
		ActionName:      baseActionName,
		ActionClassName: className,
		ActionFileName:  actionFileName,
		Domain:          domain,
		ActionType:      actionType,
	}

	// Determine template files based on action type
	actionTemplate := getActionTemplate(actionType)
	testTemplate := getTestTemplate(actionType)

	if actionTemplate == "" || testTemplate == "" {
		return fmt.Errorf("unsupported action type: %s", actionType)
	}

	actionPath := filepath.Join(pluginDir, "actions", actionFileName+"_action.go")
	testPath := filepath.Join(pluginDir, "actions", actionFileName+"_action_test.go")
	actionExists := fileExists(actionPath)
	testExists := fileExists(testPath)

	if actionExists && testExists {
		return fmt.Errorf("action and test already exist for %q (action: %s, test: %s)", actionName, actionPath, testPath)
	}

	if err := os.MkdirAll(filepath.Dir(actionPath), 0755); err != nil {
		return fmt.Errorf("failed to create actions directory: %w", err)
	}

	if !actionExists {
		actionContent, err := RenderTemplate(actionTemplate, data)
		if err != nil {
			return fmt.Errorf("failed to render action template: %w", err)
		}
		if err := os.WriteFile(actionPath, actionContent, 0644); err != nil {
			return fmt.Errorf("failed to write action file: %w", err)
		}
		fmt.Printf("Wrote %s\n", actionPath)

		// Generate the options YAML file alongside the action
		optionsTemplate := getOptionsTemplate(actionType)
		if optionsTemplate != "" {
			optionsPath := filepath.Join(pluginDir, "actions", actionFileName+"_action.yaml")
			optionsContent, err := RenderTemplate(optionsTemplate, data)
			if err != nil {
				return fmt.Errorf("failed to render options template: %w", err)
			}
			if err := os.WriteFile(optionsPath, optionsContent, 0644); err != nil {
				return fmt.Errorf("failed to write options file: %w", err)
			}
			fmt.Printf("Wrote %s\n", optionsPath)
		}
	} else {
		fmt.Printf("Action %s already exists; generating test only.\n", actionPath)
	}

	if !testExists {
		testContent, err := RenderTemplate(testTemplate, data)
		if err != nil {
			return fmt.Errorf("failed to render test template: %w", err)
		}
		if err := os.WriteFile(testPath, testContent, 0644); err != nil {
			return fmt.Errorf("failed to write test file: %w", err)
		}
		fmt.Printf("Wrote %s\n", testPath)
	}

	// Generate flow JSON file
	flowTemplate := getFlowTemplate(actionType)
	if flowTemplate != "" {
		flowContent, err := RenderTemplate(flowTemplate, data)
		if err != nil {
			return fmt.Errorf("failed to render flow template: %w", err)
		}

		flowFileName := getFlowFileName(actionType, actionFileName)
		flowPath := filepath.Join(pluginDir, "flows", flowFileName)
		if err := os.MkdirAll(filepath.Dir(flowPath), 0755); err != nil {
			return fmt.Errorf("failed to create flows directory: %w", err)
		}

		if err := os.WriteFile(flowPath, flowContent, 0644); err != nil {
			return fmt.Errorf("failed to write flow file: %w", err)
		}
	}

	// Ensure main.go has the blank import for the actions package
	if err := ensureBlankImport(filepath.Join(pluginDir, "main.go"), modulePath+"/actions"); err != nil {
		return fmt.Errorf("failed to update main.go imports: %w", err)
	}

	return nil
}

// GenerateSupplier generates a new lookup table supplier in an existing Go plugin
func (g *GoGenerator) GenerateSupplier(pluginName string, supplierName string, options *generator.GenerateSupplierOptions) error {
	if err := ValidateSupplierName(supplierName); err != nil {
		return err
	}

	dirOverride := ""
	if options != nil {
		dirOverride = options.PluginDir
	}
	pluginDir, err := resolvePluginDir(pluginName, dirOverride)
	if err != nil {
		return err
	}
	pluginName = filepath.Base(pluginDir)

	if _, err := os.Stat(pluginDir); os.IsNotExist(err) {
		return fmt.Errorf("plugin directory does not exist: %s", pluginDir)
	}

	modulePath, err := readModulePath(filepath.Join(pluginDir, "go.mod"))
	if err != nil {
		return fmt.Errorf("failed to read module path from go.mod: %w", err)
	}

	// Strip "-supplier" suffix if present
	baseName := supplierName
	if strings.HasSuffix(supplierName, "-supplier") {
		baseName = supplierName[:len(supplierName)-len("-supplier")]
	}

	supplierClassName := PluginNameToClassName(baseName) + "Supplier"
	supplierFileName := ActionNameToFileName(baseName) + "_supplier"

	data := &TemplateData{
		PluginName:        pluginName,
		ModulePath:        modulePath,
		SupplierClassName: supplierClassName,
		SupplierFileName:  supplierFileName,
	}

	content, err := RenderTemplate("suppliers/supplier.go.tmpl", data)
	if err != nil {
		return fmt.Errorf("failed to render supplier template: %w", err)
	}

	supplierPath := filepath.Join(pluginDir, "suppliers", supplierFileName+".go")
	if err := os.MkdirAll(filepath.Dir(supplierPath), 0755); err != nil {
		return fmt.Errorf("failed to create suppliers directory: %w", err)
	}

	if err := os.WriteFile(supplierPath, content, 0644); err != nil {
		return fmt.Errorf("failed to write supplier file: %w", err)
	}

	// Ensure main.go has the blank import for the suppliers package
	if err := ensureBlankImport(filepath.Join(pluginDir, "main.go"), modulePath+"/suppliers"); err != nil {
		return fmt.Errorf("failed to update main.go imports: %w", err)
	}

	return nil
}

// resolvePluginDir returns the absolute plugin directory either from an explicit
// override or by joining the configured repoPath with pluginName.
func resolvePluginDir(pluginName, dirOverride string) (string, error) {
	if dirOverride != "" {
		return dirOverride, nil
	}
	if err := ValidatePluginName(pluginName); err != nil {
		return "", err
	}
	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return "", fmt.Errorf("repoPath not configured in development config")
	}
	return filepath.Join(repoPath, pluginName), nil
}

// fileExists returns true when path exists and is a regular file or directory.
func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

// UpdatePlugin updates an existing Go plugin with the latest templates
func (g *GoGenerator) UpdatePlugin(pluginName string, options *generator.UpdateOptions) error {
	var pluginDir string
	if options != nil && options.PluginDir != "" {
		pluginDir = options.PluginDir
		pluginName = filepath.Base(pluginDir)
	} else {
		if err := ValidatePluginName(pluginName); err != nil {
			return err
		}
		config := app.GetInstance().GetConfig()
		repoPath := config.Development.RepoPath
		if repoPath == "" {
			return fmt.Errorf("repoPath not configured in development config")
		}
		pluginDir = filepath.Join(repoPath, pluginName)
	}

	if _, err := os.Stat(pluginDir); os.IsNotExist(err) {
		return fmt.Errorf("plugin directory does not exist: %s", pluginDir)
	}

	// Read existing plugin info
	modulePath, err := readModulePath(filepath.Join(pluginDir, "go.mod"))
	if err != nil {
		return fmt.Errorf("failed to read module path: %w", err)
	}

	groupID, domain, err := readPluginCoordinates(filepath.Join(pluginDir, "main.go"))
	if err != nil {
		groupID = "org.deltafi"
		domain = groupID + "." + pluginName
	}

	description := fmt.Sprintf("Go plugin for DeltaFi: %s", pluginName)
	if desc, err := readDescription(filepath.Join(pluginDir, "main.go")); err == nil && desc != "" {
		description = desc
	}

	deltaFiVersion := generator.AdjustVersionForSnapshot(app.GetVersion())

	data := &TemplateData{
		PluginName:     pluginName,
		ModulePath:     modulePath,
		GroupID:        groupID,
		Domain:         domain,
		Description:    description,
		DeltaFiVersion: deltaFiVersion,
	}

	// Ensure integration directory exists
	integrationDir := filepath.Join(pluginDir, "integration")
	if _, err := os.Stat(integrationDir); os.IsNotExist(err) {
		if err := os.MkdirAll(integrationDir, 0755); err != nil {
			return fmt.Errorf("failed to create integration directory: %w", err)
		}
		if err := os.WriteFile(filepath.Join(integrationDir, ".gitignore"), []byte(""), 0644); err != nil {
			return fmt.Errorf("failed to write integration/.gitignore: %w", err)
		}
	}

	files := getTemplateFiles()

	includeSource := options != nil && options.IncludeSource

	var diffs []*generator.FileDiff
	var filesToUpdate []string

	for templatePath, outputPath := range files {
		// User-owned source files are skipped by default to keep update safe
		// to run after the user has customized them. Pass --include-source to
		// re-render them from the templates.
		if !includeSource && outputPath == "main.go" {
			continue
		}

		fullOutputPath := filepath.Join(pluginDir, outputPath)

		oldContent, err := generator.ReadFileIfExists(fullOutputPath)
		if err != nil {
			return fmt.Errorf("failed to read existing file %s: %w", fullOutputPath, err)
		}

		// Flow definition files are user-owned; skip them if they already exist.
		base := filepath.Base(outputPath)
		if oldContent != nil && (base == "variables.yaml" || base == "data-source.json") {
			continue
		}

		// Skip the Dockerfile entirely if the user has opted out of updates.
		if base == "Dockerfile" && oldContent != nil && generator.IsCustomDockerfile(oldContent) {
			continue
		}

		newContent, err := RenderTemplate(templatePath, data)
		if err != nil {
			continue
		}

		// Preserve go.mod dependencies (only update module path and action kit version)
		if outputPath == "go.mod" && oldContent != nil {
			newContent = preserveGoModDependencies(oldContent, newContent)
		}

		// Preserve user-added and user-modified ENV variables in the Dockerfile
		if outputPath == "Dockerfile" && oldContent != nil {
			newContent = generator.PreserveDockerfileEnvVars(oldContent, newContent)
		}

		// Preserve blank imports for the plugin's actions/suppliers packages,
		// which are added dynamically by GenerateAction/GenerateSupplier and
		// would otherwise be wiped out by re-rendering main.go.tmpl.
		if outputPath == "main.go" && oldContent != nil {
			newContent = preserveMainBlankImports(oldContent, newContent, modulePath)
		}

		diff := generator.CompareFiles(outputPath, oldContent, newContent)
		diffs = append(diffs, diff)

		if diff.IsModified || diff.IsNew {
			filesToUpdate = append(filesToUpdate, outputPath)
		}
	}

	if options != nil && options.DryRun {
		fmt.Println("Dry run - files that would be updated:")
		for _, path := range filesToUpdate {
			fmt.Printf("  - %s\n", path)
		}
		return nil
	}

	if len(filesToUpdate) == 0 {
		fmt.Println("Plugin is up to date - no changes needed")
		return nil
	}

	if options != nil && options.Interactive {
		return g.handleInteractiveUpdate(pluginDir, diffs, filesToUpdate)
	}

	if options != nil && options.Force {
		return g.applyUpdates(pluginDir, diffs)
	}

	return g.handleDefaultUpdate(pluginDir, diffs, filesToUpdate)
}

// handleInteractiveUpdate handles interactive file updates with merge prompts
func (g *GoGenerator) handleInteractiveUpdate(pluginDir string, diffs []*generator.FileDiff, filesToUpdate []string) error {
	fmt.Println("The following files have changes:")
	for _, path := range filesToUpdate {
		fmt.Printf("  - %s\n", path)
	}
	fmt.Println("\nFor each file, you can:")
	fmt.Println("  (y)es - Accept the new template version")
	fmt.Println("  (n)o - Keep the existing file")
	fmt.Println("  (d)iff - Show the diff")
	fmt.Println("  (a)ll - Accept all remaining changes")
	fmt.Println("  (q)uit - Cancel update")

	processed := make(map[string]bool)
	for i, diff := range diffs {
		if diff.IsIdentical || diff.IsDeleted || processed[diff.Path] {
			continue
		}

		fmt.Printf("\n--- %s ---\n", diff.Path)
		if diff.IsNew {
			fmt.Println("New file - will be created")
		} else {
			fmt.Println("File has been modified")
		}

		var action string
		fmt.Print("Update this file? [y/n/d/a/q]: ")
		fmt.Scanln(&action)

		switch strings.ToLower(action) {
		case "y", "yes":
			if err := g.writeFile(pluginDir, diff.Path, diff.NewContent); err != nil {
				return fmt.Errorf("failed to update %s: %w", diff.Path, err)
			}
			fmt.Printf("Updated %s\n", diff.Path)
		case "n", "no":
			fmt.Printf("Skipped %s\n", diff.Path)
		case "d", "diff":
			fmt.Println(diff.GenerateSideBySideDiff())
			fmt.Print("Update this file? [y/n]: ")
			fmt.Scanln(&action)
			if strings.ToLower(action) == "y" || strings.ToLower(action) == "yes" {
				if err := g.writeFile(pluginDir, diff.Path, diff.NewContent); err != nil {
					return fmt.Errorf("failed to update %s: %w", diff.Path, err)
				}
				fmt.Printf("Updated %s\n", diff.Path)
			} else {
				fmt.Printf("Skipped %s\n", diff.Path)
			}
		case "a", "all":
			applied := 0
			for j := i; j < len(diffs); j++ {
				remainingDiff := diffs[j]
				if remainingDiff.IsIdentical || remainingDiff.IsDeleted || processed[remainingDiff.Path] {
					continue
				}
				if err := g.writeFile(pluginDir, remainingDiff.Path, remainingDiff.NewContent); err != nil {
					return fmt.Errorf("failed to update %s: %w", remainingDiff.Path, err)
				}
				fmt.Printf("Updated %s\n", remainingDiff.Path)
				processed[remainingDiff.Path] = true
				applied++
			}
			if applied > 0 {
				fmt.Printf("\nSuccessfully updated %d file(s)\n", applied)
			}
			return nil
		case "q", "quit":
			fmt.Println("Update cancelled")
			return nil
		default:
			fmt.Println("Invalid option, skipping")
		}
		processed[diff.Path] = true
	}

	return nil
}

// handleDefaultUpdate shows summary and asks for confirmation
func (g *GoGenerator) handleDefaultUpdate(pluginDir string, diffs []*generator.FileDiff, filesToUpdate []string) error {
	fmt.Printf("The following %d file(s) will be updated:\n", len(filesToUpdate))
	for _, path := range filesToUpdate {
		fmt.Printf("  - %s\n", path)
	}

	fmt.Print("\nProceed with update? [y/N]: ")
	var response string
	fmt.Scanln(&response)

	if strings.ToLower(response) != "y" && strings.ToLower(response) != "yes" {
		fmt.Println("Update cancelled")
		return nil
	}

	return g.applyUpdates(pluginDir, diffs)
}

// applyUpdates applies all updates to files
func (g *GoGenerator) applyUpdates(pluginDir string, diffs []*generator.FileDiff) error {
	applied := 0
	for _, diff := range diffs {
		if diff.IsIdentical || diff.IsDeleted {
			continue
		}

		if err := g.writeFile(pluginDir, diff.Path, diff.NewContent); err != nil {
			return fmt.Errorf("failed to update %s: %w", diff.Path, err)
		}
		applied++
	}

	if applied > 0 {
		fmt.Printf("Successfully updated %d file(s)\n", applied)
	}
	return nil
}

// writeFile writes content to a file, creating directories as needed
func (g *GoGenerator) writeFile(pluginDir, relativePath string, content []byte) error {
	fullPath := filepath.Join(pluginDir, relativePath)
	if err := os.MkdirAll(filepath.Dir(fullPath), 0755); err != nil {
		return fmt.Errorf("failed to create directory: %w", err)
	}
	return os.WriteFile(fullPath, content, 0644)
}

// readModulePath reads the Go module path from a go.mod file
func readModulePath(goModPath string) (string, error) {
	file, err := os.Open(goModPath)
	if err != nil {
		return "", fmt.Errorf("failed to open go.mod: %w", err)
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if strings.HasPrefix(line, "module ") {
			return strings.TrimSpace(strings.TrimPrefix(line, "module")), nil
		}
	}

	return "", fmt.Errorf("module path not found in go.mod")
}

// readDomainFromMain reads the Domain field from main.go
func readDomainFromMain(mainPath string) (string, error) {
	content, err := os.ReadFile(mainPath)
	if err != nil {
		return "", err
	}

	re := regexp.MustCompile(`Domain:\s*"([^"]+)"`)
	matches := re.FindSubmatch(content)
	if len(matches) > 1 {
		return string(matches[1]), nil
	}

	return "", fmt.Errorf("domain not found in main.go")
}

// readPluginCoordinates reads GroupID and Domain from main.go
func readPluginCoordinates(mainPath string) (string, string, error) {
	content, err := os.ReadFile(mainPath)
	if err != nil {
		return "", "", err
	}

	groupRe := regexp.MustCompile(`GroupID:\s*"([^"]+)"`)
	groupMatches := groupRe.FindSubmatch(content)

	domainRe := regexp.MustCompile(`Domain:\s*"([^"]+)"`)
	domainMatches := domainRe.FindSubmatch(content)

	if len(groupMatches) <= 1 || len(domainMatches) <= 1 {
		return "", "", fmt.Errorf("plugin coordinates not found in main.go")
	}

	return string(groupMatches[1]), string(domainMatches[1]), nil
}

// readDescription reads the description variable from main.go
func readDescription(mainPath string) (string, error) {
	content, err := os.ReadFile(mainPath)
	if err != nil {
		return "", err
	}

	re := regexp.MustCompile(`description\s*=\s*"([^"]+)"`)
	matches := re.FindSubmatch(content)
	if len(matches) > 1 {
		return string(matches[1]), nil
	}

	return "", fmt.Errorf("description not found in main.go")
}

// ensureBlankImport adds a blank import to main.go if it doesn't already exist
func ensureBlankImport(mainPath string, importPath string) error {
	content, err := os.ReadFile(mainPath)
	if err != nil {
		return err
	}

	contentStr := string(content)

	// Check if import already exists
	if strings.Contains(contentStr, `"`+importPath+`"`) {
		return nil
	}

	// Find the import block and add the blank import
	importBlockRe := regexp.MustCompile(`(?s)(import\s*\()`)
	if importBlockRe.MatchString(contentStr) {
		// Add blank import inside the existing import block
		replacement := fmt.Sprintf("$1\n\t_ \"%s\"", importPath)
		contentStr = importBlockRe.ReplaceAllString(contentStr, replacement)
	} else {
		// No import block exists, create one after the package declaration
		packageRe := regexp.MustCompile(`(package main\n)`)
		replacement := fmt.Sprintf("$1\nimport (\n\t_ \"%s\"\n)\n", importPath)
		contentStr = packageRe.ReplaceAllString(contentStr, replacement)
	}

	return os.WriteFile(mainPath, []byte(contentStr), 0644)
}

// preserveMainBlankImports re-injects blank imports under the plugin's module
// path (e.g. `_ "<modulePath>/actions"`) from old main.go into a freshly
// rendered main.go. These imports are added dynamically by GenerateAction and
// GenerateSupplier and are not in main.go.tmpl, so re-rendering would drop
// them and break action/supplier registration.
func preserveMainBlankImports(oldContent, newContent []byte, modulePath string) []byte {
	if modulePath == "" {
		return newContent
	}

	importRe := regexp.MustCompile(`_\s+"(` + regexp.QuoteMeta(modulePath) + `/[^"]+)"`)
	matches := importRe.FindAllStringSubmatch(string(oldContent), -1)
	if len(matches) == 0 {
		return newContent
	}

	newStr := string(newContent)
	var missing []string
	for _, m := range matches {
		importPath := m[1]
		if strings.Contains(newStr, `"`+importPath+`"`) {
			continue
		}
		missing = append(missing, importPath)
	}
	if len(missing) == 0 {
		return newContent
	}

	importBlockRe := regexp.MustCompile(`(?s)(import\s*\(\s*\n)`)
	if importBlockRe.MatchString(newStr) {
		var b strings.Builder
		for _, p := range missing {
			b.WriteString("\t_ \"")
			b.WriteString(p)
			b.WriteString("\"\n")
		}
		newStr = importBlockRe.ReplaceAllString(newStr, "${1}"+b.String())
		return []byte(newStr)
	}

	// No import block in the new content - create one after `package main`.
	packageRe := regexp.MustCompile(`(package main\n)`)
	var b strings.Builder
	b.WriteString("$1\nimport (\n")
	for _, p := range missing {
		b.WriteString("\t_ \"")
		b.WriteString(p)
		b.WriteString("\"\n")
	}
	b.WriteString(")\n")
	newStr = packageRe.ReplaceAllString(newStr, b.String())
	return []byte(newStr)
}

// preserveGoModDependencies preserves user-added dependencies from the old go.mod
func preserveGoModDependencies(oldContent, newContent []byte) []byte {
	// The new go.mod template only has the base dependencies (testify + action-kit).
	// Preserve any additional require blocks or replace directives from the old content.
	oldStr := string(oldContent)
	newStr := string(newContent)

	// Extract replace directives from old content and append to new
	replaceRe := regexp.MustCompile(`(?m)^(//\s*)?replace\s+.*$`)
	replaces := replaceRe.FindAllString(oldStr, -1)
	if len(replaces) > 0 {
		newStr = strings.TrimRight(newStr, "\n") + "\n"
		for _, r := range replaces {
			if !strings.Contains(newStr, r) {
				newStr += "\n" + r
			}
		}
		newStr += "\n"
	}

	// Extract indirect dependencies block from old content
	indirectRe := regexp.MustCompile(`(?s)require\s*\(\s*\n((?:\s+[^\)]+//\s*indirect\s*\n)+)\s*\)`)
	indirectMatch := indirectRe.FindString(oldStr)
	if indirectMatch != "" && !strings.Contains(newStr, "// indirect") {
		newStr = strings.TrimRight(newStr, "\n") + "\n\n" + indirectMatch + "\n"
	}

	return []byte(newStr)
}

// normalizeActionType normalizes action type names
func normalizeActionType(actionType string) string {
	lower := strings.ToLower(actionType)
	switch lower {
	case "transform":
		return "TransformAction"
	case "egress":
		return "EgressAction"
	case "ingress", "timedingress":
		return "TimedIngressAction"
	case "joiningtransform":
		return "JoiningTransformAction"
	case "transformmany", "split":
		return "TransformManyAction"
	default:
		if strings.HasSuffix(lower, "action") {
			base := lower[:len(lower)-6]
			if len(base) > 0 {
				return strings.ToUpper(base[:1]) + base[1:] + "Action"
			}
			return "Action"
		}
		return actionType
	}
}

// getActionTemplate returns the template path for an action type
func getActionTemplate(actionType string) string {
	switch strings.ToLower(actionType) {
	case "transformaction", "transform":
		return "actions/transform_action.go.tmpl"
	case "egressaction", "egress":
		return "actions/egress_action.go.tmpl"
	case "ingressaction", "ingress", "timedingressaction", "timedingress":
		return "actions/timed_ingress_action.go.tmpl"
	case "joiningtransformaction", "joiningtransform":
		return "actions/joining_transform_action.go.tmpl"
	case "transformmanyaction", "transformmany", "split":
		return "actions/split_transform_action.go.tmpl"
	default:
		return ""
	}
}

// getTestTemplate returns the template path for a test file based on action type
func getTestTemplate(actionType string) string {
	switch strings.ToLower(actionType) {
	case "transformaction", "transform":
		return "actions/transform_action_test.go.tmpl"
	case "egressaction", "egress":
		return "actions/egress_action_test.go.tmpl"
	case "ingressaction", "ingress", "timedingressaction", "timedingress":
		return "actions/timed_ingress_action_test.go.tmpl"
	case "joiningtransformaction", "joiningtransform":
		return "actions/joining_transform_action_test.go.tmpl"
	case "transformmanyaction", "transformmany", "split":
		return "actions/split_transform_action_test.go.tmpl"
	default:
		return ""
	}
}

// getOptionsTemplate returns the template path for an action options YAML file
func getOptionsTemplate(actionType string) string {
	switch strings.ToLower(actionType) {
	case "transformaction", "transform":
		return "actions/transform_action.yaml.tmpl"
	case "egressaction", "egress":
		return "actions/egress_action.yaml.tmpl"
	case "ingressaction", "ingress", "timedingressaction", "timedingress":
		return "actions/timed_ingress_action.yaml.tmpl"
	case "joiningtransformaction", "joiningtransform":
		return "actions/joining_transform_action.yaml.tmpl"
	case "transformmanyaction", "transformmany", "split":
		return "actions/split_transform_action.yaml.tmpl"
	default:
		return ""
	}
}

// getFlowTemplate returns the template path for a flow JSON file based on action type
func getFlowTemplate(actionType string) string {
	switch strings.ToLower(actionType) {
	case "transformaction", "transform", "joiningtransformaction", "joiningtransform", "transformmanyaction", "transformmany", "split":
		return "flows/transform_flow.json.tmpl"
	case "egressaction", "egress":
		return "flows/egress_flow.json.tmpl"
	case "ingressaction", "ingress", "timedingressaction", "timedingress":
		return "flows/ingress_flow.json.tmpl"
	default:
		return ""
	}
}

// getFlowFileName returns the flow JSON file name based on action type and action file name
func getFlowFileName(actionType string, actionFileName string) string {
	switch strings.ToLower(actionType) {
	case "egressaction", "egress":
		return actionFileName + "-data-sink.json"
	case "ingressaction", "ingress", "timedingressaction", "timedingress":
		return actionFileName + "-data-source.json"
	default:
		return actionFileName + ".json"
	}
}

// getLicenseContent returns the Apache 2.0 license content
func getLicenseContent() string {
	return `                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
   implied. See the License for the specific language governing
   permissions and limitations under the License.`
}
