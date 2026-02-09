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
package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/generator"
	"github.com/deltafi/tui/internal/generator/java"
	"github.com/deltafi/tui/internal/generator/python"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var pluginGenerateCmd = &cobra.Command{
	Use:   "generate [--java | --python] <plugin-name>",
	Short: "Generate a new plugin",
	Long:  "Generate a new plugin project. Use --java for Java plugins or --python for Python plugins.",
	Args:  cobra.ExactArgs(1),
	RunE:  runPluginGenerate,
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		return getPluginDirectoryNames(toComplete)
	},
}

var pluginGenerateActionCmd = &cobra.Command{
	Use:   "action <plugin-name> <action-name>",
	Short: "Generate a new action in a plugin",
	Long:  "Generate a new action in an existing plugin. Language is auto-detected based on build.gradle (Java) or pyproject.toml (Python).",
	Args:  cobra.ExactArgs(2),
	RunE:  runPluginGenerateAction,
	ValidArgsFunction: func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		// First argument: plugin name
		if len(args) == 0 {
			return getPluginDirectoryNames(toComplete)
		}
		// Second argument: action name (no completion)
		return nil, cobra.ShellCompDirectiveNoFileComp
	},
}

func init() {
	pluginCmd.AddCommand(pluginGenerateCmd)
	pluginGenerateCmd.AddCommand(pluginGenerateActionCmd)

	// Flags for plugin generation
	pluginGenerateCmd.Flags().Bool("java", false, "Generate a Java plugin")
	pluginGenerateCmd.Flags().Bool("python", false, "Generate a Python plugin")
	pluginGenerateCmd.MarkFlagsOneRequired("java", "python")
	pluginGenerateCmd.MarkFlagsMutuallyExclusive("java", "python")
	pluginGenerateCmd.Flags().StringP("namespace", "n", "", "Plugin namespace (default: org.deltafi.<plugin-name>)")
	pluginGenerateCmd.Flags().BoolP("interactive", "i", false, "Interactive mode - prompt for each file (update only)")
	pluginGenerateCmd.Flags().BoolP("force", "f", false, "Force overwrite all files without prompting (update only)")
	pluginGenerateCmd.Flags().BoolP("dry-run", "d", false, "Dry run - show what would be updated without making changes (update only)")

	// Flags for action generation
	pluginGenerateActionCmd.Flags().StringP("type", "t", "TransformAction", "Type of action to generate")
}

// detectPluginLanguage detects the plugin language by checking for build.gradle (Java) or pyproject.toml (Python)
func detectPluginLanguage(pluginDir string) (string, error) {
	buildGradlePath := filepath.Join(pluginDir, "build.gradle")
	pyprojectPath := filepath.Join(pluginDir, "pyproject.toml")

	hasBuildGradle := false
	hasPyproject := false

	if _, err := os.Stat(buildGradlePath); err == nil {
		hasBuildGradle = true
	}

	if _, err := os.Stat(pyprojectPath); err == nil {
		hasPyproject = true
	}

	if hasBuildGradle && hasPyproject {
		return "", fmt.Errorf("plugin directory contains both build.gradle and pyproject.toml - cannot determine language")
	}

	if hasBuildGradle {
		return "java", nil
	}

	if hasPyproject {
		return "python", nil
	}

	return "", fmt.Errorf("plugin directory does not contain build.gradle or pyproject.toml - cannot determine language")
}

func runPluginGenerate(cmd *cobra.Command, args []string) error {
	javaFlag, _ := cmd.Flags().GetBool("java")
	pythonFlag, _ := cmd.Flags().GetBool("python")

	if !javaFlag && !pythonFlag {
		return fmt.Errorf("must specify either --java or --python flag")
	}

	if javaFlag {
		return runPluginGenerateJava(cmd, args)
	}

	return runPluginGeneratePython(cmd, args)
}

func runPluginGenerateAction(cmd *cobra.Command, args []string) error {
	pluginName := args[0]
	actionName := args[1]

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

	// Detect language
	language, err := detectPluginLanguage(pluginDir)
	if err != nil {
		return fmt.Errorf("failed to detect plugin language: %w", err)
	}

	actionType, _ := cmd.Flags().GetString("type")

	if language == "java" {
		gen := java.NewJavaGenerator()
		if err := gen.GenerateAction(pluginName, actionName, actionType); err != nil {
			return fmt.Errorf("error generating action: %w", err)
		}
	} else if language == "python" {
		gen := python.NewPythonGenerator()
		if err := gen.GenerateAction(pluginName, actionName, actionType); err != nil {
			return fmt.Errorf("error generating action: %w", err)
		}
	} else {
		return fmt.Errorf("unsupported language: %s", language)
	}

	fmt.Println(styles.SuccessStyle.Render("Action generated successfully"))
	fmt.Printf("Action '%s' added to plugin '%s'\n", actionName, pluginName)

	return nil
}

