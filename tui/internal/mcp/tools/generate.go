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
// ABOUTME: Plugin generation MCP tools for creating new plugins and actions
// ABOUTME: Provides tools for scaffolding Java and Python DeltaFi plugins

package tools

import (
	"context"
	"fmt"
	"os"
	"path/filepath"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/generator"
	goGen "github.com/deltafi/tui/internal/generator/golang"
	"github.com/deltafi/tui/internal/generator/java"
	"github.com/deltafi/tui/internal/generator/python"
	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	// generate_plugin - Create a new plugin skeleton
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("generate_plugin",
			mcp.WithDescription("Generate a new DeltaFi plugin skeleton. Creates a complete project structure with build files, sample data source, and configuration."),
			mcp.WithString("name", mcp.Required(), mcp.Description("Plugin name (e.g., my-awesome-plugin)")),
			mcp.WithString("language", mcp.Required(), mcp.Description("Programming language: java, python, or go")),
			mcp.WithString("namespace", mcp.Description("Plugin namespace/group ID (default: org.deltafi.<plugin-name> for java/python, org.deltafi for go)")),
			mcp.WithString("description", mcp.Description("Plugin description")),
			mcp.WithString("module", mcp.Description("Go module path (required for go, e.g., github.com/org/my-plugin)")),
		),
		Handler: generatePlugin,
		Tags:    []string{"generate"},
	})

	// generate_action - Add an action to an existing plugin
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("generate_action",
			mcp.WithDescription("Generate a new action in an existing plugin. Creates action class, test, and flow configuration."),
			mcp.WithString("plugin", mcp.Required(), mcp.Description("Plugin name (must exist in repoPath)")),
			mcp.WithString("action", mcp.Required(), mcp.Description("Action name (e.g., JsonToYamlAction)")),
			mcp.WithString("type", mcp.Description("Action type: TransformAction, EgressAction, TimedIngressAction, JoiningTransformAction, TransformManyAction (default: TransformAction)")),
		),
		Handler: generateAction,
		Tags:    []string{"generate"},
	})

	// list_generated_plugins - List plugins in the development repo path
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("list_generated_plugins",
			mcp.WithDescription("List plugin directories in the development repoPath"),
		),
		Handler: listGeneratedPlugins,
		Tags:    []string{"generate"},
	})
}

func generatePlugin(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	language, err := req.RequireString("language")
	if err != nil {
		return registry.ErrorResult("language parameter is required"), nil
	}

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return registry.ErrorResult("repoPath not configured in development config"), nil
	}

	pluginDir := filepath.Join(repoPath, name)

	// Check if plugin directory already exists
	if _, err := os.Stat(pluginDir); err == nil {
		return registry.ErrorResult(fmt.Sprintf("plugin directory already exists: %s", pluginDir)), nil
	}

	// Get optional parameters
	args := req.GetArguments()
	namespace := ""
	if ns, ok := args["namespace"].(string); ok && ns != "" {
		namespace = ns
	}

	description := fmt.Sprintf("%s plugin for DeltaFi: %s", language, name)
	if desc, ok := args["description"].(string); ok && desc != "" {
		description = desc
	}

	modulePath := ""
	if mp, ok := args["module"].(string); ok && mp != "" {
		modulePath = mp
	}

	gen, namespace, err := resolvePluginGenerator(language, name, namespace, modulePath)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	options := &generator.PluginOptions{
		GroupID:     namespace,
		Description: description,
		ModulePath:  modulePath,
	}

	if err := gen.GeneratePlugin(name, options); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type GenerateResult struct {
		PluginName  string `json:"plugin_name"`
		Language    string `json:"language"`
		Namespace   string `json:"namespace"`
		Description string `json:"description"`
		Location    string `json:"location"`
		ModulePath  string `json:"module_path,omitempty"`
	}

	result := GenerateResult{
		PluginName:  name,
		Language:    language,
		Namespace:   namespace,
		Description: description,
		Location:    pluginDir,
		ModulePath:  modulePath,
	}

	return registry.ResultFromJSON(result)
}

