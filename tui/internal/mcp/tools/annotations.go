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
// ABOUTME: DeltaFile annotation MCP tools
// ABOUTME: Adds key/value annotations to a DeltaFile and lists known annotation keys

package tools

import (
	"context"
	"fmt"
	"net/url"

	"github.com/mark3labs/mcp-go/mcp"

	"github.com/deltafi/tui/internal/mcp/registry"
)

func init() {
	reg := registry.Get()

	reg.Register(registry.Tool{
		Definition: registry.DestructiveTool("annotate_deltafile",
			"Add one or more key/value annotations to a DeltaFile",
			mcp.WithString("did", mcp.Required(), mcp.Description("DeltaFile ID (UUID)")),
			mcp.WithObject("annotations", mcp.Required(), mcp.MinProperties(1), mcp.AdditionalProperties(jsonScalarSchema), mcp.Description("Annotation key/value pairs to add; values are coerced to strings")),
			mcp.WithBoolean("allow_overwrites", mcp.Description("If true, overwrite annotations that already exist (default false)")),
		),
		Handler: annotateDeltaFile,
		Tags:    []string{"annotations"},
	})
}

func annotateDeltaFile(ctx context.Context, req mcp.CallToolRequest) (*mcp.CallToolResult, error) {
	did, errResult := requireUUID(req, "did", "did")
	if errResult != nil {
		return errResult, nil
	}

	args := req.GetArguments()
	raw, ok := args["annotations"]
	if !ok {
		return registry.ErrorResult("annotations parameter is required"), nil
	}
	annotationsMap, ok := raw.(map[string]any)
	if !ok {
		return registry.ErrorResult("annotations must be a JSON object"), nil
	}
	if len(annotationsMap) == 0 {
		return registry.ErrorResult("annotations must contain at least one key/value pair"), nil
	}

	values := url.Values{}
	for key, v := range annotationsMap {
		s, _, ok := scalarToString(v)
		if !ok {
			return registry.ErrorResult(fmt.Sprintf("annotations[%q] must be a string, number, bool, or null", key)), nil
		}
		values.Set(key, s)
	}

	allowOverwrites := false
	if v, err := req.RequireBool("allow_overwrites"); err == nil {
		allowOverwrites = v
	}

	path := "/api/v2/deltafile/annotate/" + did.String()
	if allowOverwrites {
		path += "/allowOverwrites"
	}
	path += "?" + values.Encode()

	client, err := getRESTClient()
	if err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	var body string
	if err := client.Post(path, nil, &body, nil); err != nil {
		return registry.ErrorResultFromErr(err), nil
	}

	return mcp.NewToolResultText(fmt.Sprintf("annotated DeltaFile %s with %d annotations", did, len(annotationsMap))), nil
}
