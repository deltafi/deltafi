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
// ABOUTME: Plugin MCP tools for plugin lifecycle management
// ABOUTME: Provides tools for installing, managing, and querying plugins

package tools

import (
	"context"
	"fmt"
	"time"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	// list_plugins - List installed plugins
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("list_plugins",
			mcp.WithDescription("List all installed plugins with their status, version, and image"),
		),
		Handler: listPlugins,
		Tags:    []string{"plugins"},
	})

	// get_plugin_details - Get plugin actions and variables
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_plugin_details",
			mcp.WithDescription("Get detailed information about a plugin including its actions and variables"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Plugin display name")),
		),
		Handler: getPluginDetails,
		Tags:    []string{"plugins"},
	})

	// install_plugin - Install plugin from image
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("install_plugin",
			mcp.WithDescription("Install or upgrade a plugin from a container image. Optionally wait for installation to complete."),
			mcp.WithString("image", mcp.Required(), mcp.Description("Container image (e.g., my-plugin:latest)")),
			mcp.WithString("secret", mcp.Description("Image pull secret for private registries (optional)")),
			mcp.WithBoolean("wait", mcp.Description("Wait for installation to complete (default: false). Times out after 5 minutes.")),
		),
		Handler: installPlugin,
		Tags:    []string{"plugins"},
	})

	// uninstall_plugin - Remove a plugin
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("uninstall_plugin",
			mcp.WithDescription("Uninstall a plugin from the system"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Plugin display name")),
		),
		Handler: uninstallPlugin,
		Tags:    []string{"plugins"},
	})

	// enable_plugin - Enable a disabled plugin
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("enable_plugin",
			mcp.WithDescription("Enable a previously disabled plugin"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Plugin display name")),
		),
		Handler: enablePlugin,
		Tags:    []string{"plugins"},
	})

	// disable_plugin - Disable a plugin
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("disable_plugin",
			mcp.WithDescription("Disable a plugin without removing it"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Plugin display name")),
		),
		Handler: disablePlugin,
		Tags:    []string{"plugins"},
	})

	// retry_plugin_install - Retry failed installation
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("retry_plugin_install",
			mcp.WithDescription("Retry a failed plugin installation"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Plugin display name")),
		),
		Handler: retryPluginInstall,
		Tags:    []string{"plugins"},
	})

	// rollback_plugin - Rollback to previous version
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("rollback_plugin",
			mcp.WithDescription("Rollback a failed plugin upgrade to the previous working version"),
			mcp.WithString("name", mcp.Required(), mcp.Description("Plugin display name")),
		),
		Handler: rollbackPlugin,
		Tags:    []string{"plugins"},
	})
}

func listPlugins(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetPlugins()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type PluginInfo struct {
		Name     string `json:"name"`
		Image    string `json:"image,omitempty"`
		Version  string `json:"version"`
		Status   string `json:"status"`
		Disabled bool   `json:"disabled"`
	}

	plugins := make([]PluginInfo, 0, len(resp.Plugins))
	for _, p := range resp.Plugins {
		image := ""
		if p.ImageName != nil {
			image = *p.ImageName
			if p.ImageTag != nil {
				image += ":" + *p.ImageTag
			}
		}

		status := string(p.InstallState)
		if p.Disabled {
			status = "DISABLED"
		}

		plugins = append(plugins, PluginInfo{
			Name:     p.DisplayName,
			Image:    image,
			Version:  p.PluginCoordinates.Version,
			Status:   status,
			Disabled: p.Disabled,
		})
	}

	return registry.ResultFromJSON(plugins)
}

func getPluginDetails(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	resp, err := graphql.GetPluginDetails()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	for _, plugin := range resp.Plugins {
		if plugin.DisplayName == name {
			return registry.ResultFromJSON(plugin)
		}
	}

	return registry.ErrorResult(fmt.Sprintf("plugin %s not found", name)), nil
}

func installPlugin(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	image, err := req.RequireString("image")
	if err != nil {
		return registry.ErrorResult("image parameter is required"), nil
	}

	secret := ""
	if s, err := req.RequireString("secret"); err == nil {
		secret = s
	}

	resp, err := graphql.InstallPlugin(image, secret)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	if !resp.InstallPlugin.Success {
		errors := ""
		for _, e := range resp.InstallPlugin.Errors {
			if e != nil {
				errors += *e + "; "
			}
		}
		return registry.ErrorResult("installation failed: " + errors), nil
	}

	// Check if wait was requested
	args := req.GetArguments()
	if wait, ok := args["wait"].(bool); ok && wait {
		return waitForPluginInstall(ctx, image)
	}

	return mcp.NewToolResultText(fmt.Sprintf("Plugin %s queued for installation. Use list_plugins to check status.", image)), nil
}

