/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/generator"
)

// PythonGenerator implements the Generator interface for Python plugins
type PythonGenerator struct{}

// NewPythonGenerator creates a new Python generator
func NewPythonGenerator() generator.Generator {
	return &PythonGenerator{}
}

// getTemplateFiles returns the map of template files to output paths for a given package name
func getTemplateFiles(packageName string) map[string]string {
	return map[string]string{
		"pyproject.toml.tmpl":          "pyproject.toml",
		"Dockerfile.tmpl":              "Dockerfile",
		"Makefile.tmpl":                "Makefile",
		"README.md.tmpl":               "README.md",
		"HEADER.tmpl":                  "HEADER",
		".gitignore.tmpl":              ".gitignore",
		"src/__init__.py.tmpl":         filepath.Join("src", packageName, "__init__.py"),
		"src/plugin.py.tmpl":           filepath.Join("src", packageName, "plugin.py"),
		"src/actions/__init__.py.tmpl": filepath.Join("src", packageName, "actions", "__init__.py"),
		"src/test/__init__.py.tmpl":    filepath.Join("src", packageName, "test", "__init__.py"),
		"flows/variables.yaml.tmpl":    filepath.Join("src", packageName, "flows", "variables.yaml"),
		"flows/data_source.json.tmpl":  filepath.Join("src", packageName, "flows", "data-source.json"),
	}
}

// GeneratePlugin generates a new Python plugin project
func (g *PythonGenerator) GeneratePlugin(pluginName string, options *generator.PluginOptions) error {
	if err := ValidatePluginName(pluginName); err != nil {
		return err
	}

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return fmt.Errorf("repoPath not configured in development config")
	}

	pluginDir := filepath.Join(repoPath, pluginName)

	// Check if plugin directory already exists
	if _, err := os.Stat(pluginDir); err == nil {
		return fmt.Errorf("plugin directory already exists: %s", pluginDir)
	}

	// Prepare template data
	packageName := PluginNameToPackageName(pluginName)
	groupID := "org.deltafi"
	if options != nil && options.GroupID != "" {
		groupID = options.GroupID
	}
	description := fmt.Sprintf("Python plugin for DeltaFi: %s", pluginName)
	if options != nil && options.Description != "" {
		description = options.Description
	}

	// Get DeltaFi version from TUI
	deltaFiVersion := adjustVersionForSnapshot(app.GetVersion())

	data := &TemplateData{
		PluginName:     pluginName,
		PackageName:    packageName,
		GroupID:        groupID,
		Description:    description,
		DeltaFiVersion: deltaFiVersion,
	}

	// Define files to generate
	files := getTemplateFiles(packageName)

	// Create directories
	if err := os.MkdirAll(pluginDir, 0755); err != nil {
		return fmt.Errorf("failed to create plugin directory: %w", err)
	}

	// Generate files
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

	// Copy LICENSE file from template directory (if it exists) or use a default
	licensePath := filepath.Join(pluginDir, "LICENSE")
	licenseContent := getLicenseContent()
	if err := os.WriteFile(licensePath, []byte(licenseContent), 0644); err != nil {
		return fmt.Errorf("failed to write LICENSE file: %w", err)
	}

	return nil
}