func generateAction(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	pluginName, err := req.RequireString("plugin")
	if err != nil {
		return registry.ErrorResult("plugin parameter is required"), nil
	}

	actionName, err := req.RequireString("action")
	if err != nil {
		return registry.ErrorResult("action parameter is required"), nil
	}

	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return registry.ErrorResult("repoPath not configured in development config"), nil
	}

	pluginDir := filepath.Join(repoPath, pluginName)

	// Check if plugin directory exists
	if _, err := os.Stat(pluginDir); os.IsNotExist(err) {
		return registry.ErrorResult(fmt.Sprintf("plugin directory does not exist: %s", pluginDir)), nil
	}

	// Detect language
	language, err := detectPluginLanguage(pluginDir)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	// Get action type (default to TransformAction)
	actionType := "TransformAction"
	args := req.GetArguments()
	if at, ok := args["type"].(string); ok && at != "" {
		actionType = at
	}

	gen, err := generatorForLanguage(language)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	opts := &generator.GenerateActionOptions{PluginDir: pluginDir}
	if err := gen.GenerateAction(pluginName, actionName, actionType, opts); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type ActionResult struct {
		PluginName string `json:"plugin_name"`
		ActionName string `json:"action_name"`
		ActionType string `json:"action_type"`
		Language   string `json:"language"`
	}

	result := ActionResult{
		PluginName: pluginName,
		ActionName: actionName,
		ActionType: actionType,
		Language:   language,
	}

	return registry.ResultFromJSON(result)
}

func listGeneratedPlugins(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	config := app.GetInstance().GetConfig()
	repoPath := config.Development.RepoPath
	if repoPath == "" {
		return registry.ErrorResult("repoPath not configured in development config"), nil
	}

	entries, err := os.ReadDir(repoPath)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type PluginInfo struct {
		Name     string `json:"name"`
		Language string `json:"language,omitempty"`
		Path     string `json:"path"`
	}

	plugins := make([]PluginInfo, 0)
	for _, entry := range entries {
		if entry.IsDir() {
			pluginDir := filepath.Join(repoPath, entry.Name())
			language, _ := detectPluginLanguage(pluginDir)

			plugins = append(plugins, PluginInfo{
				Name:     entry.Name(),
				Language: language,
				Path:     pluginDir,
			})
		}
	}

	type ListResult struct {
		RepoPath string       `json:"repo_path"`
		Plugins  []PluginInfo `json:"plugins"`
	}

	result := ListResult{
		RepoPath: repoPath,
		Plugins:  plugins,
	}

	return registry.ResultFromJSON(result)
}

// generatorForLanguage returns the generator for the given language, or an error
// if the language is unsupported.
func generatorForLanguage(language string) (generator.Generator, error) {
	switch language {
	case "java":
		return java.NewJavaGenerator(), nil
	case "python":
		return python.NewPythonGenerator(), nil
	case "go":
		return goGen.NewGoGenerator(), nil
	default:
		return nil, fmt.Errorf("unsupported language: %s (valid: java, python, go)", language)
	}
}

// resolvePluginGenerator selects the generator and resolves the namespace default
// for a new plugin. Returns an error if the language is unsupported or required
// language-specific options (e.g. Go's module path) are missing.
func resolvePluginGenerator(language, pluginName, namespace, modulePath string) (generator.Generator, string, error) {
	gen, err := generatorForLanguage(language)
	if err != nil {
		return nil, "", err
	}

	switch language {
	case "java":
		if namespace == "" {
			namespace = fmt.Sprintf("org.deltafi.%s", java.PluginNameToPackageName(pluginName))
		}
	case "python":
		if namespace == "" {
			namespace = fmt.Sprintf("org.deltafi.%s", python.PluginNameToPackageName(pluginName))
		}
	case "go":
		if namespace == "" {
			namespace = "org.deltafi"
		}
		if modulePath == "" {
			return nil, "", fmt.Errorf("module parameter is required for go plugins (e.g., github.com/org/%s)", pluginName)
		}
	}

	return gen, namespace, nil
}

// detectPluginLanguage detects the plugin language by checking for build.gradle (Java),
// pyproject.toml (Python), or go.mod (Go)
func detectPluginLanguage(pluginDir string) (string, error) {
	markers := map[string]string{
		"java":   "build.gradle",
		"python": "pyproject.toml",
		"go":     "go.mod",
	}

	found := make([]string, 0, len(markers))
	for lang, file := range markers {
		if _, err := os.Stat(filepath.Join(pluginDir, file)); err == nil {
			found = append(found, lang)
		}
	}

	if len(found) > 1 {
		return "", fmt.Errorf("plugin directory contains markers for multiple languages (%v) - cannot determine language", found)
	}

	if len(found) == 1 {
		return found[0], nil
	}

	return "", fmt.Errorf("plugin directory does not contain build.gradle, pyproject.toml, or go.mod - cannot determine language")
}
