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
// ABOUTME: Delete policy MCP tools for DeltaFile retention configuration
// ABOUTME: Provides load, import, toggle, remove, and export operations for timed delete policies

package tools

import (
	"context"
	"fmt"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

const timedDeletePolicySchema = `Timed delete policy fields:
- id (string, optional): UUID of an existing policy (omit when creating a new one)
- name (string, required)
- enabled (bool, required)
- flow (string, optional): restrict to a single flow
- afterCreate (string, optional): ISO-8601 duration measured from creation (e.g., "PT24H")
- afterComplete (string, optional): ISO-8601 duration measured from completion (e.g., "P7D")
- minBytes (number, optional)
- deleteMetadata (bool, required): when true, delete DeltaFile metadata along with content`

func init() {
	reg := registry.Get()

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("load_timed_delete_policy",
			"Create or update a single timed delete policy. "+timedDeletePolicySchema,
			withStructuredObject("policy", "Timed delete policy object", timedDeletePolicyJSONSchema(), true),
		),
		Handler: loadTimedDeletePolicy,
		Tags:    []string{"delete_policies"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("import_delete_policies",
			"Import a set of delete policies. Use replace_all=true to fully replace the existing set. "+timedDeletePolicySchema,
			mcp.WithArray("timed_policies", mcp.Required(), mcp.Items(timedDeletePolicyJSONSchema()), mcp.Description("Timed delete policies")),
			mcp.WithBoolean("replace_all", mcp.Description("If true, replace the full delete policy set (default false)")),
		),
		Handler: importDeletePolicies,
		Tags:    []string{"delete_policies"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("remove_delete_policy",
			"Remove a delete policy by id",
			mcp.WithString("id", mcp.Required(), mcp.Description("Delete policy ID (UUID)")),
		),
		Handler: removeDeletePolicy,
		Tags:    []string{"delete_policies"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("toggle_delete_policy",
			"Enable or disable a delete policy",
			mcp.WithString("id", mcp.Required(), mcp.Description("Delete policy ID (UUID)")),
			mcp.WithBoolean("enabled", mcp.Required(), mcp.Description("True to enable, false to disable")),
		),
		Handler: toggleDeletePolicy,
		Tags:    []string{"delete_policies"},
	})

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("export_delete_policies",
			"Export all configured delete policies in a form suitable for re-import",
		),
		Handler: exportDeletePolicies,
		Tags:    []string{"delete_policies"},
	})
}

func loadTimedDeletePolicy(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	args := req.GetArguments()
	raw, ok := args["policy"]
	if !ok {
		return registry.ErrorResult("policy parameter is required"), nil
	}
	policy, err := decodeTimedDeletePolicyInput(raw)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	resp, err := graphql.LoadTimedDeletePolicy(policy)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.UpdateTimedDeletePolicy)
}

func importDeletePolicies(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	args := req.GetArguments()
	raw, ok := args["timed_policies"]
	if !ok {
		return registry.ErrorResult("timed_policies parameter is required"), nil
	}
	replaceAll := false
	if v, err := req.RequireBool("replace_all"); err == nil {
		replaceAll = v
	}
	policies, err := decodeTimedDeletePolicies(raw, replaceAll)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	resp, err := graphql.ImportDeletePolicies(replaceAll, graphql.DeletePoliciesInput{TimedPolicies: policies})
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.LoadDeletePolicies)
}

func decodeTimedDeletePolicies(raw any, replaceAll bool) ([]graphql.TimedDeletePolicyInput, error) {
	rawList, ok := raw.([]any)
	if !ok {
		return nil, fmt.Errorf("timed_policies must be a JSON array of policy objects")
	}
	if len(rawList) == 0 && !replaceAll {
		return nil, fmt.Errorf("timed_policies must contain at least one policy unless replace_all is true")
	}
	policies := make([]graphql.TimedDeletePolicyInput, 0, len(rawList))
	for i, item := range rawList {
		policy, err := decodeTimedDeletePolicyInput(item)
		if err != nil {
			return nil, fmt.Errorf("timed_policies[%d]: %v", i, err)
		}
		policies = append(policies, policy)
	}
	return policies, nil
}

func removeDeletePolicy(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "delete policy ID")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.RemoveDeletePolicy(id)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if !resp.RemoveDeletePolicy {
		return registry.ErrorResult(fmt.Sprintf("delete policy %q was not removed", id)), nil
	}
	return mcp.NewToolResultText(fmt.Sprintf("delete policy %q removed", id)), nil
}

func toggleDeletePolicy(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "delete policy ID")
	if errResult != nil {
		return errResult, nil
	}

	enabled, err := req.RequireBool("enabled")
	if err != nil {
		return registry.ErrorResult("enabled parameter is required"), nil
	}

	resp, err := graphql.ToggleDeletePolicy(enabled, id)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if !resp.EnablePolicy {
		return registry.ErrorResult(fmt.Sprintf("delete policy %q toggle was rejected", id)), nil
	}
	state := "enabled"
	if !enabled {
		state = "disabled"
	}
	return mcp.NewToolResultText(fmt.Sprintf("delete policy %q %s", id, state)), nil
}

func exportDeletePolicies(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.ExportDeletePolicies()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.ExportDeletePolicies)
}

func decodeTimedDeletePolicyInput(raw any) (graphql.TimedDeletePolicyInput, error) {
	var policy graphql.TimedDeletePolicyInput
	obj, ok := raw.(map[string]any)
	if !ok {
		return policy, fmt.Errorf("policy must be a JSON object")
	}
	if _, ok := obj["enabled"]; !ok {
		return policy, fmt.Errorf("policy.enabled is required")
	} else if _, ok := obj["enabled"].(bool); !ok {
		return policy, fmt.Errorf("policy.enabled must be a boolean")
	}
	if _, ok := obj["deleteMetadata"]; !ok {
		return policy, fmt.Errorf("policy.deleteMetadata is required")
	} else if _, ok := obj["deleteMetadata"].(bool); !ok {
		return policy, fmt.Errorf("policy.deleteMetadata must be a boolean")
	}
	if err := decodeJSON(raw, &policy); err != nil {
		return policy, fmt.Errorf("invalid policy: %v", err)
	}
	if policy.Name == "" {
		return policy, fmt.Errorf("policy.name is required")
	}
	if policy.AfterCreate == nil && policy.AfterComplete == nil {
		return policy, fmt.Errorf("policy must set afterCreate or afterComplete")
	}
	return policy, nil
}