// GenerateAction generates a new action in an existing plugin
func (g *PythonGenerator) GenerateAction(pluginName string, actionName string, actionType string) error {
	if err := ValidatePluginName(pluginName); err != nil {
		return err
	}
	if err := ValidateActionName(actionName); err != nil {
		return err
	}

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return fmt.Errorf("repoPath not configured in development config")
	}

	pluginDir := filepath.Join(repoPath, pluginName)

	// Check if plugin directory exists
	if _, err := os.Stat(pluginDir); os.IsNotExist(err) {
		return fmt.Errorf("plugin directory does not exist: %s", pluginDir)
	}

	// Read namespace from existing plugin to respect project namespace
	packageName := PluginNameToPackageName(pluginName)
	pluginPath := filepath.Join(pluginDir, "src", packageName, "plugin.py")
	namespace, err := readNamespaceFromPlugin(pluginPath)
	if err != nil {
		// If we can't read the namespace, use default - this shouldn't happen for valid plugins
		namespace = fmt.Sprintf("org.deltafi.%s", packageName)
	}

	_ = namespace // Namespace is read for future use; actions don't directly reference it yet

	// Strip "-action" suffix if present to avoid duplication
	const actionSuffix = "-action"
	baseActionName := actionName
	if strings.HasSuffix(actionName, actionSuffix) {
		baseActionName = actionName[:len(actionName)-len(actionSuffix)]
	}

	// Generate class name from action name only (no plugin prefix)
	className := ActionNameToClassName(baseActionName)
	// Only add "Action" suffix if it doesn't already end with "Action"
	if !strings.HasSuffix(className, "Action") {
		className = className + "Action"
	}
	actionFileName := ActionNameToFileName(baseActionName)

	// Generate test class name that doesn't start with "Test" to avoid pytest collection
	testClassName := className + "Test"
	// Remove leading "Test" if present to avoid pytest trying to collect the class
	testClassName = strings.TrimPrefix(testClassName, "Test")

	// Normalize action type
	actionType = normalizeActionType(actionType)

	// Prepare template data
	data := &TemplateData{
		PluginName:     pluginName,
		PackageName:    packageName,
		ClassName:      className,
		TestClassName:  testClassName,
		ActionName:     baseActionName, // Use base name for metadata
		ActionFileName: actionFileName,
		ActionType:     actionType,
		GroupID:        namespace,
	}

	// Determine template files based on action type
	actionTemplate := getActionTemplate(actionType)
	testTemplate := getTestTemplate(actionType)

	if actionTemplate == "" || testTemplate == "" {
		return fmt.Errorf("unsupported action type: %s", actionType)
	}

	// Generate action file
	actionContent, err := RenderTemplate(actionTemplate, data)
	if err != nil {
		return fmt.Errorf("failed to render action template: %w", err)
	}

	actionPath := filepath.Join(pluginDir, "src", packageName, "actions", actionFileName+"_action.py")
	if err := os.MkdirAll(filepath.Dir(actionPath), 0755); err != nil {
		return fmt.Errorf("failed to create actions directory: %w", err)
	}

	if err := os.WriteFile(actionPath, actionContent, 0644); err != nil {
		return fmt.Errorf("failed to write action file: %w", err)
	}

	// Generate test file
	testContent, err := RenderTemplate(testTemplate, data)
	if err != nil {
		return fmt.Errorf("failed to render test template: %w", err)
	}

	testPath := filepath.Join(pluginDir, "src", packageName, "test", "test_"+actionFileName+"_action.py")
	if err := os.MkdirAll(filepath.Dir(testPath), 0755); err != nil {
		return fmt.Errorf("failed to create test directory: %w", err)
	}

	if err := os.WriteFile(testPath, testContent, 0644); err != nil {
		return fmt.Errorf("failed to write test file: %w", err)
	}

	// Generate flow JSON file based on action type
	flowTemplate := getFlowTemplate(actionType)
	if flowTemplate != "" {
		flowContent, err := RenderTemplate(flowTemplate, data)
		if err != nil {
			return fmt.Errorf("failed to render flow template: %w", err)
		}

		// Determine flow file name based on action type
		flowFileName := getFlowFileName(actionType, actionFileName)
		flowPath := filepath.Join(pluginDir, "src", packageName, "flows", flowFileName)
		if err := os.MkdirAll(filepath.Dir(flowPath), 0755); err != nil {
			return fmt.Errorf("failed to create flows directory: %w", err)
		}

		if err := os.WriteFile(flowPath, flowContent, 0644); err != nil {
			return fmt.Errorf("failed to write flow file: %w", err)
		}
	}

	// Note: Actions are auto-discovered from the actions package, so no explicit
	// registration in plugin.py is needed. The updatePluginRegistration function
	// is kept for future use if thread configuration or other registration is needed.
	if err := updatePluginRegistration(pluginDir, packageName, className, actionName); err != nil {
		return fmt.Errorf("failed to update plugin registration: %w", err)
	}

	return nil
}

// readNamespaceFromPlugin reads the namespace (GroupID) from an existing plugin.py file
func readNamespaceFromPlugin(pluginPath string) (string, error) {
	file, err := os.Open(pluginPath)
	if err != nil {
		return "", fmt.Errorf("failed to open plugin.py: %w", err)
	}
	defer file.Close()

	// Look for PluginCoordinates("namespace", ...) pattern
	// Example: return PluginCoordinates("org.deltafi.my_plugin", plugin_name, plugin_metadata["Version"])
	re := regexp.MustCompile(`PluginCoordinates\s*\(\s*"([^"]+)"`)
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		matches := re.FindStringSubmatch(line)
		if len(matches) > 1 {
			return matches[1], nil
		}
	}

	if err := scanner.Err(); err != nil {
		return "", fmt.Errorf("error reading plugin.py: %w", err)
	}

	return "", fmt.Errorf("namespace not found in plugin.py")
}

