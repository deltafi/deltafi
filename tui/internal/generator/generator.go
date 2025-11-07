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
package generator

// Generator defines the interface for plugin generators
type Generator interface {
	// GeneratePlugin generates a new plugin project
	GeneratePlugin(pluginName string, options *PluginOptions) error

	// GenerateAction generates a new action in an existing plugin
	GenerateAction(pluginName string, actionName string, actionType string) error

	// UpdatePlugin updates an existing plugin with the latest templates
	UpdatePlugin(pluginName string, options *UpdateOptions) error
}

// UpdateOptions contains options for plugin updates
type UpdateOptions struct {
	// Interactive mode for handling conflicts
	Interactive bool
	// Force overwrite all files without prompting
	Force bool
	// Dry run - show what would be updated without making changes
	DryRun bool
}

// PluginOptions contains options for plugin generation
type PluginOptions struct {
	GroupID     string
	Description string
}
