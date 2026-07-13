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
// ABOUTME: Resume policy MCP tools for automatic error recovery configuration
// ABOUTME: Provides CRUD, dry-run, toggle, and apply operations for resume policies

package tools

import (
	"context"
	"fmt"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

// Seam over the generated GraphQL mutation so the handler can be tested without
// a live server.
var loadResumePoliciesMutation = graphql.LoadResumePolicies

const resumePolicySchema = `Policy object fields:
- id (string, optional): UUID of an existing policy (omit for new policies)
- name (string, required)
- errorSubstring (string, optional): substring of the error message that triggers the policy
- dataSource (string, optional): only apply to this data source
- action (string, optional): only apply to this action
- maxAttempts (int, required): maximum number of automatic resume attempts
- priority (int, optional)
- enabled (bool, optional)
- backOff (object, required): { delay (int, required), maxDelay (int, optional), multiplier (int, optional), random (bool, optional) }`

func init() {
	reg := registry.Get()

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_resume_policies",
			"List all configured resume policies",
		),
		Handler: listResumePolicies,
		Tags:    []string{"auto_resume"},
	})

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_resume_policy",
			"Get a single resume policy by id",
			mcp.WithString("id", mcp.Required(), mcp.Description("Resume policy ID (UUID)")),
		),
		Handler: getResumePolicy,
		Tags:    []string{"auto_resume"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("load_resume_policies",
			"Load one or more resume policies. Each policy is created or replaced by name. "+resumePolicySchema,
			mcp.WithArray("policies", mcp.Required(), mcp.Items(resumePolicyJSONSchema()), mcp.Description("Resume policies to load")),
			mcp.WithBoolean("replace_all", mcp.Description("If true, replace the full policy set with the provided policies; pass an empty array to clear all policies (default false)")),
		),
		Handler: loadResumePolicies,
		Tags:    []string{"auto_resume"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("update_resume_policy",
			"Update an existing resume policy. The policy object must include the id of the policy to update. "+resumePolicySchema,
			withStructuredObject("policy", "Resume policy object (must include id)", resumePolicyJSONSchema(), true),
		),
		Handler: updateResumePolicy,
		Tags:    []string{"auto_resume"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("remove_resume_policy",
			"Delete a resume policy by id",
			mcp.WithString("id", mcp.Required(), mcp.Description("Resume policy ID (UUID)")),
		),
		Handler: removeResumePolicy,
		Tags:    []string{"auto_resume"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("toggle_resume_policy",
			"Enable or disable a resume policy",
			mcp.WithString("id", mcp.Required(), mcp.Description("Resume policy ID (UUID)")),
			mcp.WithBoolean("enabled", mcp.Required(), mcp.Description("True to enable, false to disable")),
		),
		Handler: toggleResumePolicy,
		Tags:    []string{"auto_resume"},
	})

	reg.Register(registry.Tool{
		Definition: registry.SafeWriteTool("apply_resume_policies",
			"Apply the named resume policies to any matching errored DeltaFiles, scheduling them for automatic resume",
			mcp.WithArray("names", mcp.Required(), mcp.WithStringItems(mcp.MinLength(1)), mcp.MinItems(1), mcp.Description("Names of the resume policies to apply")),
		),
		Handler: applyResumePolicies,
		Tags:    []string{"auto_resume"},
	})

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("resume_policy_dry_run",
			"Evaluate a resume policy against existing errored DeltaFiles without applying it. "+resumePolicySchema,
			withStructuredObject("policy", "Resume policy object to evaluate", resumePolicyJSONSchema(), true),
		),
		Handler: resumePolicyDryRun,
		Tags:    []string{"auto_resume"},
	})
}

func listResumePolicies(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetAllResumePolicies()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.GetAllResumePolicies)
}

func getResumePolicy(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "resume policy ID")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.GetResumePolicy(id)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if resp.GetResumePolicy == nil {
		return registry.ErrorResult(fmt.Sprintf("resume policy %q not found", id)), nil
	}
	return registry.ResultFromJSON(resp.GetResumePolicy)
}