// updatePluginRegistration updates plugin.py if needed
// Currently, actions are auto-discovered, so this is a placeholder for future enhancements
// such as adding thread configuration entries
func updatePluginRegistration(pluginDir, packageName, className, actionName string) error {
	pluginPath := filepath.Join(pluginDir, "src", packageName, "plugin.py")

	// Verify plugin.py exists
	if _, err := os.Stat(pluginPath); os.IsNotExist(err) {
		return fmt.Errorf("plugin.py not found at %s", pluginPath)
	}

	// Actions are auto-discovered from the actions package, so no explicit
	// registration is needed. This function is a placeholder for future
	// enhancements like thread configuration.

	return nil
}

// UpdatePlugin updates an existing Python plugin with the latest templates
func (g *PythonGenerator) UpdatePlugin(pluginName string, options *generator.UpdateOptions) error {
	if err := ValidatePluginName(pluginName); err != nil {
		return err
	}

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return fmt.Errorf("repoPath not configured in development config")
	}

	pluginDir := filepath.Join(repoPath, pluginName)

	// Check if plugin directory exists
	if _, err := os.Stat(pluginDir); os.IsNotExist(err) {
		return fmt.Errorf("plugin directory does not exist: %s", pluginDir)
	}

	// Read existing plugin.py to get namespace and other info
	packageName := PluginNameToPackageName(pluginName)
	pluginPath := filepath.Join(pluginDir, "src", packageName, "plugin.py")
	namespace, err := readNamespaceFromPlugin(pluginPath)
	if err != nil {
		// If we can't read the namespace, use default
		namespace = fmt.Sprintf("org.deltafi.%s", packageName)
	}

	// Read existing pyproject.toml to get description
	pyprojectPath := filepath.Join(pluginDir, "pyproject.toml")
	description := fmt.Sprintf("Python plugin for DeltaFi: %s", pluginName)
	if pyprojectContent, err := os.ReadFile(pyprojectPath); err == nil {
		// Try to extract description from pyproject.toml
		lines := strings.Split(string(pyprojectContent), "\n")
		for _, line := range lines {
			if strings.HasPrefix(strings.TrimSpace(line), "description = ") {
				desc := strings.TrimPrefix(strings.TrimSpace(line), "description = ")
				desc = strings.Trim(desc, "\"")
				if desc != "" {
					description = desc
				}
				break
			}
		}
	}

	// Get DeltaFi version from TUI
	deltaFiVersion := adjustVersionForSnapshot(app.GetVersion())

	// Prepare template data
	data := &TemplateData{
		PluginName:     pluginName,
		PackageName:    packageName,
		GroupID:        namespace,
		Description:    description,
		DeltaFiVersion: deltaFiVersion,
	}

	// Define files to check/update
	files := getTemplateFiles(packageName)

	// Collect file diffs
	var diffs []*generator.FileDiff
	var filesToUpdate []string

	for templatePath, outputPath := range files {
		fullOutputPath := filepath.Join(pluginDir, outputPath)

		// Read existing file
		oldContent, err := generator.ReadFileIfExists(fullOutputPath)
		if err != nil {
			return fmt.Errorf("failed to read existing file %s: %w", fullOutputPath, err)
		}

		// Generate new content from template
		newContent, err := RenderTemplate(templatePath, data)
		if err != nil {
			// Skip files that don't have templates (like HEADER which might not exist)
			continue
		}

		// Special handling for pyproject.toml: preserve version field
		if outputPath == "pyproject.toml" && oldContent != nil {
			newContent = preservePyProjectVersion(oldContent, newContent)
		}

		// Compare files
		diff := generator.CompareFiles(outputPath, oldContent, newContent)
		diffs = append(diffs, diff)

		if diff.IsModified || diff.IsNew {
			filesToUpdate = append(filesToUpdate, outputPath)
		}
	}

	// If dry run, just show what would be updated
	if options != nil && options.DryRun {
		fmt.Println("Dry run - files that would be updated:")
		for _, path := range filesToUpdate {
			fmt.Printf("  - %s\n", path)
		}
		return nil
	}

	// If no changes, we're done
	if len(filesToUpdate) == 0 {
		fmt.Println("Plugin is up to date - no changes needed")
		return nil
	}

	// Show diffs and handle conflicts
	if options != nil && options.Interactive {
		return g.handleInteractiveUpdate(pluginDir, diffs, filesToUpdate, data)
	}

	// Force mode - overwrite all files
	if options != nil && options.Force {
		return g.applyUpdates(pluginDir, diffs, data)
	}

	// Default: show diffs and ask for confirmation
	return g.handleDefaultUpdate(pluginDir, diffs, filesToUpdate, data)
}

