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
// ABOUTME: MCP tools that drive the `deltafi plugin generate` command tree
// ABOUTME: Creates new plugins, adds actions/suppliers, and refreshes scaffolding

package tools

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/generator"
	"github.com/deltafi/tui/internal/generator/gitsetup"
	goGen "github.com/deltafi/tui/internal/generator/golang"
	"github.com/deltafi/tui/internal/generator/java"
	"github.com/deltafi/tui/internal/generator/python"
	"github.com/deltafi/tui/internal/mcp/registry"
)

const validActionTypes = "TransformAction, EgressAction, TimedIngressAction, JoiningTransformAction, TransformManyAction (Python also supports TransformExecAction)"

func init() {
	reg := registry.Get()

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("generate_plugin",
			"Generate a new DeltaFi plugin project in the configured repos directory. Writes a fresh scaffold for Java, Python, or Go.",
			mcp.WithString("language", mcp.Required(), mcp.Description("Plugin language: java, python, or go")),
			mcp.WithString("name", mcp.Required(), mcp.Description("Plugin name (used as the directory and artifact name, e.g. my-plugin)")),
			mcp.WithString("namespace", mcp.Description("Plugin namespace / group id. Defaults to org.deltafi.<plugin-name> for Java/Python and org.deltafi for Go.")),
			mcp.WithString("description", mcp.Description("Plugin description. Defaults to \"<Language> plugin for DeltaFi: <name>\".")),
			mcp.WithString("module", mcp.Description("Go module path, required for Go plugins (e.g. github.com/org/my-plugin)")),
			mcp.WithBoolean("no_git", mcp.Description("If true, skip `git init` in the new plugin directory (default false)")),
		),
		Handler: generatePluginTool,
		Tags:    []string{"generate"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("generate_plugin_action",
			"Add a new action to an existing plugin. Language is auto-detected from the plugin's build file. Valid action types: "+validActionTypes+".",
			mcp.WithString("plugin", mcp.Required(), mcp.Description("Plugin name (resolved in the configured repos directory) or an absolute path to the plugin directory")),
			mcp.WithString("name", mcp.Required(), mcp.Description("Action name (e.g. NormalizeJson)")),
			mcp.WithString("type", mcp.Description("Action type (default TransformAction). One of "+validActionTypes+".")),
		),
		Handler: generatePluginActionTool,
		Tags:    []string{"generate"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("generate_plugin_supplier",
			"Add a lookup table supplier to an existing Go plugin. Only valid for Go plugins.",
			mcp.WithString("plugin", mcp.Required(), mcp.Description("Plugin name (resolved in the configured repos directory) or an absolute path to the plugin directory")),
			mcp.WithString("name", mcp.Required(), mcp.Description("Supplier name (e.g. UserDirectory)")),
		),
		Handler: generatePluginSupplierTool,
		Tags:    []string{"generate"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("update_plugin_scaffolding",
			"Refresh an existing plugin's scaffolding (build files, Dockerfile, Makefile, flow files, README) to the current templates. User-owned source files are preserved unless include_source=true. Pass dry_run=true to preview changes without writing.",
			mcp.WithString("plugin", mcp.Required(), mcp.Description("Plugin name (resolved in the configured repos directory) or an absolute path to the plugin directory")),
			mcp.WithBoolean("dry_run", mcp.Description("Show what would change without writing (default false)")),
			mcp.WithBoolean("include_source", mcp.Description("Also refresh user-owned source files (main.go, PluginClass.java, plugin.py). Default false.")),
			mcp.WithBoolean("no_git", mcp.Description("Skip `git init` after the update (default false)")),
		),
		Handler: updatePluginScaffoldingTool,
		Tags:    []string{"generate"},
	})
}

func generatePluginTool(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	language, errResult := requireNonEmptyString(req, "language")
	if errResult != nil {
		return errResult, nil
	}
	language = strings.ToLower(language)
	if language != "java" && language != "python" && language != "go" {
		return registry.ErrorResult(fmt.Sprintf("invalid language %q (expected java, python, or go)", language)), nil
	}

	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	repoPath := app.GetInstance().GetConfig().Development.RepoPath
	if repoPath == "" {
		return registry.ErrorResult("repoPath is not configured in development config"), nil
	}
	pluginDir := filepath.Join(repoPath, name)
	if _, err := os.Stat(pluginDir); err == nil {
		return registry.ErrorResult(fmt.Sprintf("plugin already exists at %s; use update_plugin_scaffolding to refresh it", pluginDir)), nil
	}

	namespace, _ := req.RequireString("namespace")
	if namespace == "" {
		switch language {
		case "java":
			namespace = "org.deltafi." + java.PluginNameToPackageName(name)
		case "python":
			namespace = "org.deltafi." + python.PluginNameToPackageName(name)
		case "go":
			namespace = "org.deltafi"
		}
	}

	description := fmt.Sprintf("%s plugin for DeltaFi: %s", capitalize(language), name)
	if d, err := req.RequireString("description"); err == nil && d != "" {
		description = d
	}

	options := &generator.PluginOptions{
		GroupID:     namespace,
		Description: description,
	}
	var output bytes.Buffer
	options.Output = &output

	var gen generator.Generator
	switch language {
	case "java":
		gen = java.NewJavaGenerator()
	case "python":
		gen = python.NewPythonGenerator()
	case "go":
		module, _ := req.RequireString("module")
		if module == "" {
			return registry.ErrorResult(fmt.Sprintf("module parameter is required for Go plugins (e.g. github.com/org/%s)", name)), nil
		}
		options.ModulePath = module
		gen = goGen.NewGoGenerator()
	}

	genErr := gen.GeneratePlugin(name, options)
	if genErr != nil {
		return registry.ErrorResult(fmt.Sprintf("error generating %s plugin: %v\n%s", language, genErr, output.String())), nil
	}

	noGit, _ := req.RequireBool("no_git")
	if !noGit {
		if _, err := gitsetup.EnsureRepoRoot(pluginDir); err != nil {
			return registry.ErrorResult(fmt.Sprintf("plugin generated at %s but Git initialization failed: %v", pluginDir, gitSetupError(err))), nil
		}
		if err := gitsetup.Stage(pluginDir); err != nil {
			return registry.ErrorResult(fmt.Sprintf("plugin generated at %s but Git initialization failed: %v", pluginDir, gitSetupError(err))), nil
		}
	}

	result := map[string]any{
		"plugin":      name,
		"language":    language,
		"namespace":   namespace,
		"description": description,
		"directory":   pluginDir,
	}
	if options.ModulePath != "" {
		result["module"] = options.ModulePath
	}
	if output.Len() > 0 {
		result["generator_output"] = strings.TrimSpace(output.String())
	}
	return registry.ResultFromJSON(result)
}

func generatePluginActionTool(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	pluginRef, errResult := requireNonEmptyString(req, "plugin")
	if errResult != nil {
		return errResult, nil
	}
	actionName, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	pluginDir, pluginName, err := resolvePluginRef(pluginRef)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	actionType := "TransformAction"
	if t, err := req.RequireString("type"); err == nil && t != "" {
		actionType = t
	}

	gen, language, err := generatorForPlugin(pluginDir)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	var output bytes.Buffer
	opts := &generator.GenerateActionOptions{PluginDir: pluginDir, Output: &output}
	genErr := gen.GenerateAction(pluginName, actionName, actionType, opts)
	if genErr != nil {
		return registry.ErrorResult(fmt.Sprintf("error generating action: %v\n%s", genErr, output.String())), nil
	}

	result := map[string]any{
		"plugin":      pluginName,
		"directory":   pluginDir,
		"language":    language,
		"action":      actionName,
		"action_type": actionType,
	}
	if output.Len() > 0 {
		result["generator_output"] = strings.TrimSpace(output.String())
	}
	return registry.ResultFromJSON(result)
}

func generatePluginSupplierTool(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	pluginRef, errResult := requireNonEmptyString(req, "plugin")
	if errResult != nil {
		return errResult, nil
	}
	supplierName, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	pluginDir, pluginName, err := resolvePluginRef(pluginRef)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	language, err := detectPluginLanguageMCP(pluginDir)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}
	if language != "go" {
		return registry.ErrorResult(fmt.Sprintf("supplier generation is only supported for Go plugins (detected: %s)", language)), nil
	}

	gen := goGen.NewGoGenerator().(*goGen.GoGenerator)
	var output bytes.Buffer
	opts := &generator.GenerateSupplierOptions{PluginDir: pluginDir, Output: &output}
	genErr := gen.GenerateSupplier(pluginName, supplierName, opts)
	if genErr != nil {
		return registry.ErrorResult(fmt.Sprintf("error generating supplier: %v\n%s", genErr, output.String())), nil
	}

	result := map[string]any{
		"plugin":    pluginName,
		"directory": pluginDir,
		"supplier":  supplierName,
	}
	if output.Len() > 0 {
		result["generator_output"] = strings.TrimSpace(output.String())
	}
	return registry.ResultFromJSON(result)
}

