// ABOUTME: CLI commands for generating plugin projects, actions, and suppliers.
// ABOUTME: Supports Java, Python, and Go plugins with language auto-detection.

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
	"os/exec"
	"path/filepath"
	"sort"
	"strings"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/generator"
	goGen "github.com/deltafi/tui/internal/generator/golang"
	"github.com/deltafi/tui/internal/generator/java"
	"github.com/deltafi/tui/internal/generator/python"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

var pluginGenerateCmd = &cobra.Command{
	Use:     "generate [--java | --python | --go] --name <plugin-name>",
	Aliases: []string{"g"},
	Short:   "Generate a new plugin",
	Long:    "Generate a new plugin project. Use --java for Java, --python for Python, or --go for Go plugins.",
	Args:    cobra.NoArgs,
	RunE:    runPluginGenerate,
}

var pluginGenerateActionCmd = &cobra.Command{
	Use:   "action <action-name>",
	Short: "Generate a new action in a plugin",
	Long: "Generate a new action in an existing plugin. Language is auto-detected based on " +
		"build.gradle (Java), pyproject.toml (Python), or go.mod (Go). " +
		"Runs in the current directory by default, or pass --in-repo to target a plugin in " +
		"the configured repos directory.\n\n" +
		"If the action source file already exists, only the test file is generated " +
		"(useful for adding tests to manually-written actions).",
	Args: cobra.ExactArgs(1),
	RunE: runPluginGenerateAction,
}

var pluginGenerateSupplierCmd = &cobra.Command{
	Use:   "supplier <supplier-name>",
	Short: "Generate a new lookup table supplier in a Go plugin",
	Long: "Generate a new lookup table supplier in an existing Go plugin. " +
		"Runs in the current directory by default, or pass --in-repo to target a plugin in " +
		"the configured repos directory.",
	Args: cobra.ExactArgs(1),
	RunE: runPluginGenerateSupplier,
}

var pluginGenerateUpdateCmd = &cobra.Command{
	Use:   "update",
	Short: "Update an existing plugin to match the latest templates",
	Long: "Update an existing plugin's scaffolding (build files, Dockerfiles, Makefile, " +
		"flow files, README, etc.) to match the current generator templates. Runs in the " +
		"current directory by default, or pass --in-repo to target a plugin in the " +
		"configured repos directory.\n\n" +
		"By default, user-owned source files (Go main.go, Java PluginClass.java, " +
		"Python plugin.py) are NOT touched. Pass --include-source to refresh them too.\n\n" +
		"Existing go.mod dependencies and dynamic action/supplier blank imports in " +
		"Go main.go are preserved.",
	Args: cobra.NoArgs,
	RunE: runPluginGenerateUpdate,
}

func init() {
	pluginCmd.AddCommand(pluginGenerateCmd)
	pluginGenerateCmd.AddCommand(pluginGenerateActionCmd)
	pluginGenerateCmd.AddCommand(pluginGenerateSupplierCmd)
	pluginGenerateCmd.AddCommand(pluginGenerateUpdateCmd)

	// Flags for plugin generation
	pluginGenerateCmd.Flags().StringP("name", "N", "", "Plugin name (required)")
	if err := pluginGenerateCmd.MarkFlagRequired("name"); err != nil {
		panic(err)
	}
	pluginGenerateCmd.Flags().Bool("java", false, "Generate a Java plugin")
	pluginGenerateCmd.Flags().Bool("python", false, "Generate a Python plugin")
	pluginGenerateCmd.Flags().Bool("go", false, "Generate a Go plugin")
	pluginGenerateCmd.MarkFlagsOneRequired("java", "python", "go")
	pluginGenerateCmd.MarkFlagsMutuallyExclusive("java", "python", "go")
	pluginGenerateCmd.Flags().StringP("namespace", "n", "", "Plugin namespace (default: org.deltafi.<plugin-name> for Java/Python, org.deltafi for Go)")
	pluginGenerateCmd.Flags().StringP("module", "m", "", "Go module path (required for Go plugins, e.g., github.com/org/my-plugin)")
	pluginGenerateCmd.Flags().Bool("no-git", false, "Skip automatic `git init` in the new plugin directory")

	// Flags for action generation
	pluginGenerateActionCmd.Flags().StringP("type", "t", "TransformAction", "Type of action to generate")
	pluginGenerateActionCmd.Flags().String("in-repo", "", "Plugin name in the configured repos directory (default: use current directory)")

	// Flags for supplier generation
	pluginGenerateSupplierCmd.Flags().String("in-repo", "", "Plugin name in the configured repos directory (default: use current directory)")

	// Flags for plugin update
	pluginGenerateUpdateCmd.Flags().String("in-repo", "", "Plugin name in the configured repos directory (default: use current directory)")
	pluginGenerateUpdateCmd.Flags().BoolP("interactive", "i", false, "Interactive mode - prompt for each file")
	pluginGenerateUpdateCmd.Flags().BoolP("force", "f", false, "Force overwrite all files without prompting")
	pluginGenerateUpdateCmd.Flags().BoolP("dry-run", "d", false, "Dry run - show what would be updated without making changes")
	pluginGenerateUpdateCmd.Flags().Bool("include-source", false, "Refresh user-owned source files (main.go, PluginClass.java, plugin.py) from templates")
	pluginGenerateUpdateCmd.Flags().Bool("no-git", false, "Skip automatic `git init` in the plugin directory")
}