// handleInteractiveUpdate handles interactive file updates with merge prompts
func (g *PythonGenerator) handleInteractiveUpdate(pluginDir string, diffs []*generator.FileDiff, filesToUpdate []string, data *TemplateData) error {
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
		if diff.IsIdentical || diff.IsDeleted {
			continue
		}

		if processed[diff.Path] {
			continue
		}

		fmt.Printf("\n--- %s ---\n", diff.Path)
		if diff.IsNew {
			fmt.Println("New file - will be created")
		} else {
			fmt.Println("File has been modified")
		}

		// Prompt for action
		var action string
		fmt.Print("Update this file? [y/n/d/a/q]: ")
		fmt.Scanln(&action)

		switch strings.ToLower(action) {
		case "y", "yes":
			// Apply update
			if err := g.writeFile(pluginDir, diff.Path, diff.NewContent); err != nil {
				return fmt.Errorf("failed to update %s: %w", diff.Path, err)
			}
			fmt.Printf("✓ Updated %s\n", diff.Path)
			processed[diff.Path] = true
		case "n", "no":
			fmt.Printf("⊘ Skipped %s\n", diff.Path)
			processed[diff.Path] = true
		case "d", "diff":
			fmt.Println(diff.GenerateSideBySideDiff())
			// Re-prompt after showing diff
			fmt.Print("Update this file? [y/n]: ")
			fmt.Scanln(&action)
			if strings.ToLower(action) == "y" || strings.ToLower(action) == "yes" {
				if err := g.writeFile(pluginDir, diff.Path, diff.NewContent); err != nil {
					return fmt.Errorf("failed to update %s: %w", diff.Path, err)
				}
				fmt.Printf("✓ Updated %s\n", diff.Path)
			} else {
				fmt.Printf("⊘ Skipped %s\n", diff.Path)
			}
			processed[diff.Path] = true
		case "a", "all":
			// Apply all remaining updates
			applied := 0
			for j := i; j < len(diffs); j++ {
				remainingDiff := diffs[j]
				if remainingDiff.IsIdentical || remainingDiff.IsDeleted || processed[remainingDiff.Path] {
					continue
				}
				if err := g.writeFile(pluginDir, remainingDiff.Path, remainingDiff.NewContent); err != nil {
					return fmt.Errorf("failed to update %s: %w", remainingDiff.Path, err)
				}
				fmt.Printf("✓ Updated %s\n", remainingDiff.Path)
				processed[remainingDiff.Path] = true
				applied++
			}
			if applied > 0 {
				fmt.Printf("\n✓ Successfully updated %d file(s)\n", applied)
			}
			return nil
		case "q", "quit":
			fmt.Println("Update cancelled")
			return nil
		default:
			fmt.Println("Invalid option, skipping")
			processed[diff.Path] = true
		}
	}

	return nil
}

// handleDefaultUpdate handles default update mode (show summary and ask for confirmation)
func (g *PythonGenerator) handleDefaultUpdate(pluginDir string, diffs []*generator.FileDiff, filesToUpdate []string, data *TemplateData) error {
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

	return g.applyUpdates(pluginDir, diffs, data)
}

// applyUpdates applies all updates to files
func (g *PythonGenerator) applyUpdates(pluginDir string, diffs []*generator.FileDiff, data *TemplateData) error {
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
		fmt.Printf("✓ Successfully updated %d file(s)\n", applied)
	}
	return nil
}