func updatePluginScaffoldingTool(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	pluginRef, errResult := requireNonEmptyString(req, "plugin")
	if errResult != nil {
		return errResult, nil
	}
	pluginDir, pluginName, err := resolvePluginRef(pluginRef)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	dryRun, _ := req.RequireBool("dry_run")
	includeSource, _ := req.RequireBool("include_source")
	noGit, _ := req.RequireBool("no_git")

	gen, language, err := generatorForPlugin(pluginDir)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	var output bytes.Buffer
	options := &generator.UpdateOptions{
		Force:         !dryRun, // MCP has no stdin, force when actually writing
		DryRun:        dryRun,
		IncludeSource: includeSource,
		PluginDir:     pluginDir,
		Output:        &output,
	}
	genErr := gen.UpdatePlugin(pluginName, options)
	if genErr != nil {
		return registry.ErrorResult(fmt.Sprintf("error updating plugin: %v\n%s", genErr, output.String())), nil
	}

	if !dryRun && !noGit {
		if _, err := gitsetup.EnsureRepo(pluginDir); err != nil {
			return registry.ErrorResult(fmt.Sprintf("plugin scaffolding updated at %s but Git initialization failed: %v", pluginDir, gitSetupError(err))), nil
		}
	}

	result := map[string]any{
		"plugin":         pluginName,
		"directory":      pluginDir,
		"language":       language,
		"dry_run":        dryRun,
		"include_source": includeSource,
	}
	if output.Len() > 0 {
		result["generator_output"] = strings.TrimSpace(output.String())
	}
	return registry.ResultFromJSON(result)
}