// resolvePluginDirFromCmd resolves the plugin directory from --in-repo or cwd.
// Returns the absolute plugin directory and the plugin name (basename).
func resolvePluginDirFromCmd(cmd *cobra.Command) (string, string, error) {
	inRepo, _ := cmd.Flags().GetString("in-repo")
	var pluginDir string
	if inRepo != "" {
		config := app.GetInstance().GetConfig()
		repoPath := config.Development.RepoPath
		if repoPath == "" {
			return "", "", fmt.Errorf("repoPath not configured in development config")
		}
		pluginDir = filepath.Join(repoPath, inRepo)
	} else {
		cwd, err := os.Getwd()
		if err != nil {
			return "", "", fmt.Errorf("failed to determine current directory: %w", err)
		}
		pluginDir = cwd
	}
	if _, err := os.Stat(pluginDir); os.IsNotExist(err) {
		return "", "", fmt.Errorf("plugin directory does not exist: %s", pluginDir)
	}
	return pluginDir, filepath.Base(pluginDir), nil
}

// detectPluginLanguage detects the plugin language by checking for build files
func detectPluginLanguage(pluginDir string) (string, error) {
	markers := map[string]string{
		"build.gradle":   "java",
		"pyproject.toml": "python",
		"go.mod":         "go",
	}

	var detected []string
	for file, lang := range markers {
		if _, err := os.Stat(filepath.Join(pluginDir, file)); err == nil {
			detected = append(detected, lang)
		}
	}

	if len(detected) > 1 {
		return "", fmt.Errorf("plugin directory contains multiple build files (%s) - cannot determine language", strings.Join(detected, ", "))
	}

	if len(detected) == 1 {
		return detected[0], nil
	}

	return "", fmt.Errorf("plugin directory does not contain build.gradle, pyproject.toml, or go.mod - cannot determine language")
}

func runPluginGenerate(cmd *cobra.Command, args []string) error {
	javaFlag, _ := cmd.Flags().GetBool("java")
	pythonFlag, _ := cmd.Flags().GetBool("python")
	goFlag, _ := cmd.Flags().GetBool("go")

	if javaFlag {
		return runPluginGenerateJava(cmd, args)
	}
	if pythonFlag {
		return runPluginGeneratePython(cmd, args)
	}
	if goFlag {
		return runPluginGenerateGo(cmd, args)
	}

	return fmt.Errorf("must specify --java, --python, or --go flag")
}