// preservePyProjectVersion preserves the project version field and additional dependencies from old content
// Note: deltafi dependency version is updated to match TUI version, but other dependencies are preserved
func preservePyProjectVersion(oldContent, newContent []byte) []byte {
	newContentStr := string(newContent)

	// Extract and preserve project version
	versionRe := regexp.MustCompile(`(?m)^version\s*=\s*"([^"]+)"`)
	oldMatches := versionRe.FindSubmatch(oldContent)
	if len(oldMatches) >= 2 {
		oldVersion := string(oldMatches[1])
		newContentStr = versionRe.ReplaceAllString(newContentStr, fmt.Sprintf(`version = "%s"`, oldVersion))
	}

	// Extract all dependencies from old content (excluding deltafi)
	// Use a regex that matches the dependencies array across multiple lines
	// Pattern: dependencies = [ ... ] where ] is on its own line
	dependenciesRe := regexp.MustCompile(`(?s)dependencies\s*=\s*\[(.*?)\n\s*\]`)
	oldDepsMatch := dependenciesRe.FindSubmatch(oldContent)

	if len(oldDepsMatch) >= 2 {
		oldDepsStr := string(oldDepsMatch[1])
		// Extract individual dependencies (lines that contain quoted strings)
		depLineRe := regexp.MustCompile(`(?m)^\s*"([^"]+)"`)
		var additionalDeps []string

		for _, line := range strings.Split(oldDepsStr, "\n") {
			depMatch := depLineRe.FindStringSubmatch(line)
			if len(depMatch) >= 2 {
				dep := depMatch[1]
				// Skip deltafi dependency - we'll use the TUI version for that
				if !strings.HasPrefix(dep, "deltafi==") {
					// Remove trailing comma if present, and normalize whitespace
					cleanLine := strings.TrimSpace(line)
					cleanLine = strings.TrimSuffix(cleanLine, ",")
					cleanLine = strings.TrimSpace(cleanLine)
					additionalDeps = append(additionalDeps, cleanLine)
				}
			}
		}

		// If there are additional dependencies, merge them with the deltafi dependency
		if len(additionalDeps) > 0 {
			// Find the dependencies section in new content
			newDepsMatch := dependenciesRe.FindSubmatch([]byte(newContentStr))
			if len(newDepsMatch) >= 2 {
				// Extract the deltafi dependency from new content (it's already rendered with TUI version)
				newDepsStr := string(newDepsMatch[1])
				var deltafiDep string
				for _, line := range strings.Split(newDepsStr, "\n") {
					depMatch := depLineRe.FindStringSubmatch(line)
					if len(depMatch) >= 2 && strings.HasPrefix(depMatch[1], "deltafi==") {
						deltafiDep = strings.TrimSpace(line)
						break
					}
				}

				// Only merge if we found the deltafi dependency
				if deltafiDep != "" {
					// Remove trailing comma from deltafi dependency if present
					deltafiDep = strings.TrimSuffix(strings.TrimSpace(deltafiDep), ",")
					deltafiDep = strings.TrimSpace(deltafiDep)

					// Build new dependencies list: deltafi first, then additional deps
					newDeps := []string{deltafiDep}
					newDeps = append(newDeps, additionalDeps...)

					// Format each dependency with proper indentation and comma
					formattedDeps := make([]string, len(newDeps))
					for i, dep := range newDeps {
						formattedDeps[i] = "  " + dep
					}

					// Replace the dependencies section (including the closing bracket)
					// Add commas between items, and a trailing comma before the closing bracket
					newDepsBlock := "[\n" + strings.Join(formattedDeps, ",\n") + ",\n]"
					newContentStr = dependenciesRe.ReplaceAllString(newContentStr, "dependencies = "+newDepsBlock)
				}
			}
		}
	}

	return []byte(newContentStr)
}

// adjustVersionForSnapshot adjusts a SNAPSHOT version to use the previous patch version
// For example: 2.34.5-SNAPSHOT -> 2.34.4, 2.34.0-SNAPSHOT -> 2.34.0
func adjustVersionForSnapshot(version string) string {
	if version == "" {
		return "0.0.0" // Fallback if version not set
	}

	// Check if version is a SNAPSHOT
	if !strings.Contains(version, "-SNAPSHOT") {
		return version
	}

	// Extract the version part before -SNAPSHOT
	versionPart := strings.Split(version, "-SNAPSHOT")[0]

	// Parse major.minor.patch
	versionRe := regexp.MustCompile(`^(\d+)\.(\d+)\.(\d+)$`)
	matches := versionRe.FindStringSubmatch(versionPart)
	if len(matches) != 4 {
		// If we can't parse it, return as-is
		return versionPart
	}

	// Extract version components
	major := matches[1]
	minor := matches[2]
	patch := matches[3]

	// Decrement patch version (but not below 0)
	// We'll parse as int, decrement, and convert back
	patchInt, err := strconv.Atoi(patch)
	if err != nil {
		// If we can't parse the patch version, return as-is
		return versionPart
	}
	if patchInt > 0 {
		patchInt--
	}
	// patchInt stays 0 if it was already 0

	// Reconstruct version
	return fmt.Sprintf("%s.%s.%d", major, minor, patchInt)
}

