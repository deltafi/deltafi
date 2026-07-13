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
// ABOUTME: Integration testing MCP tools
// ABOUTME: Provides tools for managing and running DeltaFi integration tests

package tools

import (
	"context"
	"fmt"
	"os"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/mcp/registry"
)

var loadIntegrationTestRequest = graphql.LoadIntegrationTest

func init() {
	reg := registry.Get()

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("list_integration_tests",
			"List all configured integration tests",
		),
		Handler: listIntegrationTestsTool,
		Tags:    []string{"testing"},
	})

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_integration_test",
			"Get a single integration test definition by name",
			mcp.WithString("name", mcp.Required(), mcp.Description("Integration test name")),
		),
		Handler: getIntegrationTest,
		Tags:    []string{"testing"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("load_integration_test",
			"Load (create or update) an integration test from YAML configuration",
			mcp.WithString("config_yaml", mcp.Description("Inline YAML configuration for the integration test")),
			mcp.WithString("config_path", mcp.Description("Path to a YAML file on the local filesystem (~/.. and relative paths are accepted)")),
		),
		Handler: loadIntegrationTest,
		Tags:    []string{"testing"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("start_integration_test",
			"Start a configured integration test by name and return the run identifier",
			mcp.WithString("name", mcp.Required(), mcp.Description("Integration test name")),
		),
		Handler: startIntegrationTest,
		Tags:    []string{"testing"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("remove_integration_test",
			"Delete an integration test definition by name",
			mcp.WithString("name", mcp.Required(), mcp.Description("Integration test name")),
		),
		Handler: removeIntegrationTest,
		Tags:    []string{"testing"},
	})

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_test_results",
			"List all integration test run results",
		),
		Handler: getTestResults,
		Tags:    []string{"testing"},
	})

	reg.Register(registry.Tool{
		Definition: registry.ReadOnlyTool("get_test_result",
			"Get the result of a single integration test run by id",
			mcp.WithString("id", mcp.Required(), mcp.Description("Test result identifier returned by start_integration_test")),
		),
		Handler: getTestResult,
		Tags:    []string{"testing"},
	})

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("remove_test_result",
			"Delete a recorded integration test result by id",
			mcp.WithString("id", mcp.Required(), mcp.Description("Test result identifier")),
		),
		Handler: removeTestResult,
		Tags:    []string{"testing"},
	})
}

func listIntegrationTestsTool(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetIntegrationTests()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.GetIntegrationTests)
}

func getIntegrationTest(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.GetIntegrationTest(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if resp.GetIntegrationTest.Name == nil {
		return registry.ErrorResult(fmt.Sprintf("integration test %q not found", name)), nil
	}
	return registry.ResultFromJSON(resp.GetIntegrationTest)
}

func loadIntegrationTest(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	configYaml, _ := req.RequireString("config_yaml")
	configPath, _ := req.RequireString("config_path")

	if configYaml == "" && configPath == "" {
		return registry.ErrorResult("either config_yaml or config_path is required"), nil
	}
	if configYaml != "" && configPath != "" {
		return registry.ErrorResult("config_yaml and config_path are mutually exclusive"), nil
	}

	if configPath != "" {
		path, err := resolveLocalPath(configPath)
		if err != nil {
			return registry.ErrorResult(err.Error()), nil
		}
		data, err := os.ReadFile(path)
		if err != nil {
			return registry.ErrorResult(fmt.Sprintf("failed to read %s: %v", path, err)), nil
		}
		configYaml = string(data)
	}

	resp, err := loadIntegrationTestRequest(configYaml)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.LoadIntegrationTest)
}

func startIntegrationTest(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.StartIntegrationTest(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.StartIntegrationTest)
}

func removeIntegrationTest(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	name, errResult := requireNonEmptyString(req, "name")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.RemoveIntegrationTest(name)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if !resp.RemoveIntegrationTest {
		return registry.ErrorResult(fmt.Sprintf("integration test %q was not removed", name)), nil
	}
	return mcp.NewToolResultText(fmt.Sprintf("integration test %q removed", name)), nil
}

func getTestResults(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	resp, err := graphql.GetTestResults()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	return registry.ResultFromJSON(resp.GetTestResults)
}

func getTestResult(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireNonEmptyString(req, "id")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.GetTestResult(id)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if resp.GetTestResult.Id == nil {
		return registry.ErrorResult(fmt.Sprintf("test result %q not found", id)), nil
	}
	return registry.ResultFromJSON(resp.GetTestResult)
}

func removeTestResult(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	id, errResult := requireNonEmptyString(req, "id")
	if errResult != nil {
		return errResult, nil
	}

	resp, err := graphql.RemoveTestResult(id)
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}
	if !resp.RemoveTestResult {
		return registry.ErrorResult(fmt.Sprintf("test result %q was not removed", id)), nil
	}
	return mcp.NewToolResultText(fmt.Sprintf("test result %q removed", id)), nil
}