func loadResumePolicies(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	args := req.GetArguments()
	raw, ok := args["policies"]
	if !ok {
		return registry.ErrorResult("policies parameter is required"), nil
	}
	rawList, ok := raw.([]any)
	if !ok {
		return registry.ErrorResult("policies must be a JSON array of policy objects"), nil
	}

	replaceAll := false
	if v, err := req.RequireBool("replace_all"); err == nil {
		replaceAll = v
	}

	if len(rawList) == 0 && !replaceAll {
		return registry.ErrorResult("policies must contain at least one policy unless replace_all is true"), nil
	}

	policies := make([]graphql.ResumePolicyInput, 0, len(rawList))
	for i, item := range rawList {
		policy, err := decodeResumePolicyInput(item)
		if err != nil {
			return registry.ErrorResult(fmt.Sprintf("policy at index %d: %v", i, err)), nil
		}
		policies = append(policies, policy)
	}

	resp, err := loadResumePoliciesMutation(replaceAll, policies)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.LoadResumePolicies)
}

func updateResumePolicy(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	args := req.GetArguments()
	raw, ok := args["policy"]
	if !ok {
		return registry.ErrorResult("policy parameter is required"), nil
	}
	policy, err := decodeResumePolicyInput(raw)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}
	if policy.Id == nil {
		return registry.ErrorResult("policy.id is required for updates"), nil
	}

	resp, err := graphql.UpdateResumePolicy(policy)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.UpdateResumePolicy)
}

func removeResumePolicy(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "resume policy ID")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.RemoveResumePolicy(id)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if !resp.RemoveResumePolicy {
		return registry.ErrorResult(fmt.Sprintf("resume policy %q was not removed", id)), nil
	}
	return mcp.NewToolResultText(fmt.Sprintf("resume policy %q removed", id)), nil
}

func toggleResumePolicy(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireUUID(req, "id", "resume policy ID")
	if errResult != nil {
		return errResult, nil
	}

	enabled, err := req.RequireBool("enabled")
	if err != nil {
		return registry.ErrorResult("enabled parameter is required"), nil
	}

	resp, err := graphql.ToggleResumePolicy(enabled, id)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if !resp.EnableResumePolicy {
		return registry.ErrorResult(fmt.Sprintf("resume policy %q toggle was rejected", id)), nil
	}
	state := "enabled"
	if !enabled {
		state = "disabled"
	}
	return mcp.NewToolResultText(fmt.Sprintf("resume policy %q %s", id, state)), nil
}

func applyResumePolicies(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	args := req.GetArguments()
	raw, ok := args["names"]
	if !ok {
		return registry.ErrorResult("names parameter is required"), nil
	}
	rawList, ok := raw.([]any)
	if !ok {
		return registry.ErrorResult("names must be an array of strings"), nil
	}
	names := make([]string, 0, len(rawList))
	for i, item := range rawList {
		s, ok := item.(string)
		if !ok {
			return registry.ErrorResult(fmt.Sprintf("names[%d] must be a string", i)), nil
		}
		names = append(names, s)
	}
	if len(names) == 0 {
		return registry.ErrorResult("names must contain at least one policy name"), nil
	}

	resp, err := graphql.ApplyResumePolicies(names)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.ApplyResumePolicies)
}

func resumePolicyDryRun(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	args := req.GetArguments()
	raw, ok := args["policy"]
	if !ok {
		return registry.ErrorResult("policy parameter is required"), nil
	}
	policy, err := decodeResumePolicyInput(raw)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	resp, err := graphql.ResumePolicyDryRun(policy)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.ResumePolicyDryRun)
}

func decodeResumePolicyInput(raw any) (graphql.ResumePolicyInput, error) {
	var policy graphql.ResumePolicyInput
	if raw == nil {
		return policy, fmt.Errorf("policy must be a JSON object")
	}
	if _, ok := raw.(map[string]any); !ok {
		return policy, fmt.Errorf("policy must be a JSON object")
	}
	if err := decodeJSON(raw, &policy); err != nil {
		return policy, fmt.Errorf("invalid policy: %v", err)
	}
	if policy.Name == "" {
		return policy, fmt.Errorf("policy.name is required")
	}
	if policy.MaxAttempts <= 0 {
		return policy, fmt.Errorf("policy.maxAttempts must be greater than zero")
	}
	if policy.BackOff.Delay <= 0 {
		return policy, fmt.Errorf("policy.backOff.delay must be greater than zero")
	}
	return policy, nil
}