func runPluginGenerateJava(cmd *cobra.Command, args []string) error {
	pluginName := args[0]

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return fmt.Errorf("repoPath not configured in development config")
	}

	pluginDir := filepath.Join(repoPath, pluginName)

	// Check if plugin directory already exists
	if _, err := os.Stat(pluginDir); err == nil {
		// Directory exists - run update logic
		return runPluginGenerateJavaUpdate(cmd, args)
	}

	// Directory doesn't exist - run generate logic
	// Get namespace flag, default to org.deltafi.<package_name>
	namespace, _ := cmd.Flags().GetString("namespace")
	if namespace == "" {
		packageName := java.PluginNameToPackageName(pluginName)
		namespace = fmt.Sprintf("org.deltafi.%s", packageName)
	}

	gen := java.NewJavaGenerator()
	options := &generator.PluginOptions{
		GroupID:     namespace,
		Description: fmt.Sprintf("Java plugin for DeltaFi: %s", pluginName),
	}

	if err := gen.GeneratePlugin(pluginName, options); err != nil {
		return fmt.Errorf("error generating Java plugin: %w", err)
	}

	pluginPath := filepath.Join(config.Development.RepoPath, pluginName)

	fmt.Println(styles.SuccessStyle.Render("Java plugin generated successfully"))
	fmt.Printf("Plugin location: %s\n", pluginPath)
	fmt.Printf("Namespace: %s\n", namespace)

	return nil
}

func runPluginGenerateJavaUpdate(cmd *cobra.Command, args []string) error {
	pluginName := args[0]
	interactive, _ := cmd.Flags().GetBool("interactive")
	force, _ := cmd.Flags().GetBool("force")
	dryRun, _ := cmd.Flags().GetBool("dry-run")

	gen := java.NewJavaGenerator()
	options := &generator.UpdateOptions{
		Interactive: interactive,
		Force:       force,
		DryRun:      dryRun,
	}

	if err := gen.UpdatePlugin(pluginName, options); err != nil {
		return fmt.Errorf("error updating plugin: %w", err)
	}

	return nil
}

func runPluginGeneratePython(cmd *cobra.Command, args []string) error {
	pluginName := args[0]

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return fmt.Errorf("repoPath not configured in development config")
	}

	pluginDir := filepath.Join(repoPath, pluginName)

	// Check if plugin directory already exists
	if _, err := os.Stat(pluginDir); err == nil {
		// Directory exists - run update logic
		return runPluginGeneratePythonUpdate(cmd, args)
	}

	// Directory doesn't exist - run generate logic
	// Get namespace flag, default to org.deltafi.<snake_case_plugin_name>
	namespace, _ := cmd.Flags().GetString("namespace")
	if namespace == "" {
		packageName := python.PluginNameToPackageName(pluginName)
		namespace = fmt.Sprintf("org.deltafi.%s", packageName)
	}

	gen := python.NewPythonGenerator()
	options := &generator.PluginOptions{
		GroupID:     namespace,
		Description: fmt.Sprintf("Python plugin for DeltaFi: %s", pluginName),
	}

	if err := gen.GeneratePlugin(pluginName, options); err != nil {
		return fmt.Errorf("error generating Python plugin: %w", err)
	}

	pluginPath := filepath.Join(config.Development.RepoPath, pluginName)

	fmt.Println(styles.SuccessStyle.Render("Python plugin generated successfully"))
	fmt.Printf("Plugin location: %s\n", pluginPath)
	fmt.Printf("Namespace: %s\n", namespace)

	return nil
}

func runPluginGeneratePythonUpdate(cmd *cobra.Command, args []string) error {
	pluginName := args[0]
	interactive, _ := cmd.Flags().GetBool("interactive")
	force, _ := cmd.Flags().GetBool("force")
	dryRun, _ := cmd.Flags().GetBool("dry-run")

	gen := python.NewPythonGenerator()
	options := &generator.UpdateOptions{
		Interactive: interactive,
		Force:       force,
		DryRun:      dryRun,
	}

	if err := gen.UpdatePlugin(pluginName, options); err != nil {
		return fmt.Errorf("error updating plugin: %w", err)
	}

	return nil
}

// getPluginDirectoryNames returns a list of directory names in the repos directory for tab completion
func getPluginDirectoryNames(toComplete string) ([]string, cobra.ShellCompDirective) {
	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return nil, cobra.ShellCompDirectiveError
	}

	entries, err := os.ReadDir(repoPath)
	if err != nil {
		return nil, cobra.ShellCompDirectiveError
	}

	var dirNames []string
	for _, entry := range entries {
		if entry.IsDir() {
			name := entry.Name()
			// Filter by toComplete prefix if provided
			if toComplete == "" || strings.HasPrefix(name, toComplete) {
				dirNames = append(dirNames, name)
			}
		}
	}

	sort.Strings(dirNames)
	return escapedCompletions(dirNames, toComplete)
}
