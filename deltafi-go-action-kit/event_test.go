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
// ABOUTME: Tests for event wire format types and time formatting.
// ABOUTME: Verifies formatTime output and ActionEvent JSON serialization matches Java wire format.

package actionkit

import (
	"encoding/json"
	"testing"
	"time"
)

func TestFormatTime(t *testing.T) {
	ts := time.Date(2025, 6, 15, 10, 30, 45, 123456789, time.UTC)
	got := formatTime(ts)

	// Should be parseable as RFC3339Nano
	parsed, err := time.Parse(time.RFC3339Nano, got)
	if err != nil {
		t.Fatalf("formatTime output %q is not valid RFC3339Nano: %v", got, err)
	}
	if !parsed.Equal(ts) {
		t.Errorf("round-trip mismatch: got %v, want %v", parsed, ts)
	}
}

func TestActionEventJSON_Transform(t *testing.T) {
	event := &ActionEvent{
		DID:        "did-123",
		FlowName:   "my-flow",
		FlowID:     "flow-id",
		ActionName: "MyAction",
		Start:      "2025-01-01T00:00:00Z",
		Stop:       "2025-01-01T00:00:01Z",
		Type:       "TRANSFORM",
		Transform: []TransformEvent{{
			DID: "did-123",
			Content: []ActionContent{{
				Name:      "out.txt",
				MediaType: "text/plain",
				Segments:  []Segment{{UUID: "u1", Offset: 0, Size: 10, DID: "did-123"}},
			}},
			Metadata:    map[string]string{"k": "v"},
			Annotations: map[string]string{"a": "b"},
		}},
	}

	data, err := json.Marshal(event)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	// Verify top-level fields
	if parsed["did"] != "did-123" {
		t.Errorf("did = %v", parsed["did"])
	}
	if parsed["flowName"] != "my-flow" {
		t.Errorf("flowName = %v", parsed["flowName"])
	}
	if parsed["flowId"] != "flow-id" {
		t.Errorf("flowId = %v", parsed["flowId"])
	}
	if parsed["type"] != "TRANSFORM" {
		t.Errorf("type = %v", parsed["type"])
	}

	// Transform should be present, error/filter/ingress should not
	if parsed["transform"] == nil {
		t.Error("transform should be present")
	}
	if _, ok := parsed["error"]; ok {
		t.Error("error should be omitted")
	}
	if _, ok := parsed["filter"]; ok {
		t.Error("filter should be omitted")
	}
	if _, ok := parsed["ingress"]; ok {
		t.Error("ingress should be omitted")
	}
}

func TestActionEventJSON_Error(t *testing.T) {
	event := &ActionEvent{
		DID:        "did-123",
		FlowName:   "flow",
		FlowID:     "fid",
		ActionName: "Act",
		Start:      "2025-01-01T00:00:00Z",
		Stop:       "2025-01-01T00:00:01Z",
		Type:       "ERROR",
		Error: &ErrorEvent{
			Cause:   "something broke",
			Context: "details here",
		},
	}

	data, err := json.Marshal(event)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}

	if parsed["type"] != "ERROR" {
		t.Errorf("type = %v", parsed["type"])
	}

	errObj := parsed["error"].(map[string]any)
	if errObj["cause"] != "something broke" {
		t.Errorf("cause = %v", errObj["cause"])
	}
	if errObj["context"] != "details here" {
		t.Errorf("context = %v", errObj["context"])
	}

	if _, ok := parsed["transform"]; ok {
		t.Error("transform should be omitted for error events")
	}
}

func TestActionEventJSON_Filter(t *testing.T) {
	event := &ActionEvent{
		DID:  "did-123",
		Type: "FILTER",
		Filter: &FilterEvent{
			Message:     "filtered because",
			Annotations: map[string]string{"reason": "test"},
		},
	}

	data, err := json.Marshal(event)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	json.Unmarshal(data, &parsed)

	filterObj := parsed["filter"].(map[string]any)
	if filterObj["message"] != "filtered because" {
		t.Errorf("message = %v", filterObj["message"])
	}
	annotations := filterObj["annotations"].(map[string]any)
	if annotations["reason"] != "test" {
		t.Errorf("annotation reason = %v", annotations["reason"])
	}
}

func TestActionEventJSON_Ingress(t *testing.T) {
	event := &ActionEvent{
		DID:  "did-123",
		Type: "INGRESS",
		Ingress: &IngressEvent{
			Memo:             "memo-state",
			ExecuteImmediate: false,
			Status:           "HEALTHY",
			StatusMessage:    "all good",
			IngressItems: []IngressEventItem{{
				DID:           "new-did",
				DeltaFileName: "file.txt",
				Content: []ActionContent{{
					Name:      "file.txt",
					MediaType: "text/plain",
					Segments:  []Segment{{UUID: "u1", Size: 5, DID: "new-did"}},
				}},
				Metadata:    map[string]string{"k": "v"},
				Annotations: map[string]string{"a": "b"},
			}},
		},
	}

	data, err := json.Marshal(event)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	json.Unmarshal(data, &parsed)

	ingressObj := parsed["ingress"].(map[string]any)
	if ingressObj["memo"] != "memo-state" {
		t.Errorf("memo = %v", ingressObj["memo"])
	}
	if ingressObj["executeImmediate"] != false {
		t.Errorf("executeImmediate = %v", ingressObj["executeImmediate"])
	}
	if ingressObj["status"] != "HEALTHY" {
		t.Errorf("status = %v", ingressObj["status"])
	}

	items := ingressObj["ingressItems"].([]any)
	if len(items) != 1 {
		t.Fatalf("expected 1 ingress item, got %d", len(items))
	}

	item := items[0].(map[string]any)
	if item["did"] != "new-did" {
		t.Errorf("item did = %v", item["did"])
	}
	if item["deltaFileName"] != "file.txt" {
		t.Errorf("item deltaFileName = %v", item["deltaFileName"])
	}
}

func TestActionEventJSON_Egress(t *testing.T) {
	event := &ActionEvent{
		DID:  "did-123",
		Type: "EGRESS",
		Messages: []LogMessage{
			{Severity: SeverityInfo, Created: "2025-01-01T00:00:00Z", Source: "Act", Message: "done"},
		},
	}

	data, err := json.Marshal(event)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}

	var parsed map[string]any
	json.Unmarshal(data, &parsed)

	if parsed["type"] != "EGRESS" {
		t.Errorf("type = %v", parsed["type"])
	}

	msgs := parsed["messages"].([]any)
	if len(msgs) != 1 {
		t.Fatalf("expected 1 message, got %d", len(msgs))
	}

	// No transform/error/filter/ingress
	if _, ok := parsed["transform"]; ok {
		t.Error("transform should be omitted for egress")
	}
}
