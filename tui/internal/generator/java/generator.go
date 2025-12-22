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
package java

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/generator"
	"github.com/deltafi/tui/internal/types"
)

// JavaGenerator implements the Generator interface for Java plugins
type JavaGenerator struct{}

// NewJavaGenerator creates a new Java generator
func NewJavaGenerator() generator.Generator {
	return &JavaGenerator{}
}

// getTemplateFiles returns the map of template files to output paths for a given groupID path and class name
func getTemplateFiles(groupIDPath, className string) map[string]string {
	return map[string]string{
		"build.gradle.tmpl":                             "build.gradle",
		"settings.gradle.tmpl":                          "settings.gradle",
		"gradle.properties.tmpl":                        "gradle.properties",
		".gitignore.tmpl":                               ".gitignore",
		"HEADER.tmpl":                                   "HEADER",
		"LICENSE.tmpl":                                  "LICENSE",
		"README.md.tmpl":                                "README.md",
		"application.yaml.tmpl":                         filepath.Join("src", "main", "resources", "application.yaml"),
		"src/main/java/PluginClass.java.tmpl":           filepath.Join("src", "main", "java", groupIDPath, className+".java"),
		"gradlew.tmpl":                                  "gradlew",
		"gradlew.bat.tmpl":                              "gradlew.bat",
		"gradle/wrapper/gradle-wrapper.properties.tmpl": filepath.Join("gradle", "wrapper", "gradle-wrapper.properties"),
		"gradle/wrapper/gradle-wrapper.jar":             filepath.Join("gradle", "wrapper", "gradle-wrapper.jar"),
		"flows/variables.json.tmpl":                     filepath.Join("src", "main", "resources", "flows", "variables.json"),
		"flows/data_source.json.tmpl":                   filepath.Join("src", "main", "resources", "flows", "data-source.json"),
	}
}

// isBinaryFile returns true if the template path is a binary file that should be copied without template processing
func isBinaryFile(templatePath string) bool {
	return strings.HasSuffix(templatePath, ".jar")
}

// GeneratePlugin generates a new Java plugin project
func (g *JavaGenerator) GeneratePlugin(pluginName string, options *generator.PluginOptions) error {
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
	description := fmt.Sprintf("Java plugin for DeltaFi: %s", pluginName)
	if options != nil && options.Description != "" {
		description = options.Description
	}

	// Get DeltaFi version from TUI
	deltaFiVersion := getDeltaFiVersion()

	className := PluginNameToClassName(pluginName)

	// Convert GroupID to file path (e.g., "org.deltafi.awesome.java" -> "org/deltafi/awesome/java")
	groupIDPath := strings.ReplaceAll(groupID, ".", "/")

	data := &TemplateData{
		PluginName:     pluginName,
		PackageName:    packageName,
		ClassName:      className,
		GroupID:        groupID,
		Description:    description,
		DeltaFiVersion: deltaFiVersion,
	}

	// Define files to generate
	files := getTemplateFiles(groupIDPath, className)

	// Create directories
	if err := os.MkdirAll(pluginDir, 0755); err != nil {
		return fmt.Errorf("failed to create plugin directory: %w", err)
	}

	// Generate files
	for templatePath, outputPath := range files {
		var content []byte
		var err error

		if isBinaryFile(templatePath) {
			// Binary files are copied directly without template processing
			content, err = ReadRawFile(templatePath)
		} else {
			content, err = RenderTemplate(templatePath, data)
		}

		if err != nil {
			return fmt.Errorf("failed to process %s: %w", templatePath, err)
		}

		fullPath := filepath.Join(pluginDir, outputPath)
		if err := os.MkdirAll(filepath.Dir(fullPath), 0755); err != nil {
			return fmt.Errorf("failed to create directory for %s: %w", outputPath, err)
		}

		// Set executable permissions for gradlew scripts
		perm := os.FileMode(0644)
		if outputPath == "gradlew" || outputPath == "gradlew.bat" {
			perm = 0755
		}

		if err := os.WriteFile(fullPath, content, perm); err != nil {
			return fmt.Errorf("failed to write file %s: %w", outputPath, err)
		}
	}

	return nil
}

