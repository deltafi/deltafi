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
// ABOUTME: Request-level tests for MCP REST-backed handlers
// ABOUTME: Verifies methods, paths, bodies, headers, and response handling

package tools

import (
	"bytes"
	"context"
	"io"
	"net/http"
	"strings"
	"testing"

	"github.com/google/uuid"
	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/internal/api"
)

type recordingRESTClient struct {
	method  string
	path    string
	body    any
	opts    *api.RequestOpts
	respond func(any)
}

func (c *recordingRESTClient) record(method, path string, body, result any, opts *api.RequestOpts) error {
	c.method, c.path, c.body, c.opts = method, path, body, opts
	if c.respond != nil {
		c.respond(result)
	}
	return nil
}

func (c *recordingRESTClient) Get(path string, result any, opts *api.RequestOpts) error {
	return c.record(http.MethodGet, path, nil, result, opts)
}
func (c *recordingRESTClient) Post(path string, body, result any, opts *api.RequestOpts) error {
	return c.record(http.MethodPost, path, body, result, opts)
}
func (c *recordingRESTClient) Put(path string, body, result any, opts *api.RequestOpts) error {
	return c.record(http.MethodPut, path, body, result, opts)
}
func (c *recordingRESTClient) DoWithBody(method, path string, body, result any, opts *api.RequestOpts) error {
	return c.record(method, path, body, result, opts)
}

func useRecordingRESTClient(t *testing.T, client restClient) {
	t.Helper()
	original := getRESTClient
	getRESTClient = func() (restClient, error) { return client, nil }
	t.Cleanup(func() { getRESTClient = original })
}

func TestAnnotateDeltaFileRequest(t *testing.T) {
	client := &recordingRESTClient{}
	useRecordingRESTClient(t, client)
	did := uuid.New()
	req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{
		"did": did.String(), "annotations": map[string]any{"count": float64(2), "ready": true},
		"allow_overwrites": true,
	}}}
	result, err := annotateDeltaFile(context.Background(), req)
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", result, err)
	}
	want := "/api/v2/deltafile/annotate/" + did.String() + "/allowOverwrites?count=2&ready=true"
	if client.method != http.MethodPost || client.path != want {
		t.Fatalf("got %s %s, want POST %s", client.method, client.path, want)
	}
}

func TestCreateEventRequest(t *testing.T) {
	client := &recordingRESTClient{respond: func(result any) {
		*(result.(*api.Event)) = *api.NewEvent()
	}}
	useRecordingRESTClient(t, client)
	req := mcp.CallToolRequest{Params: mcp.CallToolParams{Arguments: map[string]any{
		"summary": "deployment", "severity": api.WarnSeverity, "source": "test", "notification": true,
	}}}
	result, err := createEvent(context.Background(), req)
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", result, err)
	}
	event, ok := client.body.(*api.Event)
	if !ok || client.method != http.MethodPost || client.path != "/api/v2/events" {
		t.Fatalf("unexpected request: %s %s body=%T", client.method, client.path, client.body)
	}
	if event.Summary != "deployment" || event.Severity != api.WarnSeverity || event.Source != "test" || !event.Notification {
		t.Fatalf("unexpected event body: %+v", event)
	}
}

func TestPerformIngressRequest(t *testing.T) {
	client := &recordingRESTClient{respond: func(result any) { *(result.(*string)) = "did-123" }}
	useRecordingRESTClient(t, client)
	result, err := performIngress("source-a", "data.bin", "application/test", `{"key":"value"}`, []byte("payload"))
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", result, err)
	}
	if client.method != http.MethodPost || client.path != "/api/v2/deltafile/ingress" {
		t.Fatalf("got %s %s", client.method, client.path)
	}
	if client.opts == nil || client.opts.Headers["Filename"] != "data.bin" || client.opts.Headers["DataSource"] != "source-a" || client.opts.Headers["Content-Type"] != "application/test" || client.opts.Headers["Metadata"] != `{"key":"value"}` {
		t.Fatalf("unexpected headers: %+v", client.opts)
	}
	reader, ok := client.body.(io.Reader)
	if !ok {
		t.Fatalf("body is %T, want io.Reader", client.body)
	}
	body, _ := io.ReadAll(reader)
	if !bytes.Equal(body, []byte("payload")) || !strings.Contains(resultText(t, result), "did-123") {
		t.Fatalf("unexpected body/result: %q %q", body, resultText(t, result))
	}
}

func TestGetStatusReportRequest(t *testing.T) {
	client := &recordingRESTClient{respond: func(result any) {
		*(result.(*map[string]any)) = map[string]any{"status": "ok"}
	}}
	useRecordingRESTClient(t, client)
	result, err := getStatusReport(context.Background(), mcp.CallToolRequest{})
	if err != nil || result.IsError {
		t.Fatalf("unexpected result: result=%v err=%v", result, err)
	}
	if client.method != http.MethodGet || client.path != "/api/v2/status/report" || !strings.Contains(resultText(t, result), `"status": "ok"`) {
		t.Fatalf("unexpected request/result: %s %s %q", client.method, client.path, resultText(t, result))
	}
}
