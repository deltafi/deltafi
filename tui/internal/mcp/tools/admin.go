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

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

// Seams over the generated GraphQL functions so handlers can be tested without
// a live server.
var (
	updateProperties  = graphql.UpdateProperties
	getSystemSnapshot = graphql.GetSystemSnapshot
)

func init() {
	reg := registry.Get()

	// get_system_properties - Get system configuration
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_system_properties",
			"Get all system property sets and their values",
		),
		Handler: getSystemProperties,
		Tags:    []string{"admin"},
	})

	// update_system_properties - Update one or more system properties
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("update_system_properties",
			"Update one or more system properties. Pass a JSON object mapping property keys to their new values; set a value to null to clear it.",
			mcp.WithObject("updates", mcp.Required(), mcp.Description("Map of property key (string) to new value (string or null). Example: {\"deltafi.ingress.enabled\": \"true\", \"deltafi.requeueSeconds\": \"30\"}")),
		),
		Handler: updateSystemProperties,
		Tags:    []string{"admin"},
	})

	// list_snapshots - List system snapshots
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_snapshots",
			"List all available system snapshots",
		),
		Handler: listSnapshots,
		Tags:    []string{"admin"},
	})

	// get_snapshot - Get snapshot details
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_snapshot",
			"Get detailed information about a specific snapshot",
			mcp.WithString("id", mcp.Required(), mcp.Description("Snapshot ID (UUID)")),
		),
		Handler: getSnapshot,
		Tags:    []string{"admin"},
	})

	// create_snapshot - Create system snapshot
	reg.Register(registry.Tool{
		Definition: registry.SafeWriteTool("create_snapshot",
			"Create a new system snapshot capturing current configuration",
			mcp.WithString("reason", mcp.Description("Reason for creating the snapshot")),
		),
		Handler: createSnapshot,
		Tags:    []string{"admin"},
	})

	// delete_snapshot - Delete a system snapshot
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("delete_snapshot",
			"Delete a system snapshot by id",
			mcp.WithString("id", mcp.Required(), mcp.Description("Snapshot ID (UUID)")),
		),
		Handler: deleteSnapshot,
		Tags:    []string{"admin"},
	})

	// restore_snapshot - Reset system from an existing snapshot
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("restore_snapshot",
			"Reset the system to the state captured by an existing snapshot",
			mcp.WithString("id", mcp.Required(), mcp.Description("Snapshot ID (UUID) to restore from")),
			mcp.WithBoolean("hard_reset", mcp.Description("Perform a hard reset (default false)")),
		),
		Handler: restoreSnapshot,
		Tags:    []string{"admin"},
	})

	// import_snapshot - Import a snapshot from JSON
	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("import_snapshot",
			"Import a system snapshot from a JSON payload. Optionally reset the system to the imported snapshot after import.",
			mcp.WithObject("snapshot", mcp.Required(), mcp.Description("Snapshot object with id, reason, created (ISO8601), schemaVersion, and snapshot (object) fields")),
			mcp.WithBoolean("reset", mcp.Description("If true, reset the system to the imported snapshot after import (default false)")),
			mcp.WithBoolean("hard_reset", mcp.Description("When reset is true, perform a hard reset (default false)")),
		),
		Handler: importSnapshot,
		Tags:    []string{"admin"},
	})

	// list_delete_policies - List content deletion policies
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_delete_policies",
			"List all configured content deletion policies",
		),
		Handler: listDeletePolicies,
		Tags:    []string{"admin"},
	})

	// get_ssl_info - Get SSL/TLS configuration
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_ssl_info",
			"Get SSL/TLS certificate information and settings",
		),
		Handler: getSslInfo,
		Tags:    []string{"admin"},
	})

	// get_status_report - Aggregated system health summary
	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_status_report",
			"Get an aggregated status report: overall status, unacknowledged error count, DeltaFile queue depths, CPU/memory/disk utilization, and DeltaFi version",
		),
		Handler: getStatusReport,
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

func updateSystemProperties(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	args := req.GetArguments()
	raw, ok := args["updates"]
	if !ok {
		return registry.ErrorResult("updates parameter is required"), nil
	}

	updateMap, ok := raw.(map[string]any)
	if !ok {
		return registry.ErrorResult("updates must be a JSON object mapping property keys to values"), nil
	}
	if len(updateMap) == 0 {
		return registry.ErrorResult("updates must contain at least one key/value pair"), nil
	}

	updates := make([]graphql.KeyValueInput, 0, len(updateMap))
	for key, v := range updateMap {
		entry := graphql.KeyValueInput{Key: key}
		s, isNull, ok := scalarToString(v)
		if !ok {
			return registry.ErrorResult(fmt.Sprintf("unsupported value type for key %q (expected string, number, bool, or null)", key)), nil
		}
		if !isNull {
			entry.Value = &s
		}
		updates = append(updates, entry)
	}

	resp, err := updateProperties(updates)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if !resp.UpdateProperties {
		return registry.ErrorResult("property update was rejected by the server"), nil
	}

	keys := make([]string, 0, len(updateMap))
	for k := range updateMap {
		keys = append(keys, k)
	}
	return mcp.NewToolResultText(fmt.Sprintf("updated %d properties: %v", len(keys), keys)), nil
}

func listSnapshots(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetSystemSnapshots()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return registry.ResultFromJSON(resp.GetSystemSnapshots)
}

func getSnapshot(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "snapshot ID")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := getSystemSnapshot(id)
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

func deleteSnapshot(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "snapshot ID")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.DeleteSnapshot(id)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.DeleteSnapshot)
}

func restoreSnapshot(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "snapshot ID")
	if errResult != nil {
		return errResult, nil
	}

	hardReset := false
	if v, err := req.RequireBool("hard_reset"); err == nil {
		hardReset = v
	}

	resp, err := graphql.ResetFromSnapshotWithId(id, &hardReset)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.ResetFromSnapshotWithId)
}

func importSnapshot(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	args := req.GetArguments()
	raw, ok := args["snapshot"]
	if !ok {
		return registry.ErrorResult("snapshot parameter is required"), nil
	}
	var input graphql.SystemSnapshotInput
	if err := decodeJSON(raw, &input); err != nil {
		return registry.ErrorResult(fmt.Sprintf("invalid snapshot: %v", err)), nil
	}

	reset := false
	if v, err := req.RequireBool("reset"); err == nil {
		reset = v
	}
	hardReset := false
	if v, err := req.RequireBool("hard_reset"); err == nil {
		hardReset = v
	}

	if reset {
		resp, err := graphql.ImportSnapshotAndReset(input, &hardReset)
		if err != nil {
			return registry.ErrorResultFromErr(err), nil
		}
		return registry.ResultFromJSON(resp.ImportSnapshotAndReset)
	}

	resp, err := graphql.ImportSnapshot(input)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.ImportSnapshot)
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

func getStatusReport(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	client, err := getRESTClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	var report map[string]any
	if err := client.Get("/api/v2/status/report", &report, nil); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(report)
}