// resolvePluginRef accepts either a plugin name (looked up in RepoPath) or an
// absolute path to a plugin directory. Returns the resolved directory and the
// derived plugin name (the basename of the directory).
func resolvePluginRef(ref string) (string, string, error) {
	if ref == "" {
		return "", "", fmt.Errorf("plugin reference is empty")
	}

	var pluginDir string
	if filepath.IsAbs(ref) || strings.HasPrefix(ref, "~") {
		expanded, err := resolveLocalPath(ref)
		if err != nil {
			return "", "", err
		}
		pluginDir = expanded
	} else {
		repoPath := app.GetInstance().GetConfig().Development.RepoPath
		if repoPath == "" {
			return "", "", fmt.Errorf("repoPath is not configured in development config; pass an absolute path instead")
		}
		pluginDir = filepath.Join(repoPath, ref)
	}

	info, err := os.Stat(pluginDir)
	if err != nil {
		return "", "", fmt.Errorf("plugin directory does not exist: %s", pluginDir)
	}
	if !info.IsDir() {
		return "", "", fmt.Errorf("plugin reference is not a directory: %s", pluginDir)
	}
	return pluginDir, filepath.Base(pluginDir), nil
}

// detectPluginLanguageMCP detects the plugin language by checking for build files.
func detectPluginLanguageMCP(pluginDir string) (string, error) {
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
	return "", fmt.Errorf("plugin directory %s does not contain build.gradle, pyproject.toml, or go.mod - cannot determine language", pluginDir)
}

func generatorForPlugin(pluginDir string) (generator.Generator, string, error) {
	language, err := detectPluginLanguageMCP(pluginDir)
	if err != nil {
		return nil, "", err
	}
	switch language {
	case "java":
		return java.NewJavaGenerator(), language, nil
	case "python":
		return python.NewPythonGenerator(), language, nil
	case "go":
		return goGen.NewGoGenerator(), language, nil
	}
	return nil, "", fmt.Errorf("unsupported language: %s", language)
}

func capitalize(s string) string {
	if s == "" {
		return s
	}
	return strings.ToUpper(s[:1]) + s[1:]
}

// gitSetupError restores the no_git hint when Git is unavailable so callers of
// the generation tools know how to skip Git setup.
func gitSetupError(err error) error {
	if errors.Is(err, gitsetup.ErrGitNotFound) {
		return fmt.Errorf("git executable not found (pass no_git=true to skip Git setup): %w", err)
	}
	return err
}