// writeFile writes content to a file, creating directories as needed
func (g *PythonGenerator) writeFile(pluginDir, relativePath string, content []byte) error {
	fullPath := filepath.Join(pluginDir, relativePath)
	if err := os.MkdirAll(filepath.Dir(fullPath), 0755); err != nil {
		return fmt.Errorf("failed to create directory: %w", err)
	}
	return os.WriteFile(fullPath, content, 0644)
}

// normalizeActionType normalizes action type names
func normalizeActionType(actionType string) string {
	actionType = strings.ToLower(actionType)
	switch actionType {
	case "transform":
		return "TransformAction"
	case "egress":
		return "EgressAction"
	case "ingress":
		return "IngressAction"
	case "timedingress":
		return "TimedIngressAction"
	case "joiningtransform":
		return "JoiningTransformAction"
	case "transformmany":
		return "TransformManyAction"
	case "transformexec":
		return "TransformExecAction"
	default:
		// If it already ends with "action", capitalize and add "Action"
		if strings.HasSuffix(actionType, "action") {
			base := actionType[:len(actionType)-6]
			if len(base) > 0 {
				return strings.ToUpper(base[:1]) + base[1:] + "Action"
			}
			return "Action"
		}
		// Return as-is, assuming it's already in the correct format
		return actionType
	}
}

// getActionTemplate returns the template path for an action type
func getActionTemplate(actionType string) string {
	actionType = strings.ToLower(actionType)
	switch actionType {
	case "transformaction", "transform":
		return "actions/transform_action.py.tmpl"
	case "egressaction", "egress":
		return "actions/egress_action.py.tmpl"
	case "ingressaction", "ingress":
		return "actions/timed_ingress_action.py.tmpl"
	case "timedingressaction", "timedingress":
		return "actions/timed_ingress_action.py.tmpl"
	case "joiningtransformaction", "joiningtransform":
		return "actions/joining_transform_action.py.tmpl"
	case "transformmanyaction", "transformmany":
		return "actions/transform_many_action.py.tmpl"
	case "transformexecaction", "transformexec":
		return "actions/transform_exec_action.py.tmpl"
	default:
		return ""
	}
}

// getTestTemplate returns the template path for a test file based on action type
func getTestTemplate(actionType string) string {
	actionType = strings.ToLower(actionType)
	switch actionType {
	case "transformaction", "transform":
		return "test/transform_action_test.py.tmpl"
	case "egressaction", "egress":
		return "test/egress_action_test.py.tmpl"
	case "ingressaction", "ingress":
		return "test/timed_ingress_action_test.py.tmpl"
	case "timedingressaction", "timedingress":
		return "test/timed_ingress_action_test.py.tmpl"
	case "joiningtransformaction", "joiningtransform":
		return "test/joining_transform_action_test.py.tmpl"
	case "transformmanyaction", "transformmany":
		return "test/transform_many_action_test.py.tmpl"
	case "transformexecaction", "transformexec":
		return "test/transform_exec_action_test.py.tmpl"
	default:
		return ""
	}
}

// getFlowTemplate returns the template path for a flow JSON file based on action type
func getFlowTemplate(actionType string) string {
	actionType = strings.ToLower(actionType)
	switch actionType {
	case "transformaction", "transform":
		return "flows/transform_flow.json.tmpl"
	case "egressaction", "egress":
		return "flows/egress_flow.json.tmpl"
	case "ingressaction", "ingress":
		return "flows/ingress_flow.json.tmpl"
	case "timedingressaction", "timedingress":
		return "flows/ingress_flow.json.tmpl"
	case "joiningtransformaction", "joiningtransform":
		return "flows/transform_flow.json.tmpl"
	case "transformmanyaction", "transformmany":
		return "flows/transform_flow.json.tmpl"
	case "transformexecaction", "transformexec":
		return "flows/transform_flow.json.tmpl"
	default:
		return ""
	}
}

// getFlowFileName returns the flow JSON file name based on action type and action file name
func getFlowFileName(actionType string, actionFileName string) string {
	actionType = strings.ToLower(actionType)
	switch actionType {
	case "transformaction", "transform", "joiningtransformaction", "joiningtransform", "transformmanyaction", "transformmany", "transformexecaction", "transformexec":
		return actionFileName + ".json"
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

   Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>

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