// GenerateAction generates a new action in an existing plugin
func (g *JavaGenerator) GenerateAction(pluginName string, actionName string, actionType string) error {
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
	buildGradlePath := filepath.Join(pluginDir, "build.gradle")
	groupID, err := readGroupIDFromBuildGradle(buildGradlePath)
	if err != nil {
		// If we can't read the group ID, use default
		groupID = fmt.Sprintf("org.deltafi.%s", packageName)
	}
	// Convert GroupID to file path (e.g., "org.deltafi.awesome.java" -> "org/deltafi/awesome/java")
	groupIDPath := strings.ReplaceAll(groupID, ".", "/")

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

	// Avoid conflicts with base action class names from actionkit
	baseActionClassNames := map[string]bool{
		"TransformAction":        true,
		"EgressAction":           true,
		"TimedIngressAction":     true,
		"IngressAction":          true,
		"JoiningTransformAction": true,
		"TransformManyAction":    true,
	}
	if baseActionClassNames[className] {
		// Append "Custom" to avoid conflict with base class
		className = className + "Custom"
	}

	actionFileName := ActionNameToFileName(baseActionName)

	// Generate test class name
	testClassName := className + "Test"

	// Normalize action type
	actionType = normalizeActionType(actionType)

	// Prepare template data
	data := &TemplateData{
		PluginName:      pluginName,
		PackageName:     packageName,
		ClassName:       className,
		TestClassName:   testClassName,
		ActionName:      baseActionName, // Use base name for metadata
		ActionClassName: className,
		ActionFileName:  actionFileName,
		ActionType:      actionType,
		GroupID:         groupID,
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

	actionPath := filepath.Join(pluginDir, "src", "main", "java", groupIDPath, "actions", className+".java")
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

	testPath := filepath.Join(pluginDir, "src", "test", "java", groupIDPath, "actions", testClassName+".java")
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
		// Use base action name (kebab-case) for flow file names
		flowFileName := getFlowFileName(actionType, baseActionName)
		flowPath := filepath.Join(pluginDir, "src", "main", "resources", "flows", flowFileName)
		if err := os.MkdirAll(filepath.Dir(flowPath), 0755); err != nil {
			return fmt.Errorf("failed to create flows directory: %w", err)
		}

		if err := os.WriteFile(flowPath, flowContent, 0644); err != nil {
			return fmt.Errorf("failed to write flow file: %w", err)
		}
	}

	return nil
}

// readGroupIDFromBuildGradle reads the group ID from build.gradle
func readGroupIDFromBuildGradle(buildGradlePath string) (string, error) {
	file, err := os.Open(buildGradlePath)
	if err != nil {
		return "", fmt.Errorf("failed to open build.gradle: %w", err)
	}
	defer file.Close()

	// Look for "group 'org.deltafi.helloworld'" pattern
	re := regexp.MustCompile(`group\s+['"]([^'"]+)['"]`)
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		matches := re.FindStringSubmatch(line)
		if len(matches) > 1 {
			return matches[1], nil
		}
	}

	if err := scanner.Err(); err != nil {
		return "", fmt.Errorf("error reading build.gradle: %w", err)
	}

	return "", fmt.Errorf("group ID not found in build.gradle")
}

