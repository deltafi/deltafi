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
// ABOUTME: Tool registry for MCP server
// ABOUTME: Manages tool definitions, handlers, and tag-based filtering

package registry

import (
	"context"
	"encoding/json"
	"slices"
	"sync"

	"github.com/mark3labs/mcp-go/mcp"
	"github.com/mark3labs/mcp-go/server"
)

// Tool represents an MCP tool with its definition, handler, and tags
type Tool struct {
	Definition mcp.Tool
	Handler    server.ToolHandlerFunc
	Tags       []string
}

// Registry manages the collection of available tools
type Registry struct {
	tools []Tool
	mu    sync.RWMutex
}

// Global registry instance
var (
	instance     *Registry
	registryOnce sync.Once
)

// Get returns the singleton registry instance
func Get() *Registry {
	registryOnce.Do(func() {
		instance = &Registry{
			tools: make([]Tool, 0),
		}
	})
	return instance
}

// Register adds a tool to the registry
func (r *Registry) Register(t Tool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.tools = append(r.tools, t)
}

// GetByTags returns tools that match any of the specified tags
// If tags contains "all", all tools are returned
func (r *Registry) GetByTags(tags []string) []Tool {
	r.mu.RLock()
	defer r.mu.RUnlock()

	if slices.Contains(tags, "all") {
		return r.tools
	}

	var result []Tool
	for _, tool := range r.tools {
		for _, tag := range tool.Tags {
			if slices.Contains(tags, tag) {
				result = append(result, tool)
				break
			}
		}
	}
	return result
}

// GetAllTags returns all unique tags from registered tools
func (r *Registry) GetAllTags() []string {
	r.mu.RLock()
	defer r.mu.RUnlock()

	tagSet := make(map[string]bool)
	for _, tool := range r.tools {
		for _, tag := range tool.Tags {
			tagSet[tag] = true
		}
	}

	tags := make([]string, 0, len(tagSet))
	for tag := range tagSet {
		tags = append(tags, tag)
	}
	return tags
}

// Count returns the total number of registered tools
func (r *Registry) Count() int {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return len(r.tools)
}

// Helper function to format any value as JSON for tool results
func FormatJSON(v any) (string, error) {
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return "", err
	}
	return string(data), nil
}

// Helper function to create a tool result from any value
func ResultFromJSON(v any) (*mcp.CallToolResult, error) {
	text, err := FormatJSON(v)
	if err != nil {
		return mcp.NewToolResultError("failed to format result: " + err.Error()), nil
	}
	return mcp.NewToolResultText(text), nil
}

// Helper function to create an error result
func ErrorResult(msg string) *mcp.CallToolResult {
	return mcp.NewToolResultError(msg)
}

// Helper function to create an error result from an error
func ErrorResultFromErr(err error) *mcp.CallToolResult {
	return mcp.NewToolResultError(err.Error())
}

// ToolHandler is a convenience type for tool handler functions
type ToolHandler func(ctx context.Context, request mcp.CallToolRequest) (*mcp.CallToolResult, error)
