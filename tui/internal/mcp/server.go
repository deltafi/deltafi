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
// ABOUTME: MCP server setup and configuration
// ABOUTME: Creates and runs the Model Context Protocol server for DeltaFi

package mcp

import (
	"fmt"

	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/mcp/registry"
	"github.com/mark3labs/mcp-go/server"

	// Import tool packages to trigger their init() registration
	_ "github.com/deltafi/tui/internal/mcp/tools"
)

// Serve starts the MCP server with the specified tool tags
func Serve(tags []string) error {
	// Get version from app
	version := app.GetVersion()

	// Create MCP server
	s := server.NewMCPServer(
		"DeltaFi",
		version,
		server.WithToolCapabilities(false),
		server.WithRecovery(),
	)

	// Get tools matching the requested tags
	reg := registry.Get()
	tools := reg.GetByTags(tags)

	if len(tools) == 0 {
		return fmt.Errorf("no tools found for tags: %v (available tags: %v)", tags, reg.GetAllTags())
	}

	// Register each tool with the server
	for _, tool := range tools {
		s.AddTool(tool.Definition, tool.Handler)
	}

	// Start the stdio server (blocks until connection closes)
	return server.ServeStdio(s)
}

// GetToolCount returns the number of tools that would be loaded for the given tags
func GetToolCount(tags []string) int {
	return len(registry.Get().GetByTags(tags))
}

// GetAvailableTags returns all available tool tags
func GetAvailableTags() []string {
	return registry.Get().GetAllTags()
}
