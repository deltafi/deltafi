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
// ABOUTME: Admin MCP tools for system configuration and management
// ABOUTME: Provides tools for snapshots, properties, and system settings

package tools

import (
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	// get_system_properties - Get system configuration
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_system_properties",
			mcp.WithDescription("Get all system property sets and their values"),
		),
		Handler: getSystemProperties,
		Tags:    []string{"admin"},
	})

	// list_snapshots - List system snapshots
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("list_snapshots",
			mcp.WithDescription("List all available system snapshots"),
		),
		Handler: listSnapshots,
		Tags:    []string{"admin"},
	})

	// get_snapshot - Get snapshot details
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_snapshot",
			mcp.WithDescription("Get detailed information about a specific snapshot"),
			mcp.WithString("id", mcp.Required(), mcp.Description("Snapshot ID (UUID)")),
		),
		Handler: getSnapshot,
		Tags:    []string{"admin"},
	})

	// create_snapshot - Create system snapshot
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("create_snapshot",
			mcp.WithDescription("Create a new system snapshot capturing current configuration"),
			mcp.WithString("reason", mcp.Description("Reason for creating the snapshot")),
		),
		Handler: createSnapshot,
		Tags:    []string{"admin"},
	})

	// list_delete_policies - List content deletion policies
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("list_delete_policies",
			mcp.WithDescription("List all configured content deletion policies"),
		),
		Handler: listDeletePolicies,
		Tags:    []string{"admin"},
	})

	// get_ssl_info - Get SSL/TLS configuration
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("get_ssl_info",
			mcp.WithDescription("Get SSL/TLS certificate information and settings"),
		),
		Handler: getSslInfo,
		Tags:    []string{"admin"},
	})

	// list_integration_tests - List integration tests
	reg.Register(registry.Tool{
		Definition: mcp.NewTool("list_integration_tests",
			mcp.WithDescription("List all configured integration tests"),
		),
		Handler: listIntegrationTests,
		Tags:    []string{"admin"},
	})
}

func getSystemProperties(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetPropertySets()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.GetPropertySets)
}

func listSnapshots(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetSystemSnapshots()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.GetSystemSnapshots)
}

func getSnapshot(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	idStr, err := req.RequireString("id")
	if err != nil {
		return registry.ErrorResult("id parameter is required"), nil
	}

	id, err := uuid.Parse(idStr)
	if err != nil {
		return registry.ErrorResult(fmt.Sprintf("invalid snapshot ID: %v", err)), nil
	}

	resp, err := graphql.GetSystemSnapshot(id)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.GetSystemSnapshot)
}

func createSnapshot(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	reason := "Created via MCP"
	if r, err := req.RequireString("reason"); err == nil && r != "" {
		reason = r
	}

	resp, err := graphql.SnapshotSystem(&reason)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.SnapshotSystem)
}

func listDeletePolicies(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetDeletePolicies()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.GetDeletePolicies)
}

func getSslInfo(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetSslInfo()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp)
}

func listIntegrationTests(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetIntegrationTests()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.GetIntegrationTests)
}
