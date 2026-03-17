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
// ABOUTME: ActionEvent wire format structs for communication with DeltaFi core.
// ABOUTME: Defines the JSON structures published to the result queue after action execution.

package actionkit

import "time"

// ActionEvent is the result payload published to Redis after action execution.
type ActionEvent struct {
	DID        string           `json:"did"`
	FlowName   string           `json:"flowName"`
	FlowID     string           `json:"flowId"`
	ActionName string           `json:"actionName"`
	Start      string           `json:"start"`
	Stop       string           `json:"stop"`
	Type       string           `json:"type"`
	Transform  []TransformEvent `json:"transform,omitempty"`
	Error      *ErrorEvent      `json:"error,omitempty"`
	Filter     *FilterEvent     `json:"filter,omitempty"`
	Ingress    *IngressEvent    `json:"ingress,omitempty"`
	Metrics    []Metric         `json:"metrics,omitempty"`
	Messages   []LogMessage     `json:"messages,omitempty"`
}

// TransformEvent describes transformed content for a single DeltaFile.
type TransformEvent struct {
	Name               string            `json:"name,omitempty"`
	DID                string            `json:"did"`
	Content            []ActionContent   `json:"content"`
	Metadata           map[string]string `json:"metadata,omitempty"`
	DeleteMetadataKeys []string          `json:"deleteMetadataKeys,omitempty"`
	Annotations        map[string]string `json:"annotations,omitempty"`
	Messages           []LogMessage      `json:"messages,omitempty"`
}

// ErrorEvent describes an error that occurred during action execution.
type ErrorEvent struct {
	Cause       string            `json:"cause"`
	Context     string            `json:"context,omitempty"`
	Annotations map[string]string `json:"annotations,omitempty"`
}

// FilterEvent describes a filter decision made by an action.
type FilterEvent struct {
	Message     string            `json:"message"`
	Annotations map[string]string `json:"annotations,omitempty"`
}

// IngressEvent describes new DeltaFiles created by a timed ingress action.
type IngressEvent struct {
	Memo             string             `json:"memo,omitempty"`
	ExecuteImmediate bool               `json:"executeImmediate"`
	Status           string             `json:"status,omitempty"`
	StatusMessage    string             `json:"statusMessage,omitempty"`
	IngressItems     []IngressEventItem `json:"ingressItems,omitempty"`
}

// IngressEventItem describes a single DeltaFile created by ingress.
type IngressEventItem struct {
	DID           string            `json:"did"`
	DeltaFileName string            `json:"deltaFileName"`
	Content       []ActionContent   `json:"content"`
	Metadata      map[string]string `json:"metadata,omitempty"`
	Annotations   map[string]string `json:"annotations,omitempty"`
}

// Metric is a named measurement included in action results.
type Metric struct {
	Name  string            `json:"name"`
	Value float64           `json:"value"`
	Tags  map[string]string `json:"tags,omitempty"`
}

// formatTime formats a time value as ISO-8601 with timezone offset.
func formatTime(t time.Time) string {
	return t.Format(time.RFC3339Nano)
}
