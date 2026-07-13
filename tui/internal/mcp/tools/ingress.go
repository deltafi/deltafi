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
// ABOUTME: Ingress MCP tools for sending data into DeltaFi
// ABOUTME: Provides tools for uploading files and inline content via REST ingress endpoint

package tools

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	reg.Register(registry.Tool{
		Definition: registry.SafeWriteTool("ingress_file",
			"Ingress a file from the local filesystem into a DeltaFi data source. Reads the file from the machine running the MCP server.",
			mcp.WithString("path", mcp.Required(), mcp.Description("Path to the file to ingress. Absolute, relative, or ~-prefixed paths are accepted.")),
			mcp.WithString("data_source", mcp.Required(), mcp.Description("Name of the REST data source receiving the data")),
			mcp.WithString("content_type", mcp.Description("MIME type for the content (default application/octet-stream)")),
			mcp.WithString("filename", mcp.Description("Override the filename sent with the request (defaults to base name of path)")),
			mcp.WithObject("metadata", mcp.AdditionalProperties(jsonScalarSchema), mcp.Description("Metadata key/value pairs to attach to the DeltaFile")),
		),
		Handler: ingressFile,
		Tags:    []string{"ingress"},
	})

	reg.Register(registry.Tool{
		Definition: registry.SafeWriteTool("ingress_content",
			"Ingress inline content (text or base64-encoded bytes) into a DeltaFi data source",
			mcp.WithString("data_source", mcp.Required(), mcp.Description("Name of the REST data source receiving the data")),
			mcp.WithString("filename", mcp.Required(), mcp.Description("Filename to associate with the content")),
			mcp.WithString("content", mcp.Description("Content body. Use this for text. Mutually exclusive with content_base64.")),
			mcp.WithString("content_base64", mcp.Description("Base64-encoded content for binary data. Mutually exclusive with content.")),
			mcp.WithString("content_type", mcp.Description("MIME type for the content (default application/octet-stream)")),
			mcp.WithObject("metadata", mcp.AdditionalProperties(jsonScalarSchema), mcp.Description("Metadata key/value pairs to attach to the DeltaFile")),
		),
		Handler: ingressContent,
		Tags:    []string{"ingress"},
	})
}

func ingressFile(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	rawPath, errResult := requireNonEmptyString(req, "path")
	if errResult != nil {
		return errResult, nil
	}

	dataSource, errResult := requireNonEmptyString(req, "data_source")
	if errResult != nil {
		return errResult, nil
	}

	path, err := resolveLocalPath(rawPath)
	if err != nil {
		return registry.ErrorResult(err.Error()), nil
	}

	info, err := os.Stat(path)
	if err != nil {
		return registry.ErrorResult(fmt.Sprintf("cannot access %s: %v", path, err)), nil
	}
	if info.IsDir() {
		return registry.ErrorResult(fmt.Sprintf("%s is a directory; ingress_file requires a single file", path)), nil
	}

	data, err := os.ReadFile(path)
	if err != nil {
		return registry.ErrorResult(fmt.Sprintf("failed to read %s: %v", path, err)), nil
	}

	filename := filepath.Base(path)
	if override, err := req.RequireString("filename"); err == nil && override != "" {
		filename = override
	}

	contentType := "application/octet-stream"
	if ct, err := req.RequireString("content_type"); err == nil && ct != "" {
		contentType = ct
	}

	metadata, errResult := ingressMetadata(req)
	if errResult != nil {
		return errResult, nil
	}

	return performIngress(dataSource, filename, contentType, metadata, data)
}

func ingressContent(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	dataSource, errResult := requireNonEmptyString(req, "data_source")
	if errResult != nil {
		return errResult, nil
	}

	filename, errResult := requireNonEmptyString(req, "filename")
	if errResult != nil {
		return errResult, nil
	}

	data, errResult := decodeInlineContent(req)
	if errResult != nil {
		return errResult, nil
	}

	contentType := "application/octet-stream"
	if ct, err := req.RequireString("content_type"); err == nil && ct != "" {
		contentType = ct
	}

	metadata, errResult := ingressMetadata(req)
	if errResult != nil {
		return errResult, nil
	}

	return performIngress(dataSource, filename, contentType, metadata, data)
}

func decodeInlineContent(req mcp.CallToolRequest) ([]byte, *mcp.CallToolResult) {
	args := req.GetArguments()
	_, hasContent := args["content"]
	_, hasContentB64 := args["content_base64"]
	if !hasContent && !hasContentB64 {
		return nil, registry.ErrorResult("either content or content_base64 must be provided")
	}
	if hasContent && hasContentB64 {
		return nil, registry.ErrorResult("content and content_base64 are mutually exclusive")
	}

	var data []byte
	if hasContent {
		content, err := req.RequireString("content")
		if err != nil {
			return nil, registry.ErrorResult("content must be a string")
		}
		data = []byte(content)
	} else {
		contentB64, err := req.RequireString("content_base64")
		if err != nil {
			return nil, registry.ErrorResult("content_base64 must be a string")
		}
		decoded, err := base64.StdEncoding.DecodeString(contentB64)
		if err != nil {
			return nil, registry.ErrorResult(fmt.Sprintf("invalid base64 content: %v", err))
		}
		data = decoded
	}
	return data, nil
}

func ingressMetadata(req mcp.CallToolRequest) (string, *mcp.CallToolResult) {
	raw, ok := req.GetArguments()["metadata"]
	if !ok || raw == nil {
		return "", nil
	}
	if _, ok := raw.(map[string]any); !ok {
		return "", registry.ErrorResult("metadata must be a JSON object")
	}
	data, err := json.Marshal(raw)
	if err != nil {
		return "", registry.ErrorResult(fmt.Sprintf("invalid metadata: %v", err))
	}
	return string(data), nil
}

// resolveLocalPath expands a leading "~" and makes the path absolute so the
// MCP server can read the file regardless of how the agent supplied it.
func resolveLocalPath(p string) (string, error) {
	if p == "" {
		return "", fmt.Errorf("path is empty")
	}
	if strings.HasPrefix(p, "~") {
		home := app.UserHomeDir()
		if p == "~" {
			p = home
		} else if strings.HasPrefix(p, "~/") {
			p = filepath.Join(home, p[2:])
		}
	}
	abs, err := filepath.Abs(p)
	if err != nil {
		return "", fmt.Errorf("invalid path %s: %v", p, err)
	}
	return abs, nil
}

func performIngress(dataSource, filename, contentType, metadata string, data []byte) (*mcp.CallToolResult, error) {
	client, err := getRESTClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	opts := &api.RequestOpts{
		Headers: map[string]string{
			"Filename":     filename,
			"DataSource":   dataSource,
			"Content-Type": contentType,
		},
	}
	if metadata != "" {
		opts.Headers["Metadata"] = metadata
	}

	var did string
	if err := client.Post("/api/v2/deltafile/ingress", bytes.NewBuffer(data), &did, opts); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	type IngressResult struct {
		DID        string `json:"did"`
		DataSource string `json:"data_source"`
		Filename   string `json:"filename"`
		Bytes      int    `json:"bytes"`
	}

	return registry.ResultFromJSON(IngressResult{
		DID:        did,
		DataSource: dataSource,
		Filename:   filename,
		Bytes:      len(data),
	})
}