func uninstallPlugin(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	plugin, err := findPluginByName(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	resp, err := graphql.UninstallPlugin(
		plugin.PluginCoordinates.GroupId,
		plugin.PluginCoordinates.ArtifactId,
		plugin.PluginCoordinates.Version,
	)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	if !resp.UninstallPlugin.Success {
		errors := ""
		for _, e := range resp.UninstallPlugin.Errors {
			if e != nil {
				errors += *e + "; "
			}
		}
		return registry.ErrorResult("uninstall failed: " + errors), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("Plugin %s uninstalled successfully", name)), nil
}

func enablePlugin(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	plugin, err := findPluginByName(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	_, err = graphql.EnablePlugin(
		plugin.PluginCoordinates.GroupId,
		plugin.PluginCoordinates.ArtifactId,
		plugin.PluginCoordinates.Version,
	)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("Plugin %s enabled", name)), nil
}

func disablePlugin(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	plugin, err := findPluginByName(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	_, err = graphql.DisablePlugin(
		plugin.PluginCoordinates.GroupId,
		plugin.PluginCoordinates.ArtifactId,
		plugin.PluginCoordinates.Version,
	)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("Plugin %s disabled", name)), nil
}

func retryPluginInstall(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	plugin, err := findPluginByName(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	_, err = graphql.RetryPluginInstall(
		plugin.PluginCoordinates.GroupId,
		plugin.PluginCoordinates.ArtifactId,
		plugin.PluginCoordinates.Version,
	)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("Plugin %s retry initiated", name)), nil
}

func rollbackPlugin(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, err := req.RequireString("name")
	if err != nil {
		return registry.ErrorResult("name parameter is required"), nil
	}

	plugin, err := findPluginByName(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	_, err = graphql.RollbackPlugin(
		plugin.PluginCoordinates.GroupId,
		plugin.PluginCoordinates.ArtifactId,
		plugin.PluginCoordinates.Version,
	)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("Plugin %s rollback initiated", name)), nil
}

// pluginFetcher abstracts the GetPlugins call for testability.
type pluginFetcher func() (*graphql.GetPluginsResponse, error)

func waitForPluginInstall(ctx context.Context, image string) (*mcp.CallToolResult, error) {
	return pollPluginInstall(ctx, image, 5*time.Minute, 2*time.Second, graphql.GetPlugins)
}

func pollPluginInstall(ctx context.Context, image string, timeout, poll time.Duration, fetch pluginFetcher) (*mcp.CallToolResult, error) {
	deadline := time.Now().Add(timeout)
	consecutiveErrors := 0
	const maxConsecutiveErrors = 3

	for time.Now().Before(deadline) {
		select {
		case <-ctx.Done():
			return registry.ErrorResult("cancelled while waiting for plugin install"), nil
		case <-time.After(poll):
		}

		resp, err := fetch()
		if err != nil {
			consecutiveErrors++
			if consecutiveErrors >= maxConsecutiveErrors {
				return registry.ErrorResultFromErr(fmt.Errorf("failed to poll plugin status after %d attempts: %w", consecutiveErrors, err)), nil
			}
			continue
		}
		consecutiveErrors = 0

		for _, p := range resp.Plugins {
			pluginImage := ""
			if p.ImageName != nil {
				pluginImage = *p.ImageName
				if p.ImageTag != nil {
					pluginImage += ":" + *p.ImageTag
				}
			}
			if pluginImage != image {
				continue
			}

			switch p.InstallState {
			case graphql.PluginStateInstalled:
				return mcp.NewToolResultText(fmt.Sprintf("Plugin %s installed successfully (version %s)", p.DisplayName, p.PluginCoordinates.Version)), nil
			case graphql.PluginStateFailed:
				msg := fmt.Sprintf("Plugin %s installation failed", image)
				if p.InstallError != nil {
					msg += ": " + *p.InstallError
				}
				return registry.ErrorResult(msg), nil
			}
		}
	}

	return registry.ErrorResult(fmt.Sprintf("timed out waiting for plugin %s to install (%s)", image, timeout)), nil
}

func findPluginByName(name string) (*graphql.GetPluginsPluginsPlugin, error) {
	resp, err := graphql.GetPlugins()
	if err != nil {
		return nil, fmt.Errorf("unable to get plugins: %w", err)
	}

	for _, plugin := range resp.Plugins {
		if plugin.DisplayName == name {
			return &plugin, nil
		}
	}

	return nil, fmt.Errorf("plugin %s not found", name)
}
