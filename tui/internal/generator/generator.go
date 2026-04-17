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
package generator

// Generator defines the interface for plugin generators
type Generator interface {
	// GeneratePlugin generates a new plugin project
	GeneratePlugin(pluginName string, options *PluginOptions) error

	// GenerateAction generates a new action in an existing plugin
	GenerateAction(pluginName string, actionName string, actionType string, options *GenerateActionOptions) error

	// UpdatePlugin updates an existing plugin with the latest templates
	UpdatePlugin(pluginName string, options *UpdateOptions) error

	// LintPlugin checks an existing plugin for common problems and returns
	// a list of issues. A non-nil error indicates the lint run itself failed
	// (e.g. unable to read the plugin directory) rather than a failed check.
	LintPlugin(pluginName string, options *LintOptions) ([]LintIssue, error)
}

// UpdateOptions contains options for plugin updates
type UpdateOptions struct {
	// Interactive mode for handling conflicts
	Interactive bool
	// Force overwrite all files without prompting
	Force bool
	// Dry run - show what would be updated without making changes
	DryRun bool
	// IncludeSource refreshes user-owned source files (Go main.go,
	// Java PluginClass.java, Python plugin.py). Defaults to false so
	// `plugin generate update` is safe to run after the user has customized
	// their plugin code.
	IncludeSource bool
	// PluginDir, if set, points at the plugin directory directly and bypasses
	// resolution via repoPath/pluginName. The plugin name is derived from the
	// directory's basename.
	PluginDir string
}

// GenerateActionOptions contains options for action generation.
type GenerateActionOptions struct {
	// PluginDir, if set, points at the plugin directory directly and bypasses
	// resolution via repoPath/pluginName.
	PluginDir string
}

// GenerateSupplierOptions contains options for supplier generation.
type GenerateSupplierOptions struct {
	// PluginDir, if set, points at the plugin directory directly and bypasses
	// resolution via repoPath/pluginName.
	PluginDir string
}

// LintSeverity classifies a lint issue.
type LintSeverity int

const (
	// LintInfo is informational; not a failure.
	LintInfo LintSeverity = iota
	// LintWarning is a soft problem that doesn't fail the lint run.
	LintWarning
	// LintError is a hard problem that fails the lint run.
	LintError
)

// String returns a short label for the severity.
func (s LintSeverity) String() string {
	switch s {
	case LintInfo:
		return "info"
	case LintWarning:
		return "warning"
	case LintError:
		return "error"
	default:
		return "unknown"
	}
}

// LintIssue is a single problem reported by a plugin linter.
type LintIssue struct {
	Severity LintSeverity
	// File is the path (relative to the plugin dir) where the issue was found, if applicable.
	File string
	// Message describes the issue.
	Message string
	// Fix, when non-nil, is a function that fixes the issue. Only called when --fix is passed.
	Fix func() error
}

// LintOptions contains options for plugin linting.
type LintOptions struct {
	// PluginDir, if set, points at the plugin directory directly and bypasses
	// resolution via repoPath/pluginName.
	PluginDir string
	// Fix, when true, applies automatic fixes for any issues that have a Fix function.
	Fix bool
}

// PluginOptions contains options for plugin generation
type PluginOptions struct {
	GroupID     string
	Description string
	ModulePath  string // Go module path (used only by Go generator)
}