func runPluginGenerateUpdate(cmd *cobra.Command, args []string) error {
	pluginDir, _, err := resolvePluginDirFromCmd(cmd)
	if err != nil {
		return err
	}
	interactive, _ := cmd.Flags().GetBool("interactive")
	force, _ := cmd.Flags().GetBool("force")
	dryRun, _ := cmd.Flags().GetBool("dry-run")
	noGit, _ := cmd.Flags().GetBool("no-git")
	includeSource, _ := cmd.Flags().GetBool("include-source")

	language, err := detectPluginLanguage(pluginDir)
	if err != nil {
		return fmt.Errorf("failed to detect plugin language: %w", err)
	}

	var gen generator.Generator
	switch language {
	case "java":
		gen = java.NewJavaGenerator()
	case "python":
		gen = python.NewPythonGenerator()
	case "go":
		gen = goGen.NewGoGenerator()
	default:
		return fmt.Errorf("unsupported language: %s", language)
	}

	fmt.Printf("Updating %s plugin at %s\n", language, pluginDir)
	fmt.Println("Refreshes scaffolding (build files, Dockerfiles, Makefile, flow files, README, etc.).")
	if includeSource {
		fmt.Println("Source files (main.go / PluginClass.java / plugin.py) will be re-rendered too.")
	} else {
		fmt.Println("User-owned source files are preserved (use --include-source to refresh them).")
	}
	fmt.Println()

	options := &generator.UpdateOptions{
		Interactive:   interactive,
		Force:         force,
		DryRun:        dryRun,
		IncludeSource: includeSource,
		PluginDir:     pluginDir,
	}

	if err := gen.UpdatePlugin(filepath.Base(pluginDir), options); err != nil {
		return fmt.Errorf("error updating plugin: %w", err)
	}

	if !dryRun && !noGit {
		initGitRepoIfNeeded(pluginDir)
	}

	return nil
}

func runPluginGenerateAction(cmd *cobra.Command, args []string) error {
	actionName := args[0]
	pluginDir, pluginName, err := resolvePluginDirFromCmd(cmd)
	if err != nil {
		return err
	}

	language, err := detectPluginLanguage(pluginDir)
	if err != nil {
		return fmt.Errorf("failed to detect plugin language: %w", err)
	}

	actionType, _ := cmd.Flags().GetString("type")

	var gen generator.Generator
	switch language {
	case "java":
		gen = java.NewJavaGenerator()
	case "python":
		gen = python.NewPythonGenerator()
	case "go":
		gen = goGen.NewGoGenerator()
	default:
		return fmt.Errorf("unsupported language: %s", language)
	}

	opts := &generator.GenerateActionOptions{PluginDir: pluginDir}
	if err := gen.GenerateAction(pluginName, actionName, actionType, opts); err != nil {
		return fmt.Errorf("error generating action: %w", err)
	}

	fmt.Println(styles.SuccessStyle.Render("Action generated successfully"))
	return nil
}

func runPluginGenerateSupplier(cmd *cobra.Command, args []string) error {
	supplierName := args[0]
	pluginDir, pluginName, err := resolvePluginDirFromCmd(cmd)
	if err != nil {
		return err
	}

	language, err := detectPluginLanguage(pluginDir)
	if err != nil {
		return fmt.Errorf("failed to detect plugin language: %w", err)
	}

	if language != "go" {
		return fmt.Errorf("supplier generation is only supported for Go plugins (detected: %s)", language)
	}

	gen := goGen.NewGoGenerator().(*goGen.GoGenerator)
	opts := &generator.GenerateSupplierOptions{PluginDir: pluginDir}
	if err := gen.GenerateSupplier(pluginName, supplierName, opts); err != nil {
		return fmt.Errorf("error generating supplier: %w", err)
	}

	fmt.Println(styles.SuccessStyle.Render("Supplier generated successfully"))
	return nil
}

func runPluginGenerateGo(cmd *cobra.Command, args []string) error {
	pluginName, _ := cmd.Flags().GetString("name")

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return fmt.Errorf("repoPath not configured in development config")
	}

	pluginDir := filepath.Join(repoPath, pluginName)

	if _, err := os.Stat(pluginDir); err == nil {
		return fmt.Errorf("plugin already exists at %s; use `deltafi plugin generate update` to update it", pluginDir)
	}

	namespace, _ := cmd.Flags().GetString("namespace")
	if namespace == "" {
		namespace = "org.deltafi"
	}

	modulePath, _ := cmd.Flags().GetString("module")
	if modulePath == "" {
		return fmt.Errorf("--module flag is required for Go plugins (e.g., github.com/org/%s)", pluginName)
	}

	gen := goGen.NewGoGenerator()
	options := &generator.PluginOptions{
		GroupID:     namespace,
		Description: fmt.Sprintf("Go plugin for DeltaFi: %s", pluginName),
		ModulePath:  modulePath,
	}

	if err := gen.GeneratePlugin(pluginName, options); err != nil {
		return fmt.Errorf("error generating Go plugin: %w", err)
	}

	pluginPath := filepath.Join(config.Development.RepoPath, pluginName)
	if noGit, _ := cmd.Flags().GetBool("no-git"); !noGit {
		initAndStageGitRepo(pluginPath)
	}

	fmt.Println(styles.SuccessStyle.Render("Go plugin generated successfully"))
	fmt.Printf("Plugin location: %s\n", pluginPath)
	fmt.Printf("Module path: %s\n", modulePath)
	fmt.Printf("Namespace: %s\n", namespace)

	return nil
}

