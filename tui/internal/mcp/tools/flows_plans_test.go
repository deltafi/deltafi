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
// ABOUTME: Tests for get_flow_plan and export_system_plans MCP tools
// ABOUTME: Proves plan output unwraps and round-trips back into load_flow

package tools

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"testing"

	"github.com/deltafi/tui/graphql"
)

type transformPlanFixture = graphql.GetSystemFlowPlansGetAllSystemFlowPlansTransformPlansTransformFlowPlan
type dataSinkPlanFixture = graphql.GetSystemFlowPlansGetAllSystemFlowPlansDataSinkPlansDataSinkPlan

func systemPlansFixture() *graphql.GetSystemFlowPlansResponse {
	return &graphql.GetSystemFlowPlansResponse{
		GetAllSystemFlowPlans: graphql.GetSystemFlowPlansGetAllSystemFlowPlans{
			TransformPlans: []*transformPlanFixture{
				{Name: "my-transform", Type: "TRANSFORM", Description: "does things"},
			},
			DataSinkPlans: []*dataSinkPlanFixture{
				{Name: "my-sink", Type: "DATA_SINK", Description: "sinks things"},
			},
		},
	}
}

func stubSystemFlowPlans(t *testing.T, resp *graphql.GetSystemFlowPlansResponse, err error) {
	t.Helper()
	original := getSystemFlowPlans
	getSystemFlowPlans = func() (*graphql.GetSystemFlowPlansResponse, error) {
		return resp, err
	}
	t.Cleanup(func() { getSystemFlowPlans = original })
}

func TestGetFlowPlanMissingName(t *testing.T) {
	result, err := getFlowPlan(context.Background(), callToolRequest(map[string]any{
		"type": "transform",
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got := errorResultText(t, result); got != "name parameter is required" {
		t.Errorf("message: got %q", got)
	}
}

func TestGetFlowPlanUnknownType(t *testing.T) {
	stubSystemFlowPlans(t, systemPlansFixture(), nil)
	result, err := getFlowPlan(context.Background(), callToolRequest(map[string]any{
		"name": "my-transform",
		"type": "bogus",
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got := errorResultText(t, result); !strings.Contains(got, "unknown flow type: bogus") {
		t.Errorf("message should name the bad type: got %q", got)
	}
}

func TestGetFlowPlanNotFound(t *testing.T) {
	stubSystemFlowPlans(t, systemPlansFixture(), nil)
	result, err := getFlowPlan(context.Background(), callToolRequest(map[string]any{
		"name": "no-such-flow",
		"type": "transform",
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got := errorResultText(t, result); !strings.Contains(got, "no system flow plan named") {
		t.Errorf("message should report the missing plan: got %q", got)
	}
}

func TestGetFlowPlanPropagatesError(t *testing.T) {
	stubSystemFlowPlans(t, nil, errors.New("connection refused"))
	result, err := getFlowPlan(context.Background(), callToolRequest(map[string]any{
		"name": "my-transform",
		"type": "transform",
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got := errorResultText(t, result); !strings.Contains(got, "connection refused") {
		t.Errorf("message should surface the fetch error: got %q", got)
	}
}

// The plan returned by get_flow_plan must decode straight back into load_flow.
// This is the whole point of the tool: get_flow's runtime output (flowStatus,
// sourcePlugin) is rejected by load_flow's DisallowUnknownFields decoding.
func TestGetFlowPlanRoundTripsThroughLoadFlow(t *testing.T) {
	stubSystemFlowPlans(t, systemPlansFixture(), nil)

	planResult, err := getFlowPlan(context.Background(), callToolRequest(map[string]any{
		"name": "my-transform",
		"type": "transform",
	}))
	if err != nil || planResult.IsError {
		t.Fatalf("unexpected plan result: result=%v err=%v", resultText(t, planResult), err)
	}

	var config map[string]any
	if err := json.Unmarshal([]byte(resultText(t, planResult)), &config); err != nil {
		t.Fatalf("plan output is not valid JSON: %v", err)
	}

	var saved graphql.TransformFlowPlanInput
	original := saveTransformFlow
	saveTransformFlow = func(input graphql.TransformFlowPlanInput) (*graphql.SaveTransformResponse, error) {
		saved = input
		return &graphql.SaveTransformResponse{}, nil
	}
	t.Cleanup(func() { saveTransformFlow = original })

	loadResult, err := loadFlow(context.Background(), callToolRequest(map[string]any{
		"config": config,
	}))
	if err != nil || loadResult.IsError {
		t.Fatalf("plan did not round-trip into load_flow: result=%v err=%v", resultText(t, loadResult), err)
	}
	if saved.Name != "my-transform" || saved.Type != "TRANSFORM" {
		t.Fatalf("load_flow saved unexpected input: %+v", saved)
	}
}

func TestExportSystemPlansUnwrapsResponse(t *testing.T) {
	stubSystemFlowPlans(t, systemPlansFixture(), nil)

	result, err := exportSystemPlans(context.Background(), callToolRequest(map[string]any{}))
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", resultText(t, result), err)
	}

	var payload map[string]any
	if err := json.Unmarshal([]byte(resultText(t, result)), &payload); err != nil {
		t.Fatalf("export output is not valid JSON: %v", err)
	}
	if _, wrapped := payload["getAllSystemFlowPlans"]; wrapped {
		t.Fatal("export should be unwrapped, not the raw getAllSystemFlowPlans envelope")
	}
	if _, ok := payload["transformPlans"]; !ok {
		t.Fatalf("export should expose transformPlans directly: got keys %v", keysOf(payload))
	}
}

func TestExportSystemPlansPropagatesError(t *testing.T) {
	stubSystemFlowPlans(t, nil, errors.New("connection refused"))
	result, err := exportSystemPlans(context.Background(), callToolRequest(map[string]any{}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got := errorResultText(t, result); !strings.Contains(got, "connection refused") {
		t.Errorf("message should surface the fetch error: got %q", got)
	}
}

func keysOf(m map[string]any) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	return keys
}