// UpdatePlugin updates an existing Java plugin with the latest templates
func (g *JavaGenerator) UpdatePlugin(pluginName string, options *generator.UpdateOptions) error {
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

	// Read existing build.gradle to get group ID and description
	packageName := PluginNameToPackageName(pluginName)
	buildGradlePath := filepath.Join(pluginDir, "build.gradle")
	groupID, err := readGroupIDFromBuildGradle(buildGradlePath)
	if err != nil {
		// If we can't read the group ID, use default
		groupID = fmt.Sprintf("org.deltafi.%s", packageName)
	}

	// Read description from build.gradle
	description := fmt.Sprintf("Java plugin for DeltaFi: %s", pluginName)
	if buildGradleContent, err := os.ReadFile(buildGradlePath); err == nil {
		// Try to extract description from build.gradle
		lines := strings.Split(string(buildGradleContent), "\n")
		for _, line := range lines {
			if strings.Contains(line, "pluginDescription") {
				// Look for: ext.pluginDescription = '...'
				re := regexp.MustCompile(`pluginDescription\s*=\s*['"]([^'"]+)['"]`)
				matches := re.FindStringSubmatch(line)
				if len(matches) > 1 {
					description = matches[1]
					break
				}
			}
		}
	}

	// Get DeltaFi version from TUI
	deltaFiVersion := getDeltaFiVersion()

	className := PluginNameToClassName(pluginName)

	// Convert GroupID to file path (e.g., "org.deltafi.awesome.java" -> "org/deltafi/awesome/java")
	groupIDPath := strings.ReplaceAll(groupID, ".", "/")

	// Prepare template data
	data := &TemplateData{
		PluginName:     pluginName,
		PackageName:    packageName,
		ClassName:      className,
		GroupID:        groupID,
		Description:    description,
		DeltaFiVersion: deltaFiVersion,
	}

	// Define files to check/update
	files := getTemplateFiles(groupIDPath, className)

	// Collect file diffs
	var diffs []*generator.FileDiff
	var filesToUpdate []string

	for templatePath, outputPath := range files {
		fullOutputPath := filepath.Join(pluginDir, outputPath)

		// Skip gradle-wrapper.jar as it's a binary file
		if strings.Contains(templatePath, "gradle-wrapper.jar") {
			continue
		}

		// Read existing file
		oldContent, err := generator.ReadFileIfExists(fullOutputPath)
		if err != nil {
			return fmt.Errorf("failed to read existing file %s: %w", fullOutputPath, err)
		}

		// Generate new content from template
		newContent, err := RenderTemplate(templatePath, data)
		if err != nil {
			// Skip files that don't have templates
			continue
		}

		// Special handling for gradle.properties: preserve version field
		if outputPath == "gradle.properties" && oldContent != nil {
			newContent = preserveGradlePropertiesVersion(oldContent, newContent)
		}

		// Special handling for build.gradle: preserve additional dependencies
		if outputPath == "build.gradle" && oldContent != nil {
			newContent = preserveBuildGradleDependencies(oldContent, newContent)
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
func (g *JavaGenerator) handleInteractiveUpdate(pluginDir string, diffs []*generator.FileDiff, filesToUpdate []string, data *TemplateData) error {
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
func (g *JavaGenerator) handleDefaultUpdate(pluginDir string, diffs []*generator.FileDiff, filesToUpdate []string, data *TemplateData) error {
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
func (g *JavaGenerator) applyUpdates(pluginDir string, diffs []*generator.FileDiff, data *TemplateData) error {
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

// preserveGradlePropertiesVersion preserves the deltafiVersion field from old content
func preserveGradlePropertiesVersion(oldContent, newContent []byte) []byte {
	newContentStr := string(newContent)

	// Extract deltafiVersion from old content
	versionRe := regexp.MustCompile(`(?m)^deltafiVersion\s*=\s*([^\s]+)`)
	oldMatches := versionRe.FindSubmatch(oldContent)
	if len(oldMatches) >= 2 {
		oldVersion := string(oldMatches[1])
		newContentStr = versionRe.ReplaceAllString(newContentStr, fmt.Sprintf("deltafiVersion=%s", oldVersion))
	}

	return []byte(newContentStr)
}

// preserveBuildGradleDependencies preserves additional dependencies from old content
// Note: deltafi-actionkit is provided by the plugin convention, so we only preserve user-added dependencies
func preserveBuildGradleDependencies(oldContent, newContent []byte) []byte {
	newContentStr := string(newContent)

	// Extract dependencies block from old content
	// Pattern: dependencies { ... }
	dependenciesRe := regexp.MustCompile(`(?s)dependencies\s*\{([^}]*)\}`)
	oldDepsMatch := dependenciesRe.FindSubmatch(oldContent)

	if len(oldDepsMatch) >= 2 {
		oldDepsStr := string(oldDepsMatch[1])
		// Extract individual dependencies (lines that contain implementation, api, etc.)
		depLineRe := regexp.MustCompile(`(?m)^\s*(implementation|api|testImplementation|testRuntimeOnly)\s+['"]([^'"]+)['"]`)
		var additionalDeps []string

		for _, line := range strings.Split(oldDepsStr, "\n") {
			depMatch := depLineRe.FindStringSubmatch(line)
			if len(depMatch) >= 3 {
				dep := depMatch[2]
				// Skip deltafi-actionkit dependency - it's provided by the plugin convention
				if !strings.Contains(dep, "deltafi-actionkit") {
					additionalDeps = append(additionalDeps, strings.TrimSpace(line))
				}
			}
		}

		// If there are additional dependencies, add them to the new content
		if len(additionalDeps) > 0 {
			// Find the dependencies section in new content
			newDepsMatch := dependenciesRe.FindSubmatch([]byte(newContentStr))
			if len(newDepsMatch) >= 2 {
				// Format dependencies block with additional deps
				depsBlock := "dependencies {\n"
				for _, dep := range additionalDeps {
					depsBlock += "    " + dep + "\n"
				}
				depsBlock += "}"

				// Replace the dependencies section
				newContentStr = dependenciesRe.ReplaceAllString(newContentStr, depsBlock)
			}
		}
	}

	return []byte(newContentStr)
}

// getDeltaFiVersion returns the DeltaFi version to use for plugin dependencies.
// In CoreDevelopment mode, uses the TUI version (what `deltafi up` will install).
// In PluginDevelopment mode, queries the running DeltaFi system for its version.
func getDeltaFiVersion() string {
	config := app.GetInstance().GetConfig()

	// In plugin development mode, use the version of the running DeltaFi system
	// since that's what the plugin will be built against
	if config.DeploymentMode == types.PluginDevelopment {
		client, err := app.GetInstance().GetAPIClient()
		if err == nil {
			status, err := client.Status()
			if err == nil && status.Status.Version != "" {
				return status.Status.Version
			}
		}
	}

	// In core development mode (or if we can't reach the API), use the TUI version
	version := app.GetVersion()
	if version == "" {
		return "0.0.0"
	}
	return version
}

// writeFile writes content to a file, creating directories as needed
func (g *JavaGenerator) writeFile(pluginDir, relativePath string, content []byte) error {
	fullPath := filepath.Join(pluginDir, relativePath)
	if err := os.MkdirAll(filepath.Dir(fullPath), 0755); err != nil {
		return fmt.Errorf("failed to create directory: %w", err)
	}

	// Set executable permissions for gradlew scripts
	perm := os.FileMode(0644)
	if relativePath == "gradlew" || relativePath == "gradlew.bat" {
		perm = 0755
	}

	return os.WriteFile(fullPath, content, perm)
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
		return "TimedIngressAction"
	case "timedingress":
		return "TimedIngressAction"
	case "joiningtransform":
		return "JoiningTransformAction"
	case "transformmany":
		return "TransformManyAction"
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
		return "src/main/java/actions/TransformAction.java.tmpl"
	case "egressaction", "egress":
		return "src/main/java/actions/EgressAction.java.tmpl"
	case "timedingressaction", "timedingress", "ingressaction", "ingress":
		return "src/main/java/actions/TimedIngressAction.java.tmpl"
	case "joiningtransformaction", "joiningtransform":
		return "src/main/java/actions/JoiningTransformAction.java.tmpl"
	case "transformmanyaction", "transformmany":
		return "src/main/java/actions/TransformManyAction.java.tmpl"
	default:
		return ""
	}
}

// getTestTemplate returns the template path for a test file based on action type
func getTestTemplate(actionType string) string {
	actionType = strings.ToLower(actionType)
	switch actionType {
	case "transformaction", "transform":
		return "src/test/java/actions/TransformActionTest.java.tmpl"
	case "egressaction", "egress":
		return "src/test/java/actions/EgressActionTest.java.tmpl"
	case "timedingressaction", "timedingress", "ingressaction", "ingress":
		return "src/test/java/actions/TimedIngressActionTest.java.tmpl"
	case "joiningtransformaction", "joiningtransform":
		return "src/test/java/actions/JoiningTransformActionTest.java.tmpl"
	case "transformmanyaction", "transformmany":
		return "src/test/java/actions/TransformManyActionTest.java.tmpl"
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
	case "timedingressaction", "timedingress", "ingressaction", "ingress":
		return "flows/ingress_flow.json.tmpl"
	case "joiningtransformaction", "joiningtransform":
		return "flows/transform_flow.json.tmpl"
	case "transformmanyaction", "transformmany":
		return "flows/transform_flow.json.tmpl"
	default:
		return ""
	}
}

// getFlowFileName returns the flow JSON file name based on action type and action file name
func getFlowFileName(actionType string, actionFileName string) string {
	actionType = strings.ToLower(actionType)
	switch actionType {
	case "transformaction", "transform", "joiningtransformaction", "joiningtransform", "transformmanyaction", "transformmany":
		return actionFileName + ".json"
	case "egressaction", "egress":
		return actionFileName + "-data-sink.json"
	case "timedingressaction", "timedingress", "ingressaction", "ingress":
		return actionFileName + "-data-source.json"
	default:
		return actionFileName + ".json"
	}
}