func runPluginGenerateJava(cmd *cobra.Command, args []string) error {
	pluginName, _ := cmd.Flags().GetString("name")

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return fmt.Errorf("repoPath not configured in development config")
	}

	pluginDir := filepath.Join(repoPath, pluginName)

	if _, err := os.Stat(pluginDir); err == nil {
		return fmt.Errorf("plugin already exists at %s; use `deltafi plugin generate update` to update it", pluginDir)
	}

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
	if noGit, _ := cmd.Flags().GetBool("no-git"); !noGit {
		initAndStageGitRepo(pluginPath)
	}

	fmt.Println(styles.SuccessStyle.Render("Java plugin generated successfully"))
	fmt.Printf("Plugin location: %s\n", pluginPath)
	fmt.Printf("Namespace: %s\n", namespace)

	return nil
}

func runPluginGeneratePython(cmd *cobra.Command, args []string) error {
	pluginName, _ := cmd.Flags().GetString("name")

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return fmt.Errorf("repoPath not configured in development config")
	}

	pluginDir := filepath.Join(repoPath, pluginName)

	if _, err := os.Stat(pluginDir); err == nil {
		return fmt.Errorf("plugin already exists at %s; use `deltafi plugin generate update` to update it", pluginDir)
	}

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
	if noGit, _ := cmd.Flags().GetBool("no-git"); !noGit {
		initAndStageGitRepo(pluginPath)
	}

	fmt.Println(styles.SuccessStyle.Render("Python plugin generated successfully"))
	fmt.Printf("Plugin location: %s\n", pluginPath)
	fmt.Printf("Namespace: %s\n", namespace)

	return nil
}

// initGitRepoIfNeeded runs `git init` in pluginDir when git is available and
// pluginDir is not already inside a git working tree. Failures are reported as
// warnings and never block plugin generation/update.
func initGitRepoIfNeeded(pluginDir string) {
	gitPath, err := exec.LookPath("git")
	if err != nil {
		return
	}

	check := exec.Command(gitPath, "rev-parse", "--is-inside-work-tree")
	check.Dir = pluginDir
	if err := check.Run(); err == nil {
		return
	}

	init := exec.Command(gitPath, "init", "--quiet")
	init.Dir = pluginDir
	if out, err := init.CombinedOutput(); err != nil {
		fmt.Printf("warning: git init failed in %s: %v\n%s", pluginDir, err, string(out))
		return
	}
	fmt.Printf("Initialized git repository in %s\n", pluginDir)
}

// initAndStageGitRepo initializes a git repo in pluginDir if needed and then
// stages every file under pluginDir. Used by `plugin generate` so the new
// scaffolding shows up as a single staged changeset.
func initAndStageGitRepo(pluginDir string) {
	gitPath, err := exec.LookPath("git")
	if err != nil {
		return
	}

	initGitRepoIfNeeded(pluginDir)

	add := exec.Command(gitPath, "add", ".")
	add.Dir = pluginDir
	if out, err := add.CombinedOutput(); err != nil {
		fmt.Printf("warning: git add failed in %s: %v\n%s", pluginDir, err, string(out))
	}
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
			if toComplete == "" || strings.HasPrefix(name, toComplete) {
				dirNames = append(dirNames, name)
			}
		}
	}

	sort.Strings(dirNames)
	return escapedCompletions(dirNames, toComplete)
}
